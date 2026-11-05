package com.vaultt.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "export_history")
data class ExportHistoryEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val archiveName: String,
    val archiveSizeBytes: Long,
    val itemCount: Int,
    val status: String, // "COMPLETED", "FAILED", "CANCELLED"
    val destinationUri: String? = null
)

