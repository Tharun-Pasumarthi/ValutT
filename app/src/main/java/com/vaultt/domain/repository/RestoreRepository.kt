package com.vaultt.domain.repository

import android.net.Uri
import com.vaultt.feature.backup.model.ArchiveHeader
import com.vaultt.feature.backup.model.ArchiveManifest
import com.vaultt.feature.backup.model.ArchiveMetadata
import com.vaultt.feature.backup.model.RestorePhase
import kotlinx.coroutines.flow.Flow

data class RestoreState(
    val phase: RestorePhase = RestorePhase.IDLE,
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val bytesProcessed: Long = 0L,
    val restoredCount: Int = 0,
    val errorMessage: String? = null
)

interface RestoreRepository {
    suspend fun inspectHeader(uri: Uri): Result<ArchiveHeader>
    suspend fun verifyPassword(uri: Uri, password: CharArray): Result<Boolean>
    suspend fun getRestorePreview(uri: Uri, password: CharArray): Result<Pair<ArchiveManifest, ArchiveMetadata>>
    fun restoreVault(uri: Uri, password: CharArray, replaceExisting: Boolean): Flow<RestoreState>
    fun cancelRestore()
}

