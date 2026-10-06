package com.vaultt.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vaultt.core.database.dao.AlbumDao
import com.vaultt.core.database.dao.VaultObjectDao
import com.vaultt.core.database.entity.AlbumEntity
import com.vaultt.core.database.entity.VaultObjectEntity

@Database(
    entities = [VaultObjectEntity::class, AlbumEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultObjectDao(): VaultObjectDao
    abstract fun albumDao(): AlbumDao
}
