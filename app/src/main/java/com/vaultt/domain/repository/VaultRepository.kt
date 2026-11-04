package com.vaultt.domain.repository

import android.content.IntentSender
import android.net.Uri
import com.vaultt.domain.model.VaultObject
import com.vaultt.core.database.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

interface VaultRepository {
    fun getVaultObjects(): Flow<List<VaultObject>>
    fun getVaultObjectsByType(type: String): Flow<List<VaultObject>>
    fun getCountByType(type: String): Flow<Int>
    fun getTrashObjects(): Flow<List<VaultObject>>
    fun getTrashCount(): Flow<Int>
    
    suspend fun importPhoto(uri: Uri, name: String, size: Long, force: Boolean = false, folderId: String? = null): Result<ImportResult>
    suspend fun importMediaStreaming(uri: Uri, name: String, size: Long, type: String, force: Boolean = false, folderId: String? = null): Result<ImportResult>
    suspend fun importFromBytes(bytes: ByteArray, name: String, type: String, folderId: String? = null): Result<String>
    fun getFolders(): Flow<List<VaultFolder>>
    fun getFolderObjects(folderId: String): Flow<List<VaultObject>>
    fun getUnfiledObjects(): Flow<List<VaultObject>>
    suspend fun createFolder(name: String): Result<String>
    suspend fun renameFolder(id: String, name: String): Result<Unit>
    suspend fun setFolderCover(id: String, objectId: String?): Result<Unit>
    suspend fun deleteFolder(id: String): Result<Unit>
    suspend fun moveToFolder(objectId: String, folderId: String?): Result<Unit>
    suspend fun moveToFolder(objectIds: List<String>, folderId: String?): Result<Unit>

    suspend fun getDecryptedData(id: String): Result<ByteArray>
    suspend fun getDecryptedThumbnail(id: String): Result<ByteArray?>
    suspend fun getDecryptedStream(id: String): InputStream
    suspend fun exportObject(id: String, outputStream: OutputStream): Result<Unit>
    
    suspend fun deleteObject(id: String): Result<Unit>
    suspend fun renameObject(id: String, newName: String): Result<Unit>
    suspend fun toggleFavorite(id: String): Result<Unit>
    suspend fun moveToTrash(id: String): Result<Unit>
    suspend fun moveToTrash(ids: List<String>): Result<Unit>
    suspend fun restoreFromTrash(id: String): Result<Unit>
    suspend fun restoreFromTrash(ids: List<String>): Result<Unit>
    suspend fun deleteObjects(ids: List<String>): Result<Unit>
    suspend fun emptyTrash(): Result<Unit>
    
    suspend fun getStorageStats(): Map<String, Long>
    suspend fun destroyVault(): Result<Unit>
    
    fun getDeleteRequestIntentSender(uris: List<Uri>): IntentSender?
}

data class ImportResult(
    val id: String?,
    val isDuplicate: Boolean = false,
    val sourceUri: Uri? = null,
    val name: String? = null
)

data class VaultFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val itemCount: Int = 0,
    val coverVaultObjectId: String? = null
)
