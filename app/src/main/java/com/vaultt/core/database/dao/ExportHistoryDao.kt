package com.vaultt.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vaultt.core.database.entity.ExportHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportHistoryDao {

    @Query("SELECT * FROM export_history ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<ExportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExportHistoryEntity)

    @Query("DELETE FROM export_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM export_history")
    suspend fun clearAll()
}

