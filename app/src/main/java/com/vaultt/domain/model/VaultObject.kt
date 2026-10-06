package com.vaultt.domain.model

data class VaultObject(
    val id: String,
    val name: String,
    val type: VaultObjectType,
    val size: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val isFavorite: Boolean = false
)

enum class VaultObjectType {
    PHOTO, VIDEO, AUDIO, DOCUMENT, OTHER
}
