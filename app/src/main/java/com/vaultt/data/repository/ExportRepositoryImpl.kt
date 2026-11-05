package com.vaultt.data.repository

import android.content.Context
import android.net.Uri
import com.vaultt.core.backup.VaultArchiveManager
import com.vaultt.domain.repository.ExportHistoryRepository
import com.vaultt.domain.repository.ExportRepository
import com.vaultt.domain.repository.ExportState
import com.vaultt.feature.backup.model.ExportPhase
import com.vaultt.feature.backup.model.ExportPreviewStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archiveManager: VaultArchiveManager,
    private val exportHistoryRepository: ExportHistoryRepository
) : ExportRepository {

    override suspend fun getPreviewStats(
        includeTrash: Boolean,
        targetItemIds: List<String>?
    ): ExportPreviewStats {
        return archiveManager.calculatePreview(includeTrash, targetItemIds)
    }

    override fun exportVault(
        password: CharArray,
        includeTrash: Boolean,
        includeSettings: Boolean,
        targetItemIds: List<String>?
    ): Flow<ExportState> = flow {
        emit(ExportState(phase = ExportPhase.PREPARING))

        var lastState = ExportState(phase = ExportPhase.PREPARING)
        val result = archiveManager.exportArchive(
            password = password,
            includeTrash = includeTrash,
            includeSettings = includeSettings,
            targetItemIds = targetItemIds
        ) { phase, processed, total, bytes ->
            val state = ExportState(
                phase = phase,
                processedFiles = processed,
                totalFiles = total,
                bytesProcessed = bytes
            )
            lastState = state
        }

        if (result.isSuccess) {
            val file = result.getOrThrow()
            emit(
                ExportState(
                    phase = ExportPhase.COMPLETED,
                    processedFiles = lastState.totalFiles,
                    totalFiles = lastState.totalFiles,
                    bytesProcessed = file.length(),
                    completedFile = file
                )
            )

            // Record in export history (non-destructive record)
            runCatching {
                exportHistoryRepository.recordExport(
                    archiveName = file.name,
                    sizeBytes = file.length(),
                    itemCount = lastState.totalFiles,
                    status = "COMPLETED"
                )
            }
        } else {
            val ex = result.exceptionOrNull()
            val isCancelled = ex is InterruptedException
            val phase = if (isCancelled) ExportPhase.CANCELLED else ExportPhase.FAILED
            emit(
                ExportState(
                    phase = phase,
                    errorMessage = ex?.message ?: "Export operation failed."
                )
            )

            runCatching {
                exportHistoryRepository.recordExport(
                    archiveName = "Export_${System.currentTimeMillis()}",
                    sizeBytes = 0L,
                    itemCount = lastState.totalFiles,
                    status = if (isCancelled) "CANCELLED" else "FAILED"
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun cancelExport() {
        archiveManager.cancelExport()
    }

    override suspend fun saveArchiveToUri(sourceFile: File, targetUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IOException("Could not open destination stream from SAF.")
            Unit
        }
    }
}

