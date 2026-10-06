package com.vaultt.domain.repository

import android.content.IntentSender
import android.net.Uri
import com.vaultt.domain.model.VaultObject
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

interface VaultRepository {
    fun getVaultObjects(): Flow<List<VaultObject>>
    fun getVaultObjectsByType(type: String): Flow<List<VaultObject>>
    fun getCountByType(type: String): Flow<Int>
    fun getTrashObjects(): Flow<List<VaultObject>>
    fun getTrashCount(): Flow<Int>
    
    suspend fun importPhoto(uri: Uri, name: String, size: Long, force: Boolean = false): Result<ImportResult>
    suspend fun importMediaStreaming(uri: Uri, name: String, size: Long, type: String, force: Boolean = false): Result<ImportResult>
    suspend fun importFromBytes(bytes: ByteArray, name: String, type: String): Result<String>

    suspend fun getDecryptedData(id: String): Result<ByteArray>
    suspend fun getDecryptedThumbnail(id: String): Result<ByteArray?>
    suspend fun getDecryptedStream(id: String): InputStream
    suspend fun exportObject(id: String, outputStream: OutputStream): Result<Unit>
    
    suspend fun deleteObject(id: String): Result<Unit>
    suspend fun renameObject(id: String, newName: String): Result<Unit>
    suspend fun toggleFavorite(id: String): Result<Unit>
    suspend fun moveToTrash(id: String): Result<Unit>
    suspend fun restoreFromTrash(id: String): Result<Unit>
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
