package com.vaultt.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_objects")
data class VaultObjectEntity(
    @PrimaryKey val id: String,
    val encryptedName: ByteArray,
    val type: String, // PHOTO, VIDEO, etc.
    val size: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val hash: String? = null, // Added for duplicate detection
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultObjectEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
