package com.vaultt.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vaultDir = File(context.filesDir, "vault")
    private val objectsDir = File(vaultDir, "objects")
    private val thumbnailsDir = File(vaultDir, "thumbnails")

    init {
        if (!vaultDir.exists()) vaultDir.mkdirs()
        if (!objectsDir.exists()) objectsDir.mkdirs()
        if (!thumbnailsDir.exists()) thumbnailsDir.mkdirs()
    }

    fun getObjectFile(id: String): File {
        return File(objectsDir, "$id.vlt")
    }

    fun getThumbnailFile(id: String): File {
        return File(thumbnailsDir, "$id.thumb.vlt")
    }
    
    fun clearVault() {
        vaultDir.deleteRecursively()
        vaultDir.mkdirs()
        objectsDir.mkdirs()
        thumbnailsDir.mkdirs()
    }
}
