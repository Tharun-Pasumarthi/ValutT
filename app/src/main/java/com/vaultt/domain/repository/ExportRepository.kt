package com.vaultt.domain.repository

import android.net.Uri
import com.vaultt.feature.backup.model.ExportPhase
import com.vaultt.feature.backup.model.ExportPreviewStats
import kotlinx.coroutines.flow.Flow
import java.io.File

data class ExportState(
    val phase: ExportPhase = ExportPhase.IDLE,
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val bytesProcessed: Long = 0L,
    val completedFile: File? = null,
    val errorMessage: String? = null
)

interface ExportRepository {
    suspend fun getPreviewStats(includeTrash: Boolean, targetItemIds: List<String>? = null): ExportPreviewStats
    fun exportVault(
        password: CharArray,
        includeTrash: Boolean,
        includeSettings: Boolean,
        targetItemIds: List<String>? = null
    ): Flow<ExportState>
    fun cancelExport()
    suspend fun saveArchiveToUri(sourceFile: File, targetUri: Uri): Result<Unit>
}

