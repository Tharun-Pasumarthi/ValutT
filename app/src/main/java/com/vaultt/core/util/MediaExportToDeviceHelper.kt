package com.vaultt.core.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object MediaExportToDeviceHelper {

    suspend fun exportToDevice(
        context: Context,
        repository: VaultRepository,
        item: VaultObject,
        removeFromVault: Boolean = false
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val mimeType = item.mimeType ?: getFallbackMimeType(item.type, item.name)
            val extension = getExtension(item.name)
            val safeName = sanitizeFileName(item.name)

            val targetUri: Uri = when (item.type) {
                VaultObjectType.PHOTO -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VaultT")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("Failed to create MediaStore entry in Pictures")
                }
                VaultObjectType.VIDEO -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
                        put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VaultT")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                    }
                    contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("Failed to create MediaStore entry in Movies")
                }
                VaultObjectType.AUDIO -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, safeName)
                        put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/VaultT")
                            put(MediaStore.Audio.Media.IS_PENDING, 1)
                        }
                    }
                    contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("Failed to create MediaStore entry in Music")
                }
                VaultObjectType.DOCUMENT, VaultObjectType.OTHER -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                            put(MediaStore.Downloads.MIME_TYPE, mimeType)
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VaultT")
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: throw IOException("Failed to create MediaStore entry in Downloads")
                    } else {
                        // Legacy pre-Q downloads fallback
                        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VaultT")
                        if (!downloadDir.exists()) downloadDir.mkdirs()
                        val destFile = File(downloadDir, safeName)
                        repository.getDecryptedStream(item.id).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        return@runCatching Uri.fromFile(destFile)
                    }
                }
            }

            // Stream decrypted vault data to target URI
            contentResolver.openOutputStream(targetUri)?.use { output ->
                repository.getDecryptedStream(item.id).use { input ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Could not open output stream for destination")

            // Clear IS_PENDING on Android Q+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(targetUri, completeValues, null, null)
            }

            // Remove from vault if "move" was requested
            if (removeFromVault) {
                repository.deleteObject(item.id)
            }

            targetUri
        }
    }

    suspend fun exportMultipleToDevice(
        context: Context,
        repository: VaultRepository,
        items: List<VaultObject>,
        removeFromVault: Boolean = false,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        items.forEachIndexed { index, item ->
            val result = exportToDevice(context, repository, item, removeFromVault)
            if (result.isSuccess) successCount++
            onProgress?.invoke(index + 1, items.size)
        }
        successCount
    }

    private fun getFallbackMimeType(type: VaultObjectType, name: String): String {
        return when (type) {
            VaultObjectType.PHOTO -> if (name.endsWith(".png", true)) "image/png" else "image/jpeg"
            VaultObjectType.VIDEO -> "video/mp4"
            VaultObjectType.AUDIO -> "audio/mpeg"
            VaultObjectType.DOCUMENT -> if (name.endsWith(".pdf", true)) "application/pdf" else "application/octet-stream"
            VaultObjectType.OTHER -> "application/octet-stream"
        }
    }

    private fun getExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot) else ""
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}

