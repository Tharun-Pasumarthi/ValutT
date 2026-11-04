package com.vaultt.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    val repository: VaultRepository,
    val vaultPrefs: com.vaultt.core.security.VaultPrefs
) : ViewModel() {

    fun getZoomSensitivity(): Float = vaultPrefs.getZoomSensitivity()

    val trashItems: StateFlow<List<VaultObject>> = repository.getTrashObjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getThumbnail(id: String): ByteArray? {
        return repository.getDecryptedThumbnail(id).getOrNull()
    }

    suspend fun getFullImage(id: String): ByteArray? {
        return repository.getDecryptedData(id).getOrNull()
    }

    fun restoreObject(id: String) {
        viewModelScope.launch {
            repository.restoreFromTrash(id)
        }
    }

    fun restoreObjects(ids: List<String>) {
        viewModelScope.launch {
            repository.restoreFromTrash(ids)
        }
    }

    fun permanentlyDelete(id: String) {
        viewModelScope.launch {
            repository.deleteObject(id)
        }
    }

    fun permanentlyDeleteObjects(ids: List<String>) {
        viewModelScope.launch {
            repository.deleteObjects(ids)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }
}
