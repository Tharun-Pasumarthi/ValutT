package com.vaultt.feature.photos

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
class PhotosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VaultRepository
) : ViewModel() {

    val photos: StateFlow<List<VaultObject>> = repository.getVaultObjectsByType(VaultObjectType.PHOTO.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _duplicateAlert = MutableStateFlow<List<Pair<Uri, String>>>(emptyList())
    val duplicateAlert = _duplicateAlert.asStateFlow()

    private val _deleteIntentSender = MutableSharedFlow<IntentSender>()
    val deleteIntentSender = _deleteIntentSender.asSharedFlow()

    fun importPhotos(uris: List<Uri>, force: Boolean = false) {
        viewModelScope.launch {
            _isImporting.value = true
            val duplicates = mutableListOf<Pair<Uri, String>>()
            val successfullyImportedUris = mutableListOf<Uri>()
            
            uris.forEach { uri ->
                val name = getFileName(uri)
                val size = getFileSize(uri)
                
                val result = repository.importPhoto(uri, name, size, force)
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
            
            if (successfullyImportedUris.isNotEmpty()) {
                requestSystemDeletion(successfullyImportedUris)
            }
            
            _isImporting.value = false
        }
    }

    fun continueImportingDuplicates() {
        val duplicates = _duplicateAlert.value.map { it.first }
        clearDuplicateAlert()
        if (duplicates.isNotEmpty()) {
            importPhotos(duplicates, force = true)
        }
    }

    private fun requestSystemDeletion(uris: List<Uri>) {
        val intentSender = repository.getDeleteRequestIntentSender(uris)
        if (intentSender != null) {
            viewModelScope.launch {
                _deleteIntentSender.emit(intentSender)
            }
        }
    }

    fun clearDuplicateAlert() {
        _duplicateAlert.value = emptyList()
    }

    fun importPhotosFromBytes(bytes: ByteArray, name: String) {
        viewModelScope.launch {
            _isImporting.value = true
            repository.importFromBytes(bytes, name, VaultObjectType.PHOTO.name)
            _isImporting.value = false
        }
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch {
            repository.moveToTrash(id)
        }
    }

    suspend fun getThumbnail(id: String): ByteArray? {
        return repository.getDecryptedThumbnail(id).getOrNull()
    }
    
    suspend fun getFullImage(id: String): ByteArray? {
        return repository.getDecryptedData(id).getOrNull()
    }

    private fun getFileName(uri: Uri): String {
        var name = "image_${System.currentTimeMillis()}.jpg"
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
