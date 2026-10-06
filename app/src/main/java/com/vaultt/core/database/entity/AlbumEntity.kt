package com.vaultt.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val encryptedName: ByteArray,
    val createdAt: Long,
    val coverVaultObjectId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AlbumEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
