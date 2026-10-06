package com.vaultt.feature.videos

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VaultRepository
) : ViewModel() {

    val videos: StateFlow<List<VaultObject>> = repository.getVaultObjectsByType(VaultObjectType.VIDEO.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _duplicateAlert = MutableStateFlow<List<Pair<Uri, String>>>(emptyList())
    val duplicateAlert = _duplicateAlert.asStateFlow()

    private val _deleteIntentSender = MutableSharedFlow<IntentSender>()
    val deleteIntentSender = _deleteIntentSender.asSharedFlow()

    fun importVideos(uris: List<Uri>, force: Boolean = false) {
        viewModelScope.launch {
            _isImporting.value = true
            val duplicates = mutableListOf<Pair<Uri, String>>()
            val successfullyImportedUris = mutableListOf<Uri>()

            uris.forEach { uri ->
                val name = getFileName(uri)
                val size = getFileSize(uri)
                
                val result = repository.importMediaStreaming(uri, name, size, VaultObjectType.VIDEO.name, force)
                result.onSuccess { importResult ->
                    if (importResult.isDuplicate && !force) {
                        duplicates.add(uri to (importResult.name ?: name))
                    } else {
                        successfullyImportedUris.add(uri)
                    }
                }
            }

            if (duplicates.isNotEmpty()) {
                _duplicateAlert.value = duplicates
            }

            _isImporting.value = false
        }
    }

    fun continueImportingDuplicates() {
        val duplicates = _duplicateAlert.value.map { it.first }
        _duplicateAlert.value = emptyList()
        if (duplicates.isNotEmpty()) {
            importVideos(duplicates, force = true)
        }
    }

    fun clearDuplicateAlert() {
        _duplicateAlert.value = emptyList()
    }

    fun deleteVideo(id: String) {
        viewModelScope.launch {
            repository.moveToTrash(id)
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "video_${System.currentTimeMillis()}.mp4"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex != -1) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }
}
