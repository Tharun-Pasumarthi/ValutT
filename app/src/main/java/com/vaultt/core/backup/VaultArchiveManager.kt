package com.vaultt.core.backup

import android.content.Context
import android.net.Uri
import com.vaultt.core.crypto.ArchiveCorruptedException
import com.vaultt.core.crypto.ArchiveCrypto
import com.vaultt.core.crypto.InvalidExportPasswordException
import com.vaultt.core.database.dao.AlbumDao
import com.vaultt.core.database.dao.VaultObjectDao
import com.vaultt.core.database.entity.AlbumEntity
import com.vaultt.core.database.entity.VaultObjectEntity
import com.vaultt.core.security.CryptoManager
import com.vaultt.core.security.VaultPrefs
import com.vaultt.core.storage.StorageManager
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.feature.backup.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.*
import java.security.DigestInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultArchiveManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultObjectDao: VaultObjectDao,
    private val albumDao: AlbumDao,
    private val cryptoManager: CryptoManager,
    private val storageManager: StorageManager,
    private val vaultPrefs: VaultPrefs
) {

    private val isExportCancelled = AtomicBoolean(false)
    private val isRestoreCancelled = AtomicBoolean(false)

    fun cancelExport() {
        isExportCancelled.set(true)
    }

    fun cancelRestore() {
        isRestoreCancelled.set(true)
    }

    /**
     * Calculates export preview statistics non-destructively by reading existing database records.
     */
    suspend fun calculatePreview(
        includeTrash: Boolean,
        targetItemIds: List<String>? = null
    ): ExportPreviewStats = withContext(Dispatchers.IO) {
        val allObjects = vaultObjectDao.getAllObjects().first()
        val trashObjects = if (includeTrash) vaultObjectDao.getTrashObjects().first() else emptyList()
        val allFolders = albumDao.getAllAlbums().first()

        val candidates = (allObjects + trashObjects).filter {
            if (targetItemIds != null) targetItemIds.contains(it.id) else true
        }

        var photos = 0
        var videos = 0
        var audio = 0
        var docs = 0
        var others = 0
        var totalBytes = 0L

        candidates.forEach { item ->
            totalBytes += item.size
            when (item.type) {
                VaultObjectType.PHOTO.name -> photos++
                VaultObjectType.VIDEO.name -> videos++
                VaultObjectType.AUDIO.name -> audio++
                VaultObjectType.DOCUMENT.name -> docs++
                else -> others++
            }
        }

        val estimatedArchiveSize = (totalBytes * 1.02).toLong() + 32768L // small overhead for header/manifest

        ExportPreviewStats(
            photoCount = photos,
            videoCount = videos,
            audioCount = audio,
            documentCount = docs,
            otherCount = others,
            folderCount = allFolders.size,
            trashCount = if (includeTrash) trashObjects.size else 0,
            totalSizeBytes = totalBytes,
            estimatedArchiveBytes = estimatedArchiveSize
        )
    }

    /**
     * Secure, non-destructive export of vault items to an encrypted .vaultt archive.
     * The original vault data, keys, database, and files remain 100% untouched.
     */
    suspend fun exportArchive(
        password: CharArray,
        includeTrash: Boolean,
        includeSettings: Boolean,
        targetItemIds: List<String>? = null,
        onProgress: (phase: ExportPhase, processed: Int, total: Int, bytesProcessed: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        isExportCancelled.set(false)
        val tempWorkspace = File(context.cacheDir, "vault_export_workspace_${UUID.randomUUID()}")
        tempWorkspace.mkdirs()

        val exportDir = File(context.cacheDir, "vault_exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val archiveFile = File(exportDir, "VaultT_Export_$timestamp.vaultt")

        try {
            onProgress(ExportPhase.PREPARING, 0, 0, 0L)

            // Step 1: Query original data (READ-ONLY)
            val regularItems = vaultObjectDao.getAllObjects().first()
            val trashItems = if (includeTrash) vaultObjectDao.getTrashObjects().first() else emptyList()
            val folders = albumDao.getAllAlbums().first()

            val allItemsToExport = (regularItems + trashItems).filter {
                if (targetItemIds != null) targetItemIds.contains(it.id) else true
            }

            if (isExportCancelled.get()) {
                throw InterruptedException("Export cancelled by user")
            }

            // Step 2: Derive encryption key & create verifier
            val salt = ArchiveCrypto.generateSalt()
            val derivedKey = ArchiveCrypto.deriveKey(password, salt)
            val verifierToken = ArchiveCrypto.createVerifierToken(derivedKey)

            val header = ArchiveHeader(
                formatVersion = 1,
                kdfAlgorithm = ArchiveCrypto.KDF_ALGORITHM,
                iterations = ArchiveCrypto.PBKDF2_ITERATIONS,
                cipherAlgorithm = "AES-256-GCM",
                saltHex = ArchiveCrypto.toHex(salt),
                verifierHex = ArchiveCrypto.toHex(verifierToken),
                createdAt = System.currentTimeMillis()
            )

            // Step 3: Decrypt original names and prepare manifest items
            val manifestItems = mutableListOf<ArchiveItemManifest>()
            for (item in allItemsToExport) {
                if (isExportCancelled.get()) throw InterruptedException("Export cancelled by user")
                val decryptedName = runCatching {
                    String(cryptoManager.decrypt(item.encryptedName, item.id.toByteArray()), Charsets.UTF_8)
                }.getOrDefault("File_${item.id.take(8)}")

                // Check thumbnail presence
                val thumbFile = storageManager.getThumbnailFile(item.id)
                val hasThumb = thumbFile.exists() && thumbFile.length() > 0

                // SHA-256 calculation
                val itemFile = storageManager.getObjectFile(item.id)
                val sha256 = if (item.hash != null && item.hash.isNotEmpty()) {
                    item.hash
                } else if (itemFile.exists()) {
                    calculateSha256(itemFile)
                } else {
                    ""
                }

                manifestItems.add(
                    ArchiveItemManifest(
                        id = item.id,
                        originalName = decryptedName,
                        type = item.type,
                        mimeType = item.mimeType,
                        sizeBytes = item.size,
                        sha256 = sha256,
                        isFavorite = item.isFavorite,
                        folderId = item.folderId,
                        isDeleted = item.isDeleted,
                        hasThumbnail = hasThumb,
                        createdAt = item.createdAt,
                        modifiedAt = item.modifiedAt
                    )
                )
            }

            val manifest = ArchiveManifest(
                exportVersion = 1,
                appVersion = "1.0",
                createdAt = System.currentTimeMillis(),
                totalItems = manifestItems.size,
                totalSizeBytes = manifestItems.sumOf { it.sizeBytes },
                includesTrash = includeTrash,
                items = manifestItems
            )

            // Step 4: Prepare folder and settings metadata
            val folderMetadataList = folders.map { folder ->
                val folderName = runCatching {
                    String(cryptoManager.decrypt(folder.encryptedName, folder.id.toByteArray()), Charsets.UTF_8)
                }.getOrDefault("Folder_${folder.id.take(8)}")
                ArchiveFolderMetadata(
                    id = folder.id,
                    name = folderName,
                    createdAt = folder.createdAt,
                    coverVaultObjectId = folder.coverVaultObjectId
                )
            }

            val settingsMetadata = if (includeSettings) {
                ArchiveSettingsMetadata(
                    stealthMode = vaultPrefs.isStealthModeEnabled(),
                    flipToLock = vaultPrefs.isFlipToLockEnabled(),
                    autoLockTimeout = vaultPrefs.getAutoLockTimeout(),
                    deleteOriginals = vaultPrefs.isDeleteOriginalsEnabled(),
                    zoomSensitivity = vaultPrefs.getZoomSensitivity()
                )
            } else null

            val metadata = ArchiveMetadata(
                folders = folderMetadataList,
                settings = settingsMetadata
            )

            // Step 5: Begin creating ZIP archive with encrypted entries
            onProgress(ExportPhase.CREATING_ARCHIVE, 0, manifestItems.size, 0L)
            var bytesWritten = 0L

            ZipOutputStream(BufferedOutputStream(FileOutputStream(archiveFile))).use { zos ->
                // 1. Write unencrypted header: vaultt_header.json
                zos.putNextEntry(ZipEntry("vaultt_header.json"))
                val headerBytes = header.toJson().toByteArray(Charsets.UTF_8)
                zos.write(headerBytes)
                zos.closeEntry()
                bytesWritten += headerBytes.size

                // 2. Write AES-256-GCM encrypted manifest: manifest.enc
                val encManifest = ArchiveCrypto.encryptBytes(manifest.toJson().toByteArray(Charsets.UTF_8), derivedKey)
                zos.putNextEntry(ZipEntry("manifest.enc"))
                zos.write(encManifest)
                zos.closeEntry()
                bytesWritten += encManifest.size

                // 3. Write AES-256-GCM encrypted metadata: metadata.enc
                val encMetadata = ArchiveCrypto.encryptBytes(metadata.toJson().toByteArray(Charsets.UTF_8), derivedKey)
                zos.putNextEntry(ZipEntry("metadata.enc"))
                zos.write(encMetadata)
                zos.closeEntry()
                bytesWritten += encMetadata.size

                // 4. Stream encrypted objects into ZIP
                val totalCount = manifestItems.size
                for ((index, item) in manifestItems.withIndex()) {
                    if (isExportCancelled.get()) throw InterruptedException("Export cancelled by user")

                    onProgress(ExportPhase.ENCRYPTING_FILES, index + 1, totalCount, bytesWritten)

                    val objectFile = storageManager.getObjectFile(item.id)
                    if (objectFile.exists()) {
                        zos.putNextEntry(ZipEntry("objects/${item.id}.bin"))
                        // Read decrypted data from existing vault without modifying anything
                        FileInputStream(objectFile).use { fis ->
                            cryptoManager.newDecryptingStream(fis, item.id.toByteArray()).use { decryptedStream ->
                                ArchiveCrypto.encryptStream(decryptedStream, zos, derivedKey) { chunkBytes ->
                                    bytesWritten += chunkBytes
                                }
                            }
                        }
                        zos.closeEntry()
                    }

                    // 5. Thumbnails (if available)
                    val thumbFile = storageManager.getThumbnailFile(item.id)
                    if (thumbFile.exists() && thumbFile.length() > 0) {
                        val thumbBytes = runCatching {
                            cryptoManager.decrypt(thumbFile.readBytes(), (item.id + "_thumb").toByteArray())
                        }.getOrNull()

                        if (thumbBytes != null) {
                            val encThumb = ArchiveCrypto.encryptBytes(thumbBytes, derivedKey)
                            zos.putNextEntry(ZipEntry("thumbnails/${item.id}.bin"))
                            zos.write(encThumb)
                            zos.closeEntry()
                            bytesWritten += encThumb.size
                        }
                    }
                }
            }

            if (isExportCancelled.get()) throw InterruptedException("Export cancelled by user")

            // Step 6: Verify completed archive
            onProgress(ExportPhase.VERIFYING_ARCHIVE, manifestItems.size, manifestItems.size, archiveFile.length())
            val verifyResult = verifyArchiveInternal(archiveFile, derivedKey)
            if (verifyResult.isFailure) {
                throw verifyResult.exceptionOrNull() ?: ArchiveCorruptedException("Archive verification failed.")
            }

            onProgress(ExportPhase.COMPLETED, manifestItems.size, manifestItems.size, archiveFile.length())
            Result.success(archiveFile)
        } catch (e: Exception) {
            if (archiveFile.exists()) archiveFile.delete()
            if (e is InterruptedException) {
                onProgress(ExportPhase.CANCELLED, 0, 0, 0L)
            } else {
                onProgress(ExportPhase.FAILED, 0, 0, 0L)
            }
            Result.failure(e)
        } finally {
            tempWorkspace.deleteRecursively()
            ArchiveCrypto.wipe(password)
        }
    }

    /**
     * Inspects a .vaultt archive header (without requiring password) to verify compatibility.
     */
    suspend fun inspectArchiveHeader(uri: Uri): Result<ArchiveHeader> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "vaultt_header.json") {
                            val jsonStr = String(zis.readBytes(), Charsets.UTF_8)
                            return@runCatching ArchiveHeader.fromJson(jsonStr)
                        }
                        entry = zis.nextEntry
                    }
                    throw ArchiveCorruptedException("Archive header 'vaultt_header.json' is missing.")
                }
            } ?: throw IOException("Could not open input stream from URI.")
        }
    }

    /**
     * Verifies the user-entered password against the archive verifier token instantly.
     */
    suspend fun verifyArchivePassword(uri: Uri, password: CharArray): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val headerResult = inspectArchiveHeader(uri)
            val header = headerResult.getOrThrow()
            val salt = ArchiveCrypto.fromHex(header.saltHex)
            val verifier = ArchiveCrypto.fromHex(header.verifierHex)
            val key = ArchiveCrypto.deriveKey(password, salt, header.iterations)
            ArchiveCrypto.verifyPassword(verifier, key)
        }
    }

    /**
     * Reads manifest and metadata from archive after verifying password to display a Restore Preview.
     */
    suspend fun loadRestorePreview(
        uri: Uri,
        password: CharArray
    ): Result<Pair<ArchiveManifest, ArchiveMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            val header = inspectArchiveHeader(uri).getOrThrow()
            val salt = ArchiveCrypto.fromHex(header.saltHex)
            val verifier = ArchiveCrypto.fromHex(header.verifierHex)
            val key = ArchiveCrypto.deriveKey(password, salt, header.iterations)

            if (!ArchiveCrypto.verifyPassword(verifier, key)) {
                throw InvalidExportPasswordException()
            }

            var manifest: ArchiveManifest? = null
            var metadata: ArchiveMetadata? = null

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "manifest.enc" -> {
                                val encBytes = zis.readBytes()
                                val decBytes = ArchiveCrypto.decryptBytes(encBytes, key)
                                manifest = ArchiveManifest.fromJson(String(decBytes, Charsets.UTF_8))
                            }
                            "metadata.enc" -> {
                                val encBytes = zis.readBytes()
                                val decBytes = ArchiveCrypto.decryptBytes(encBytes, key)
                                metadata = ArchiveMetadata.fromJson(String(decBytes, Charsets.UTF_8))
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            } ?: throw IOException("Could not read archive.")

            val finalManifest = manifest ?: throw ArchiveCorruptedException("manifest.enc missing from archive.")
            val finalMetadata = metadata ?: ArchiveMetadata(emptyList(), null)
            Pair(finalManifest, finalMetadata)
        }
    }

    /**
     * Restores an encrypted archive into the vault.
     * Supports Merge (keeps existing vault intact) or Replace (with explicit confirmation).
     */
    suspend fun restoreArchive(
        uri: Uri,
        password: CharArray,
        replaceExisting: Boolean,
        onProgress: (phase: RestorePhase, processed: Int, total: Int, bytesProcessed: Long) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        isRestoreCancelled.set(false)
        val tempDir = File(context.cacheDir, "vault_restore_temp_${UUID.randomUUID()}")
        tempDir.mkdirs()

        try {
            onProgress(RestorePhase.INSPECTING_ARCHIVE, 0, 0, 0L)
            val header = inspectArchiveHeader(uri).getOrThrow()
            val salt = ArchiveCrypto.fromHex(header.saltHex)
            val verifier = ArchiveCrypto.fromHex(header.verifierHex)
            val key = ArchiveCrypto.deriveKey(password, salt, header.iterations)

            onProgress(RestorePhase.VERIFYING_PASSWORD, 0, 0, 0L)
            if (!ArchiveCrypto.verifyPassword(verifier, key)) {
                throw InvalidExportPasswordException()
            }

            onProgress(RestorePhase.PARSING_MANIFEST, 0, 0, 0L)
            // Extract and decrypt manifest and metadata
            var manifest: ArchiveManifest? = null
            var metadata: ArchiveMetadata? = null

            // Stage archive to temp file if reading from content URI for random access or clean stream
            val stagedZip = File(tempDir, "source.zip")
            context.contentResolver.openInputStream(uri)?.use { src ->
                FileOutputStream(stagedZip).use { dst -> src.copyTo(dst) }
            } ?: throw IOException("Could not read restore archive.")

            ZipFile(stagedZip).use { zip ->
                val manifestEntry = zip.getEntry("manifest.enc") ?: throw ArchiveCorruptedException("manifest.enc missing")
                zip.getInputStream(manifestEntry).use {
                    val dec = ArchiveCrypto.decryptBytes(it.readBytes(), key)
                    manifest = ArchiveManifest.fromJson(String(dec, Charsets.UTF_8))
                }

                val metadataEntry = zip.getEntry("metadata.enc")
                if (metadataEntry != null) {
                    zip.getInputStream(metadataEntry).use {
                        val dec = ArchiveCrypto.decryptBytes(it.readBytes(), key)
                        metadata = ArchiveMetadata.fromJson(String(dec, Charsets.UTF_8))
                    }
                }
            }

            val finalManifest = manifest ?: throw ArchiveCorruptedException("Invalid manifest.")
            val finalMetadata = metadata ?: ArchiveMetadata(emptyList(), null)

            if (isRestoreCancelled.get()) throw InterruptedException("Restore cancelled.")

            // Handle Conflict: If replaceExisting, wipe old vault
            if (replaceExisting) {
                storageManager.clearVault()
                vaultObjectDao.deleteAll()
            }

            // Restore Folders
            finalMetadata.folders.forEach { folderMeta ->
                val encryptedFolderName = cryptoManager.encrypt(folderMeta.name.toByteArray(Charsets.UTF_8), folderMeta.id.toByteArray())
                albumDao.insert(
                    AlbumEntity(
                        id = folderMeta.id,
                        encryptedName = encryptedFolderName,
                        createdAt = folderMeta.createdAt,
                        coverVaultObjectId = folderMeta.coverVaultObjectId
                    )
                )
            }

            // Restore Settings (if present)
            finalMetadata.settings?.let { s ->
                vaultPrefs.setStealthModeEnabled(s.stealthMode)
                vaultPrefs.setFlipToLockEnabled(s.flipToLock)
                vaultPrefs.setAutoLockTimeout(s.autoLockTimeout)
                vaultPrefs.setDeleteOriginalsEnabled(s.deleteOriginals)
                vaultPrefs.setZoomSensitivity(s.zoomSensitivity)
            }

            // Restore Objects
            val totalItems = finalManifest.items.size
            var restoredCount = 0
            var bytesProcessed = 0L

            ZipFile(stagedZip).use { zip ->
                for ((index, item) in finalManifest.items.withIndex()) {
                    if (isRestoreCancelled.get()) throw InterruptedException("Restore cancelled.")

                    onProgress(RestorePhase.RESTORING_OBJECTS, index + 1, totalItems, bytesProcessed)

                    val objectEntry = zip.getEntry("objects/${item.id}.bin")
                    if (objectEntry != null) {
                        val targetVaultFile = storageManager.getObjectFile(item.id)
                        val tempDecryptedFile = File(tempDir, "temp_dec_${item.id}")

                        // Decrypt from archive stream into temp decrypted file
                        zip.getInputStream(objectEntry).use { zis ->
                            FileOutputStream(tempDecryptedFile).use { fos ->
                                ArchiveCrypto.decryptStream(zis, fos, key) { bytes ->
                                    bytesProcessed += bytes
                                }
                            }
                        }

                        // Verify integrity against manifest SHA-256
                        val calculatedHash = calculateSha256(tempDecryptedFile)
                        if (item.sha256.isNotEmpty() && !calculatedHash.equals(item.sha256, ignoreCase = true)) {
                            tempDecryptedFile.delete()
                            throw ArchiveCorruptedException("Integrity verification failed for item '${item.originalName}'. Hash mismatch.")
                        }

                        // Encrypt into Vault T storage using local device Tink key
                        FileInputStream(tempDecryptedFile).use { decFis ->
                            FileOutputStream(targetVaultFile).use { encFos ->
                                cryptoManager.newEncryptingStream(encFos, item.id.toByteArray()).use { encryptingStream ->
                                    decFis.copyTo(encryptingStream)
                                }
                            }
                        }
                        tempDecryptedFile.delete()

                        // Restore thumbnail if exists
                        val thumbEntry = zip.getEntry("thumbnails/${item.id}.bin")
                        if (thumbEntry != null) {
                            zip.getInputStream(thumbEntry).use { tis ->
                                val decThumbBytes = ArchiveCrypto.decryptBytes(tis.readBytes(), key)
                                val encThumbForDevice = cryptoManager.encrypt(decThumbBytes, (item.id + "_thumb").toByteArray())
                                storageManager.getThumbnailFile(item.id).writeBytes(encThumbForDevice)
                            }
                        }

                        // Insert database entity with local Tink encryption
                        val encNameForDevice = cryptoManager.encrypt(item.originalName.toByteArray(Charsets.UTF_8), item.id.toByteArray())
                        val entity = VaultObjectEntity(
                            id = item.id,
                            encryptedName = encNameForDevice,
                            type = item.type,
                            mimeType = item.mimeType,
                            size = item.sizeBytes,
                            createdAt = item.createdAt,
                            modifiedAt = item.modifiedAt,
                            hash = item.sha256,
                            isFavorite = item.isFavorite,
                            isDeleted = item.isDeleted,
                            folderId = item.folderId
                        )
                        vaultObjectDao.insert(entity)
                        restoredCount++
                    }
                }
            }

            onProgress(RestorePhase.COMPLETED, restoredCount, totalItems, bytesProcessed)
            Result.success(restoredCount)
        } catch (e: Exception) {
            if (e is InterruptedException) {
                onProgress(RestorePhase.CANCELLED, 0, 0, 0L)
            } else {
                onProgress(RestorePhase.FAILED, 0, 0, 0L)
            }
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
            ArchiveCrypto.wipe(password)
        }
    }

    private fun verifyArchiveInternal(archiveFile: File, key: javax.crypto.SecretKey): Result<Unit> {
        return runCatching {
            ZipFile(archiveFile).use { zip ->
                val headerEntry = zip.getEntry("vaultt_header.json") ?: throw ArchiveCorruptedException("vaultt_header.json missing")
                val manifestEntry = zip.getEntry("manifest.enc") ?: throw ArchiveCorruptedException("manifest.enc missing")
                val metadataEntry = zip.getEntry("metadata.enc") ?: throw ArchiveCorruptedException("metadata.enc missing")

                val manifest: ArchiveManifest
                zip.getInputStream(manifestEntry).use {
                    val dec = ArchiveCrypto.decryptBytes(it.readBytes(), key)
                    manifest = ArchiveManifest.fromJson(String(dec, Charsets.UTF_8))
                }

                for (item in manifest.items) {
                    val entry = zip.getEntry("objects/${item.id}.bin") ?: throw ArchiveCorruptedException("Archive missing object ${item.id}")
                    if (entry.size == 0L && item.sizeBytes > 0L) {
                        throw ArchiveCorruptedException("Corrupted zero-byte entry for object ${item.id}")
                    }
                }
            }
        }
    }

    private fun calculateSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(16384)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

