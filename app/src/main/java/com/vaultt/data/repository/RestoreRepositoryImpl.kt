package com.vaultt.data.repository

import android.net.Uri
import com.vaultt.core.backup.VaultArchiveManager
import com.vaultt.domain.repository.RestoreRepository
import com.vaultt.domain.repository.RestoreState
import com.vaultt.feature.backup.model.ArchiveHeader
import com.vaultt.feature.backup.model.ArchiveManifest
import com.vaultt.feature.backup.model.ArchiveMetadata
import com.vaultt.feature.backup.model.RestorePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreRepositoryImpl @Inject constructor(
    private val archiveManager: VaultArchiveManager
) : RestoreRepository {

    override suspend fun inspectHeader(uri: Uri): Result<ArchiveHeader> {
        return archiveManager.inspectArchiveHeader(uri)
    }

    override suspend fun verifyPassword(uri: Uri, password: CharArray): Result<Boolean> {
        return archiveManager.verifyArchivePassword(uri, password)
    }

    override suspend fun getRestorePreview(
        uri: Uri,
        password: CharArray
    ): Result<Pair<ArchiveManifest, ArchiveMetadata>> {
        return archiveManager.loadRestorePreview(uri, password)
    }

    override fun restoreVault(
        uri: Uri,
        password: CharArray,
        replaceExisting: Boolean
    ): Flow<RestoreState> = flow {
        emit(RestoreState(phase = RestorePhase.INSPECTING_ARCHIVE))

        var lastState = RestoreState(phase = RestorePhase.INSPECTING_ARCHIVE)
        val result = archiveManager.restoreArchive(
            uri = uri,
            password = password,
            replaceExisting = replaceExisting
        ) { phase, processed, total, bytes ->
            val state = RestoreState(
                phase = phase,
                processedFiles = processed,
                totalFiles = total,
                bytesProcessed = bytes
            )
            lastState = state
        }

        if (result.isSuccess) {
            val restoredCount = result.getOrThrow()
            emit(
                RestoreState(
                    phase = RestorePhase.COMPLETED,
                    processedFiles = lastState.totalFiles,
                    totalFiles = lastState.totalFiles,
                    bytesProcessed = lastState.bytesProcessed,
                    restoredCount = restoredCount
                )
            )
        } else {
            val ex = result.exceptionOrNull()
            val isCancelled = ex is InterruptedException
            val phase = if (isCancelled) RestorePhase.CANCELLED else RestorePhase.FAILED
            emit(
                RestoreState(
                    phase = phase,
                    errorMessage = ex?.message ?: "Restore operation failed."
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    override fun cancelRestore() {
        archiveManager.cancelRestore()
    }
}

