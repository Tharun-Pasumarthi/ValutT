package com.vaultt.data.repository

import android.app.RecoverableSecurityException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.vaultt.core.database.dao.VaultObjectDao
import com.vaultt.core.database.entity.VaultObjectEntity
import com.vaultt.core.security.CryptoManager
import com.vaultt.core.storage.StorageManager
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.domain.repository.ImportResult
import com.vaultt.domain.repository.VaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultObjectDao: VaultObjectDao,
    private val cryptoManager: CryptoManager,
    private val storageManager: StorageManager
) : VaultRepository {

    override fun getVaultObjects(): Flow<List<VaultObject>> {
        return vaultObjectDao.getAllObjects().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getVaultObjectsByType(type: String): Flow<List<VaultObject>> {
        return vaultObjectDao.getObjectsByType(type).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getCountByType(type: String): Flow<Int> {
        return vaultObjectDao.getCountByType(type)
    }

    override fun getTrashObjects(): Flow<List<VaultObject>> {
        return vaultObjectDao.getTrashObjects().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getTrashCount(): Flow<Int> {
        return vaultObjectDao.getTrashCount()
    }

    override suspend fun importPhoto(uri: Uri, name: String, size: Long, force: Boolean): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = context.contentResolver.openInputStream(uri)?.use { calculateHash(it) } ?: throw IOException("Could not read stream")
            
            if (!force) {
                val existing = vaultObjectDao.getAllObjects().first().find { it.hash == hash }
                if (existing != null) {
                    return@runCatching ImportResult(id = existing.id, isDuplicate = true, sourceUri = uri, name = name)
                }
            }

            val id = UUID.randomUUID().toString()
            val file = storageManager.getObjectFile(id)
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { fos ->
                    cryptoManager.newEncryptingStream(fos, id.toByteArray()).use { encryptingStream ->
                        inputStream.copyTo(encryptingStream)
                    }
                }
            }

            // Thumbnail
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                generateThumbnail(bytes)?.let { thumbBytes ->
                    val encryptedThumb = cryptoManager.encrypt(thumbBytes, (id + "_thumb").toByteArray())
                    storageManager.getThumbnailFile(id).writeBytes(encryptedThumb)
                }
            }

            val entity = VaultObjectEntity(
                id = id,
                encryptedName = cryptoManager.encrypt(name.toByteArray(), id.toByteArray()),
                type = VaultObjectType.PHOTO.name,
                size = size,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                hash = hash
            )
            vaultObjectDao.insert(entity)
            
            ImportResult(id = id, isDuplicate = false, sourceUri = uri, name = name)
        }
    }

    override suspend fun importMediaStreaming(uri: Uri, name: String, size: Long, type: String, force: Boolean): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = context.contentResolver.openInputStream(uri)?.use { calculateHash(it) } ?: throw IOException("Could not read stream")

            if (!force) {
                val existing = vaultObjectDao.getAllObjects().first().find { it.hash == hash }
                if (existing != null) {
                    return@runCatching ImportResult(id = existing.id, isDuplicate = true, sourceUri = uri, name = name)
                }
            }

            val id = UUID.randomUUID().toString()
            val file = storageManager.getObjectFile(id)
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { fos ->
                    cryptoManager.newEncryptingStream(fos, id.toByteArray()).use { encryptingStream ->
                        inputStream.copyTo(encryptingStream)
                    }
                }
            }

            if (type == VaultObjectType.VIDEO.name) {
                generateVideoThumbnail(uri)?.let { thumbBytes ->
                    val encryptedThumb = cryptoManager.encrypt(thumbBytes, (id + "_thumb").toByteArray())
                    storageManager.getThumbnailFile(id).writeBytes(encryptedThumb)
                }
            }

            val entity = VaultObjectEntity(
                id = id,
                encryptedName = cryptoManager.encrypt(name.toByteArray(), id.toByteArray()),
                type = type,
                size = size,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                hash = hash
            )
            vaultObjectDao.insert(entity)
            
            ImportResult(id = id, isDuplicate = false, sourceUri = uri, name = name)
        }
    }

    override suspend fun importFromBytes(bytes: ByteArray, name: String, type: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = calculateHash(ByteArrayInputStream(bytes))
            val id = UUID.randomUUID().toString()
            val file = storageManager.getObjectFile(id)
            
            FileOutputStream(file).use { fos ->
                cryptoManager.newEncryptingStream(fos, id.toByteArray()).use { it.write(bytes) }
            }

            if (type == VaultObjectType.PHOTO.name) {
                generateThumbnail(bytes)?.let { thumbBytes ->
                    val encryptedThumb = cryptoManager.encrypt(thumbBytes, (id + "_thumb").toByteArray())
                    storageManager.getThumbnailFile(id).writeBytes(encryptedThumb)
                }
            }

            val entity = VaultObjectEntity(
                id = id,
                encryptedName = cryptoManager.encrypt(name.toByteArray(), id.toByteArray()),
                type = type,
                size = bytes.size.toLong(),
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                hash = hash
            )
            vaultObjectDao.insert(entity)
            id
        }
    }

    override fun getDeleteRequestIntentSender(uris: List<Uri>): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_30) {
            return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }
        return null
    }

    override suspend fun getDecryptedData(id: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val file = storageManager.getObjectFile(id)
            val out = ByteArrayOutputStream()
            FileInputStream(file).use { fis ->
                cryptoManager.newDecryptingStream(fis, id.toByteArray()).use { decStream ->
                    decStream.copyTo(out)
                }
            }
            out.toByteArray()
        }
    }

    override suspend fun getDecryptedThumbnail(id: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            val file = storageManager.getThumbnailFile(id)
            if (!file.exists()) return@runCatching null
            val encryptedBytes = file.readBytes()
            cryptoManager.decrypt(encryptedBytes, (id + "_thumb").toByteArray())
        }
    }

    override suspend fun getDecryptedStream(id: String): InputStream {
        val file = storageManager.getObjectFile(id)
        val fis = FileInputStream(file)
        return cryptoManager.newDecryptingStream(fis, id.toByteArray())
    }

    override suspend fun exportObject(id: String, outputStream: OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            getDecryptedStream(id).use { decrypted ->
                decrypted.copyTo(outputStream)
            }
            Unit
        }
    }

    override suspend fun deleteObject(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            storageManager.getObjectFile(id).delete()
            storageManager.getThumbnailFile(id).delete()
            vaultObjectDao.deleteById(id)
            Unit
        }
    }

    override suspend fun renameObject(id: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encryptedName = cryptoManager.encrypt(newName.toByteArray(), id.toByteArray())
            vaultObjectDao.updateName(id, encryptedName)
            Unit
        }
    }

    override suspend fun toggleFavorite(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            vaultObjectDao.toggleFavorite(id)
        }
    }

    override suspend fun moveToTrash(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            vaultObjectDao.moveToTrash(id)
        }
    }

    override suspend fun restoreFromTrash(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            vaultObjectDao.restoreFromTrash(id)
        }
    }

    override suspend fun emptyTrash(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val trashItems = vaultObjectDao.getTrashObjects().first()
            trashItems.forEach { item ->
                storageManager.getObjectFile(item.id).delete()
                storageManager.getThumbnailFile(item.id).delete()
            }
            vaultObjectDao.emptyTrash()
        }
    }

    override suspend fun getStorageStats(): Map<String, Long> = withContext(Dispatchers.IO) {
        val all = vaultObjectDao.getAllObjects().first()
        val stats = mutableMapOf<String, Long>()
        var total = 0L
        all.forEach {
            stats[it.type] = (stats[it.type] ?: 0L) + it.size
            total += it.size
        }
        stats["TOTAL"] = total
        stats
    }

    override suspend fun destroyVault(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            storageManager.clearVault()
            vaultObjectDao.deleteAll()
        }
    }

    override suspend fun getObjectByHash(hash: String): VaultObject? {
        return vaultObjectDao.getAllObjects().first().find { it.hash == hash }?.toDomainModel()
    }

    private fun generateThumbnail(data: ByteArray): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
        val thumb = ThumbnailUtils.extractThumbnail(bitmap, 300, 300)
        val stream = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }

    private fun generateVideoThumbnail(uri: Uri): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(1000000) 
            retriever.release()
            
            val stream = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateHash(inputStream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
        }
        val digest = md.digest()
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun VaultObjectEntity.toDomainModel(): VaultObject {
        val name = try {
            String(cryptoManager.decrypt(encryptedName, id.toByteArray()))
        } catch (e: Exception) {
            "Unknown File"
        }

        return VaultObject(
            id = id,
            name = name,
            type = VaultObjectType.valueOf(type),
            size = size,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            isFavorite = isFavorite
        )
    }
}
