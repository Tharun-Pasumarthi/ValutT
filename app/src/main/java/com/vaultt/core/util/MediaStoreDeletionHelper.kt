package com.vaultt.core.util

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreDeletionHelper {

    private const val TAG = "MediaStoreDeletion"

    suspend fun requestDeleteOriginals(
        context: Context,
        uris: List<Uri>,
        onIntentSender: suspend (IntentSender) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext

        val contentResolver = context.contentResolver
        val canonicalMediaStoreUris = mutableListOf<Uri>()
        val directDeleteUris = mutableListOf<Uri>()

        uris.forEach { rawUri ->
            val canonical = resolveToCanonicalMediaStoreUri(context, rawUri)
            if (canonical != null) {
                canonicalMediaStoreUris.add(canonical)
            } else {
                directDeleteUris.add(rawUri)
            }
        }

        // 1. Direct deletion for SAF documents or non-MediaStore URIs
        directDeleteUris.forEach { uri ->
            runCatching {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.deleteDocument(contentResolver, uri)
                } else {
                    contentResolver.delete(uri, null, null)
                }
            }.onFailure {
                Log.w(TAG, "Direct delete failed for $uri: ${it.message}")
            }
        }

        // 2. On Android 11+ (API 30+), MediaStore items created by other apps require MediaStore.createDeleteRequest
        if (canonicalMediaStoreUris.isNotEmpty()) {
            val distinctUris = canonicalMediaStoreUris.distinct()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(
                        contentResolver,
                        distinctUris
                    )
                    onIntentSender(pendingIntent.intentSender)
                } catch (e: Exception) {
                    Log.e(TAG, "createDeleteRequest failed: ${e.message}", e)
                    // Fallback to direct delete in case app has permissions
                    distinctUris.forEach { uri ->
                        runCatching { contentResolver.delete(uri, null, null) }
                    }
                }
            } else {
                distinctUris.forEach { uri ->
                    runCatching { contentResolver.delete(uri, null, null) }
                }
            }
        }
    }

    private fun resolveToCanonicalMediaStoreUri(context: Context, uri: Uri): Uri? {
        val contentResolver = context.contentResolver
        val uriStr = uri.toString()

        // Case A: Already a canonical MediaStore URI
        if (uriStr.startsWith("content://media/external/images/media/") ||
            uriStr.startsWith("content://media/external/video/media/") ||
            uriStr.startsWith("content://media/external/audio/media/")
        ) {
            return uri
        }

        // Case B: Document URI from MediaDocumentsProvider (e.g. content://com.android.providers.media.documents/document/image%3A1234)
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            if (docId != null) {
                val split = docId.split(":")
                if (split.size == 2) {
                    val type = split[0].lowercase()
                    val id = split[1].toLongOrNull()
                    if (id != null) {
                        return when (type) {
                            "image" -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            "video" -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            "audio" -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                            else -> null
                        }
                    }
                }
            }
        }

        // Case C: Android 13+ Photo Picker URI (e.g. content://media/picker/.../media/1000000034)
        val lastSegmentId = uri.lastPathSegment?.toLongOrNull()
        if (lastSegmentId != null) {
            val mimeType = runCatching { contentResolver.getType(uri) }.getOrNull()?.lowercase() ?: ""
            return when {
                mimeType.startsWith("video") -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, lastSegmentId)
                mimeType.startsWith("audio") -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, lastSegmentId)
                else -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, lastSegmentId)
            }
        }

        // Case D: Query MediaStore by DISPLAY_NAME as secondary resolution
        runCatching {
            var fileName: String? = null
            var fileSize: Long = -1L

            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) fileName = c.getString(nameIdx)
                    if (sizeIdx != -1) fileSize = c.getLong(sizeIdx)
                }
            }

            if (!fileName.isNullOrBlank()) {
                val imgSelection = if (fileSize > 0) {
                    "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.SIZE} = ?"
                } else {
                    "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                }
                val imgArgs = if (fileSize > 0) arrayOf(fileName!!, fileSize.toString()) else arrayOf(fileName!!)

                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    imgSelection,
                    imgArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val foundId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, foundId)
                    }
                }

                val vidSelection = if (fileSize > 0) {
                    "${MediaStore.Video.Media.DISPLAY_NAME} = ? AND ${MediaStore.Video.Media.SIZE} = ?"
                } else {
                    "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
                }
                val vidArgs = if (fileSize > 0) arrayOf(fileName!!, fileSize.toString()) else arrayOf(fileName!!)

                contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    vidSelection,
                    vidArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val foundId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                        return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, foundId)
                    }
                }
            }
        }

        return null
    }
}
