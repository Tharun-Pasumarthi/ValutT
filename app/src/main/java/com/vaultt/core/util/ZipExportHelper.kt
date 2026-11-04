package com.vaultt.core.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.vaultt.core.security.SecurityManager
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipExportHelper {

    private const val TAG = "ZipExportHelper"

    /**
     * Exports all items of a single folder into a ZIP archive and triggers the Android share sheet.
     */
    suspend fun exportFolderAsZip(
        context: Context,
        repository: VaultRepository,
        folderName: String,
        items: List<VaultObject>,
        securityManager: SecurityManager
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            securityManager.beginExternalActivity()

            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanFolderName = folderName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val zipFile = File(exportsDir, "${cleanFolderName}_${System.currentTimeMillis()}.zip")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                val usedNames = mutableSetOf<String>()

                items.forEach { item ->
                    try {
                        val baseName = sanitizeFileName(item.name)
                        val uniqueName = makeUniqueName(baseName, usedNames)
                        usedNames.add(uniqueName)

                        val entry = ZipEntry(uniqueName)
                        zos.putNextEntry(entry)

                        repository.getDecryptedStream(item.id).use { inputStream ->
                            inputStream.copyTo(zos)
                        }
                        zos.closeEntry()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to pack item ${item.name} into ZIP: ${e.message}")
                    }
                }
            }

            // Launch system share sheet
            launchShareZip(context, zipFile, "Export: $folderName")
            zipFile
        }
    }

    /**
     * Exports the entire vault storage into an organized ZIP archive (Photos, Videos, Audio, Documents, Folders).
     */
    suspend fun exportEntireVaultAsZip(
        context: Context,
        repository: VaultRepository,
        securityManager: SecurityManager,
        onProgress: (current: Int, total: Int, currentName: String) -> Unit = { _, _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            securityManager.beginExternalActivity()

            val allObjects = repository.getVaultObjects().first()
            val folders = repository.getFolders().first().associateBy { it.id }

            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(exportsDir, "VaultT_Export_$timestamp.zip")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                val usedPaths = mutableSetOf<String>()
                val total = allObjects.size

                allObjects.forEachIndexed { index, item ->
                    try {
                        onProgress(index + 1, total, item.name)

                        // Determine subfolder inside the ZIP
                        val folderPrefix = if (item.folderId != null && folders.containsKey(item.folderId)) {
                            val fName = sanitizeFileName(folders[item.folderId]!!.name)
                            "Folders/$fName"
                        } else {
                            when (item.type) {
                                VaultObjectType.PHOTO -> "Photos"
                                VaultObjectType.VIDEO -> "Videos"
                                VaultObjectType.AUDIO -> "Audio"
                                VaultObjectType.DOCUMENT -> "Documents"
                                VaultObjectType.OTHER -> "Others"
                            }
                        }

                        val baseName = sanitizeFileName(item.name)
                        val fullPath = "$folderPrefix/$baseName"
                        val uniquePath = makeUniqueName(fullPath, usedPaths)
                        usedPaths.add(uniquePath)

                        val entry = ZipEntry(uniquePath)
                        zos.putNextEntry(entry)

                        repository.getDecryptedStream(item.id).use { inputStream ->
                            inputStream.copyTo(zos)
                        }
                        zos.closeEntry()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to pack ${item.name} into entire vault zip: ${e.message}")
                    }
                }
            }

            launchShareZip(context, zipFile, "Vault T Full Backup (ZIP)")
            zipFile
        }
    }

    private fun launchShareZip(context: Context, zipFile: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "file" }
    }

    private fun makeUniqueName(candidate: String, existing: Set<String>): String {
        if (candidate !in existing) return candidate
        val dotIndex = candidate.lastIndexOf('.')
        val base = if (dotIndex > 0) candidate.substring(0, dotIndex) else candidate
        val ext = if (dotIndex > 0) candidate.substring(dotIndex) else ""

        var counter = 1
        while ("$base ($counter)$ext" in existing) {
            counter++
        }
        return "$base ($counter)$ext"
    }
}
