package com.vaultt.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vaultt.core.security.SecurityManager
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FileShareHelper {

    suspend fun shareVaultObjects(
        context: Context,
        repository: VaultRepository,
        objects: List<VaultObject>,
        securityManager: SecurityManager? = null
    ) {
        if (objects.isEmpty()) return

        runCatching {
            val shareDir = File(context.cacheDir, "shared").apply {
                if (exists()) {
                    listFiles()?.forEach { it.delete() }
                } else {
                    mkdirs()
                }
            }

            val uris = ArrayList<Uri>()
            var primaryMimeType: String? = null

            withContext(Dispatchers.IO) {
                objects.forEachIndexed { index, item ->
                    val rawName = item.name
                    val safeName = rawName
                        .substringAfterLast('/')
                        .substringAfterLast('\\')
                        .replace(Regex("""[^\w.\- ()]"""), "_")
                        .ifBlank { "shared_file_$index" }

                    val tempFile = if (objects.count { it.name == item.name } > 1) {
                        val extension = safeName.substringAfterLast('.', "")
                        val base = if (extension.isNotEmpty()) safeName.substringBeforeLast('.') else safeName
                        val extSuffix = if (extension.isNotEmpty()) ".$extension" else ""
                        File(shareDir, "${base}_${index + 1}$extSuffix")
                    } else {
                        File(shareDir, safeName)
                    }

                    FileOutputStream(tempFile).use { fos ->
                        repository.exportObject(item.id, fos).getOrThrow()
                    }

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile
                    )
                    uris.add(uri)

                    if (primaryMimeType == null) {
                        primaryMimeType = item.mimeType
                            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(tempFile.extension.lowercase())
                    } else if (primaryMimeType != item.mimeType) {
                        val mainCategory = primaryMimeType?.substringBefore('/')
                        val itemCategory = item.mimeType?.substringBefore('/')
                        primaryMimeType = if (mainCategory != null && mainCategory == itemCategory) {
                            "$mainCategory/*"
                        } else {
                            "*/*"
                        }
                    }
                }
            }

            if (uris.isEmpty()) return@runCatching

            val resolvedMime = primaryMimeType ?: "*/*"
            val shareIntent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = resolvedMime
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = resolvedMime
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val title = if (objects.size > 1) "Share ${objects.size} items" else "Share item"
            val chooser = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.packageManager.queryIntentActivities(shareIntent, 0).forEach { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                uris.forEach { uri ->
                    context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            securityManager?.beginExternalActivity()
            context.startActivity(chooser)
        }.onFailure { e ->
            val message = if (e is ActivityNotFoundException) {
                "No app available to share"
            } else {
                "Unable to share file: ${e.localizedMessage ?: "Unknown error"}"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun openVaultObject(
        context: Context,
        repository: VaultRepository,
        item: VaultObject,
        securityManager: SecurityManager? = null
    ) {
        runCatching {
            val shareDir = File(context.cacheDir, "opened").apply {
                if (exists()) {
                    listFiles()?.forEach { it.delete() }
                } else {
                    mkdirs()
                }
            }

            val safeName = item.name
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("""[^\w.\- ()]"""), "_")
                .ifBlank { "opened_file" }

            val tempFile = File(shareDir, safeName)

            withContext(Dispatchers.IO) {
                FileOutputStream(tempFile).use { fos ->
                    repository.exportObject(item.id, fos).getOrThrow()
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val resolvedMime = item.mimeType
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(tempFile.extension.lowercase())
                ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Open file").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.packageManager.queryIntentActivities(intent, 0).forEach { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            securityManager?.beginExternalActivity()
            context.startActivity(chooser)
        }.onFailure { e ->
            val message = if (e is ActivityNotFoundException) {
                "No app available to open this file"
            } else {
                "Unable to open file: ${e.localizedMessage ?: "Unknown error"}"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
