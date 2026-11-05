package com.vaultt.domain.repository

import com.vaultt.core.database.entity.ExportHistoryEntity
import kotlinx.coroutines.flow.Flow

interface ExportHistoryRepository {
    fun getAllHistory(): Flow<List<ExportHistoryEntity>>
    suspend fun recordExport(
        archiveName: String,
        sizeBytes: Long,
        itemCount: Int,
        status: String,
        destinationUri: String? = null
    ): String
    suspend fun deleteRecord(id: String)
    suspend fun clearAll()
}

