package com.vaultt.feature.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.core.crypto.ArchiveCrypto
import com.vaultt.core.security.SecurityManager
import com.vaultt.core.security.VaultPrefs
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.repository.ExportRepository
import com.vaultt.domain.repository.ExportState
import com.vaultt.domain.repository.VaultRepository
import com.vaultt.feature.backup.model.ExportPhase
import com.vaultt.feature.backup.model.ExportPreviewStats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportRepository: ExportRepository,
    private val vaultRepository: VaultRepository,
    private val securityManager: SecurityManager,
    private val vaultPrefs: VaultPrefs
) : ViewModel() {

    private val _previewStats = MutableStateFlow<ExportPreviewStats?>(null)
    val previewStats: StateFlow<ExportPreviewStats?> = _previewStats.asStateFlow()

    private val _exportState = MutableStateFlow(ExportState())
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    val allVaultObjects: StateFlow<List<VaultObject>> = vaultRepository.getVaultObjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashVaultObjects: StateFlow<List<VaultObject>> = vaultRepository.getTrashObjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadPreview(includeTrash: Boolean, targetItemIds: List<String>? = null) {
        viewModelScope.launch {
            val stats = exportRepository.getPreviewStats(includeTrash, targetItemIds)
            _previewStats.value = stats
        }
    }

    fun startExport(
        password: String,
        includeTrash: Boolean,
        includeSettings: Boolean,
        targetItemIds: List<String>? = null
    ) {
        viewModelScope.launch {
            val charArray = password.toCharArray()
            exportRepository.exportVault(
                password = charArray,
                includeTrash = includeTrash,
                includeSettings = includeSettings,
                targetItemIds = targetItemIds
            ).collect { state ->
                _exportState.value = state
            }
        }
    }

    fun cancelExport() {
        exportRepository.cancelExport()
    }

    fun shareArchive(archiveFile: File) {
        if (!archiveFile.exists()) return
        runCatching {
            securityManager.setTemporaryPause(true)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                archiveFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Vault T Encrypted Backup")
                putExtra(Intent.EXTRA_TEXT, "Secure password-protected Vault T archive: ${archiveFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Share Encrypted Vault").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun saveArchiveToSaf(sourceFile: File, targetUri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = exportRepository.saveArchiveToUri(sourceFile, targetUri)
            onResult(result.isSuccess)
        }
    }

    fun resetState() {
        _exportState.value = ExportState()
    }

    fun evaluatePasswordStrength(password: String): Triple<Int, String, String> {
        val chars = password.toCharArray()
        val res = ArchiveCrypto.evaluatePasswordStrength(chars)
        ArchiveCrypto.wipe(chars)
        return res
    }

    suspend fun getThumbnail(id: String): Result<ByteArray?> {
        return vaultRepository.getDecryptedThumbnail(id)
    }
}

