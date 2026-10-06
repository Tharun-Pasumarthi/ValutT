package com.vaultt.feature.audio

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class AudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VaultRepository
) : ViewModel() {

    val audioFiles: StateFlow<List<VaultObject>> = repository.getVaultObjectsByType(VaultObjectType.AUDIO.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    fun importAudio(uris: List<Uri>) {
        viewModelScope.launch {
            _isImporting.value = true
            uris.forEach { uri ->
                val name = getFileName(uri)
                val size = getFileSize(uri)
                repository.importMediaStreaming(uri, name, size, VaultObjectType.AUDIO.name)
            }
            _isImporting.value = false
        }
    }

    fun importAudioFromFile(file: File) {
        viewModelScope.launch {
            _isImporting.value = true
            val bytes = file.readBytes()
            repository.importFromBytes(bytes, file.name, VaultObjectType.AUDIO.name)
            _isImporting.value = false
        }
    }

    fun deleteAudio(id: String) {
        viewModelScope.launch {
            repository.moveToTrash(id)
        }
    }
    
    fun renameAudio(id: String, newName: String) {
        viewModelScope.launch {
            repository.renameObject(id, newName)
        }
    }

    private fun getFileName(uri: Uri): String {
        return uri.lastPathSegment ?: "audio_${System.currentTimeMillis()}.mp3"
    }

    private fun getFileSize(uri: Uri): Long {
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { 
            it.length
        } ?: 0L
    }
}
