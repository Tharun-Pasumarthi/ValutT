package com.vaultt.feature.backup.model

import org.json.JSONArray
import org.json.JSONObject

data class ArchiveHeader(
    val formatVersion: Int = 1,
    val kdfAlgorithm: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 150_000,
    val cipherAlgorithm: String = "AES-256-GCM",
    val saltHex: String,
    val verifierHex: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("formatVersion", formatVersion)
        obj.put("kdfAlgorithm", kdfAlgorithm)
        obj.put("iterations", iterations)
        obj.put("cipherAlgorithm", cipherAlgorithm)
        obj.put("saltHex", saltHex)
        obj.put("verifierHex", verifierHex)
        obj.put("createdAt", createdAt)
        return obj.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): ArchiveHeader {
            val obj = JSONObject(jsonStr)
            return ArchiveHeader(
                formatVersion = obj.optInt("formatVersion", 1),
                kdfAlgorithm = obj.optString("kdfAlgorithm", "PBKDF2WithHmacSHA256"),
                iterations = obj.optInt("iterations", 150_000),
                cipherAlgorithm = obj.optString("cipherAlgorithm", "AES-256-GCM"),
                saltHex = obj.getString("saltHex"),
                verifierHex = obj.getString("verifierHex"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class ArchiveItemManifest(
    val id: String,
    val originalName: String,
    val type: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val sha256: String,
    val isFavorite: Boolean,
    val folderId: String?,
    val isDeleted: Boolean,
    val hasThumbnail: Boolean,
    val createdAt: Long,
    val modifiedAt: Long
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("originalName", originalName)
        obj.put("type", type)
        obj.put("mimeType", mimeType ?: JSONObject.NULL)
        obj.put("sizeBytes", sizeBytes)
        obj.put("sha256", sha256)
        obj.put("isFavorite", isFavorite)
        obj.put("folderId", folderId ?: JSONObject.NULL)
        obj.put("isDeleted", isDeleted)
        obj.put("hasThumbnail", hasThumbnail)
        obj.put("createdAt", createdAt)
        obj.put("modifiedAt", modifiedAt)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ArchiveItemManifest {
            return ArchiveItemManifest(
                id = obj.getString("id"),
                originalName = obj.getString("originalName"),
                type = obj.getString("type"),
                mimeType = if (obj.isNull("mimeType")) null else obj.getString("mimeType"),
                sizeBytes = obj.getLong("sizeBytes"),
                sha256 = obj.getString("sha256"),
                isFavorite = obj.optBoolean("isFavorite", false),
                folderId = if (obj.isNull("folderId")) null else obj.getString("folderId"),
                isDeleted = obj.optBoolean("isDeleted", false),
                hasThumbnail = obj.optBoolean("hasThumbnail", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                modifiedAt = obj.optLong("modifiedAt", System.currentTimeMillis())
            )
        }
    }
}

data class ArchiveManifest(
    val exportVersion: Int = 1,
    val appVersion: String = "1.0",
    val createdAt: Long = System.currentTimeMillis(),
    val totalItems: Int,
    val totalSizeBytes: Long,
    val includesTrash: Boolean,
    val items: List<ArchiveItemManifest>
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("exportVersion", exportVersion)
        obj.put("appVersion", appVersion)
        obj.put("createdAt", createdAt)
        obj.put("totalItems", totalItems)
        obj.put("totalSizeBytes", totalSizeBytes)
        obj.put("includesTrash", includesTrash)

        val arr = JSONArray()
        items.forEach { arr.put(it.toJsonObject()) }
        obj.put("items", arr)
        return obj.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): ArchiveManifest {
            val obj = JSONObject(jsonStr)
            val arr = obj.getJSONArray("items")
            val itemsList = ArrayList<ArchiveItemManifest>(arr.length())
            for (i in 0 until arr.length()) {
                itemsList.add(ArchiveItemManifest.fromJsonObject(arr.getJSONObject(i)))
            }
            return ArchiveManifest(
                exportVersion = obj.optInt("exportVersion", 1),
                appVersion = obj.optString("appVersion", "1.0"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                totalItems = obj.optInt("totalItems", itemsList.size),
                totalSizeBytes = obj.optLong("totalSizeBytes", 0L),
                includesTrash = obj.optBoolean("includesTrash", false),
                items = itemsList
            )
        }
    }
}

data class ArchiveFolderMetadata(
    val id: String,
    val name: String,
    val createdAt: Long,
    val coverVaultObjectId: String?
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("createdAt", createdAt)
        obj.put("coverVaultObjectId", coverVaultObjectId ?: JSONObject.NULL)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ArchiveFolderMetadata {
            return ArchiveFolderMetadata(
                id = obj.getString("id"),
                name = obj.getString("name"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                coverVaultObjectId = if (obj.isNull("coverVaultObjectId")) null else obj.getString("coverVaultObjectId")
            )
        }
    }
}

data class ArchiveSettingsMetadata(
    val stealthMode: Boolean,
    val flipToLock: Boolean,
    val autoLockTimeout: Int,
    val deleteOriginals: Boolean,
    val zoomSensitivity: Float
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("stealthMode", stealthMode)
        obj.put("flipToLock", flipToLock)
        obj.put("autoLockTimeout", autoLockTimeout)
        obj.put("deleteOriginals", deleteOriginals)
        obj.put("zoomSensitivity", zoomSensitivity.toDouble())
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ArchiveSettingsMetadata {
            return ArchiveSettingsMetadata(
                stealthMode = obj.optBoolean("stealthMode", false),
                flipToLock = obj.optBoolean("flipToLock", false),
                autoLockTimeout = obj.optInt("autoLockTimeout", 0),
                deleteOriginals = obj.optBoolean("deleteOriginals", false),
                zoomSensitivity = obj.optDouble("zoomSensitivity", 1.0).toFloat()
            )
        }
    }
}

data class ArchiveMetadata(
    val folders: List<ArchiveFolderMetadata>,
    val settings: ArchiveSettingsMetadata?
) {
    fun toJson(): String {
        val obj = JSONObject()
        val folderArr = JSONArray()
        folders.forEach { folderArr.put(it.toJsonObject()) }
        obj.put("folders", folderArr)
        obj.put("settings", settings?.toJsonObject() ?: JSONObject.NULL)
        return obj.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): ArchiveMetadata {
            val obj = JSONObject(jsonStr)
            val folderArr = obj.optJSONArray("folders") ?: JSONArray()
            val folderList = ArrayList<ArchiveFolderMetadata>(folderArr.length())
            for (i in 0 until folderArr.length()) {
                folderList.add(ArchiveFolderMetadata.fromJsonObject(folderArr.getJSONObject(i)))
            }
            val settings = if (obj.isNull("settings")) null else ArchiveSettingsMetadata.fromJsonObject(obj.getJSONObject("settings"))
            return ArchiveMetadata(folders = folderList, settings = settings)
        }
    }
}

enum class ExportPhase {
    IDLE,
    AUTHENTICATING,
    PREVIEW,
    PREPARING,
    ENCRYPTING_FILES,
    CREATING_ARCHIVE,
    VERIFYING_ARCHIVE,
    SAVING_ARCHIVE,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class RestorePhase {
    IDLE,
    SELECTING_ARCHIVE,
    INSPECTING_ARCHIVE,
    INSPECTING_HEADER,
    ENTERING_PASSWORD,
    VERIFYING_PASSWORD,
    PARSING_MANIFEST,
    PREVIEW,
    CONFLICT_PROMPT,
    RESTORING_OBJECTS,
    VERIFYING_INTEGRITY,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ExportPreviewStats(
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val documentCount: Int = 0,
    val otherCount: Int = 0,
    val folderCount: Int = 0,
    val trashCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val estimatedArchiveBytes: Long = 0L
) {
    val totalFilesCount: Int
        get() = photoCount + videoCount + audioCount + documentCount + otherCount
}

data class RestorePreviewStats(
    val formatVersion: Int = 1,
    val createdAt: Long = 0L,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val documentCount: Int = 0,
    val otherCount: Int = 0,
    val folderCount: Int = 0,
    val trashCount: Int = 0,
    val totalFilesCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val hasSettings: Boolean = false
)

