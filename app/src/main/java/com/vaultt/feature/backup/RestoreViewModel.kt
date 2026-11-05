package com.vaultt.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.domain.repository.RestoreRepository
import com.vaultt.domain.repository.RestoreState
import com.vaultt.domain.repository.VaultRepository
import com.vaultt.feature.backup.model.ArchiveHeader
import com.vaultt.feature.backup.model.ArchiveManifest
import com.vaultt.feature.backup.model.ArchiveMetadata
import com.vaultt.feature.backup.model.RestorePhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val restoreRepository: RestoreRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _header = MutableStateFlow<ArchiveHeader?>(null)
    val header: StateFlow<ArchiveHeader?> = _header.asStateFlow()

    private val _preview = MutableStateFlow<Pair<ArchiveManifest, ArchiveMetadata>?>(null)
    val preview: StateFlow<Pair<ArchiveManifest, ArchiveMetadata>?> = _preview.asStateFlow()

    private val _restoreState = MutableStateFlow(RestoreState())
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    val currentVaultItemCount: StateFlow<Int> = vaultRepository.getVaultObjects()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun inspectArchive(uri: Uri, onHeaderLoaded: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = restoreRepository.inspectHeader(uri)
            if (result.isSuccess) {
                _header.value = result.getOrNull()
                onHeaderLoaded(true, null)
            } else {
                _header.value = null
                onHeaderLoaded(false, result.exceptionOrNull()?.message ?: "Invalid archive header.")
            }
        }
    }

    fun verifyPasswordAndLoadPreview(
        uri: Uri,
        password: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val chars = password.toCharArray()
            val result = restoreRepository.getRestorePreview(uri, chars)
            if (result.isSuccess) {
                _preview.value = result.getOrNull()
                onComplete(true, null)
            } else {
                _preview.value = null
                onComplete(false, result.exceptionOrNull()?.message ?: "Incorrect password or corrupted archive.")
            }
        }
    }

    fun startRestore(uri: Uri, password: String, replaceExisting: Boolean) {
        viewModelScope.launch {
            val chars = password.toCharArray()
            restoreRepository.restoreVault(uri, chars, replaceExisting).collect { state ->
                _restoreState.value = state
            }
        }
    }

    fun cancelRestore() {
        restoreRepository.cancelRestore()
    }

    fun resetState() {
        _header.value = null
        _preview.value = null
        _restoreState.value = RestoreState()
    }
}

