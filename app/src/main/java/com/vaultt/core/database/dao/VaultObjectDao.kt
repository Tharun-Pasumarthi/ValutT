package com.vaultt.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vaultt.core.database.entity.VaultObjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultObjectDao {
    @Query("SELECT * FROM vault_objects WHERE isDeleted = 0")
    fun getAllObjects(): Flow<List<VaultObjectEntity>>

    @Query("SELECT * FROM vault_objects WHERE type = :type AND folderId IS NULL AND isDeleted = 0")
    fun getObjectsByType(type: String): Flow<List<VaultObjectEntity>>

    @Query("SELECT COUNT(*) FROM vault_objects WHERE type = :type AND folderId IS NULL AND isDeleted = 0")
    fun getCountByType(type: String): Flow<Int>

    @Query("SELECT * FROM vault_objects WHERE isDeleted = 1")
    fun getTrashObjects(): Flow<List<VaultObjectEntity>>

    @Query("SELECT COUNT(*) FROM vault_objects WHERE isDeleted = 1")
    fun getTrashCount(): Flow<Int>
    @Query("SELECT * FROM vault_objects WHERE folderId = :folderId AND isDeleted = 0")
    fun getObjectsByFolder(folderId: String): Flow<List<VaultObjectEntity>>
    @Query("SELECT * FROM vault_objects WHERE folderId IS NULL AND isDeleted = 0")
    fun getUnfiledObjects(): Flow<List<VaultObjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vaultObject: VaultObjectEntity)

    @Delete
    suspend fun delete(vaultObject: VaultObjectEntity)

    @Query("DELETE FROM vault_objects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE vault_objects SET encryptedName = :newName WHERE id = :id")
    suspend fun updateName(id: String, newName: ByteArray)

    @Query("UPDATE vault_objects SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: String)

    @Query("UPDATE vault_objects SET isDeleted = 1 WHERE id = :id")
    suspend fun moveToTrash(id: String)

    @Query("UPDATE vault_objects SET isDeleted = 0 WHERE id = :id")
    suspend fun restoreFromTrash(id: String)
    @Query("UPDATE vault_objects SET isDeleted = 0 WHERE id IN (:ids)")
    suspend fun restoreFromTrash(ids: List<String>)
    @Query("UPDATE vault_objects SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: String, folderId: String?)
    @Query("UPDATE vault_objects SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun setFolder(ids: List<String>, folderId: String?)
    @Query("UPDATE vault_objects SET isDeleted = 1 WHERE id IN (:ids)")
    suspend fun moveToTrash(ids: List<String>)
    
    @Query("DELETE FROM vault_objects WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM vault_objects")
    suspend fun deleteAll()
}
