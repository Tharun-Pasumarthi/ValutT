package com.vaultt.data.repository

import com.vaultt.core.database.dao.ExportHistoryDao
import com.vaultt.core.database.entity.ExportHistoryEntity
import com.vaultt.domain.repository.ExportHistoryRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportHistoryRepositoryImpl @Inject constructor(
    private val exportHistoryDao: ExportHistoryDao
) : ExportHistoryRepository {

    override fun getAllHistory(): Flow<List<ExportHistoryEntity>> {
        return exportHistoryDao.getAllHistory()
    }

    override suspend fun recordExport(
        archiveName: String,
        sizeBytes: Long,
        itemCount: Int,
        status: String,
        destinationUri: String?
    ): String {
        val id = UUID.randomUUID().toString()
        val entity = ExportHistoryEntity(
            id = id,
            createdAt = System.currentTimeMillis(),
            archiveName = archiveName,
            archiveSizeBytes = sizeBytes,
            itemCount = itemCount,
            status = status,
            destinationUri = destinationUri
        )
        exportHistoryDao.insert(entity)
        return id
    }

    override suspend fun deleteRecord(id: String) {
        exportHistoryDao.deleteById(id)
    }

    override suspend fun clearAll() {
        exportHistoryDao.clearAll()
    }
}

