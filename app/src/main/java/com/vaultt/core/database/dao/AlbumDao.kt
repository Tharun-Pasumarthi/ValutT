package com.vaultt.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vaultt.core.database.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumEntity)

    @Delete
    suspend fun delete(album: AlbumEntity)

    @Query("UPDATE albums SET coverVaultObjectId = :objectId WHERE id = :albumId")
    suspend fun updateCover(albumId: String, objectId: String)
}
