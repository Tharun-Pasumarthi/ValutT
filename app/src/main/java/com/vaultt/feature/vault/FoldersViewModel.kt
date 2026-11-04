package com.vaultt.feature.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.core.security.SecurityManager
import com.vaultt.core.security.VaultPrefs
import com.vaultt.core.util.ZipExportHelper
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.repository.VaultFolder
import com.vaultt.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoldersViewModel @Inject constructor(
    val repository: VaultRepository,
    val vaultPrefs: VaultPrefs
) : ViewModel() {

    fun getZoomSensitivity(): Float = vaultPrefs.getZoomSensitivity()

    val folders: StateFlow<List<VaultFolder>> = repository.getFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun objects(folderId: String): Flow<List<VaultObject>> = repository.getFolderObjects(folderId)

    fun unfiled(): Flow<List<VaultObject>> = repository.getUnfiledObjects()

    fun allObjects(): Flow<List<VaultObject>> = repository.getVaultObjects()

    fun create(name: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        repository.createFolder(name).onSuccess(onCreated)
    }

    fun renameFolder(id: String, name: String) = viewModelScope.launch {
        repository.renameFolder(id, name)
    }

    fun setFolderCover(folderId: String, objectId: String?) = viewModelScope.launch {
        repository.setFolderCover(folderId, objectId)
    }

    fun delete(id: String) = viewModelScope.launch {
        repository.deleteFolder(id)
    }

    fun move(objectId: String, folderId: String?) = viewModelScope.launch {
        repository.moveToFolder(objectId, folderId)
    }

    fun moveAll(objectIds: List<String>, folderId: String?) = viewModelScope.launch {
        repository.moveToFolder(objectIds, folderId)
    }

    fun rename(objectId: String, name: String) = viewModelScope.launch {
        repository.renameObject(objectId, name)
    }

    fun deleteObject(objectId: String) = viewModelScope.launch {
        repository.moveToTrash(objectId)
    }

    fun deleteObjects(objectIds: List<String>) = viewModelScope.launch {
        repository.moveToTrash(objectIds)
    }

    suspend fun getData(objectId: String): Result<ByteArray> = repository.getDecryptedData(objectId)

    suspend fun getThumbnail(objectId: String): Result<ByteArray?> = repository.getDecryptedThumbnail(objectId)

    fun exportFolderAsZip(
        context: Context,
        folderName: String,
        items: List<VaultObject>,
        securityManager: SecurityManager,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val res = ZipExportHelper.exportFolderAsZip(context, repository, folderName, items, securityManager)
            onComplete(res.isSuccess)
        }
    }
}
