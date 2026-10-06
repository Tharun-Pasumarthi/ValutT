package com.vaultt.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.core.security.SecurityManager
import com.vaultt.core.security.VaultPrefs
import com.vaultt.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val vaultPrefs: VaultPrefs,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _storageStats = MutableStateFlow<Map<String, Long>>(emptyMap())
    val storageStats: StateFlow<Map<String, Long>> = _storageStats

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _storageStats.value = vaultRepository.getStorageStats()
        }
    }

    fun isBiometricEnabled() = vaultPrefs.isBiometricEnabled()
    fun setBiometricEnabled(enabled: Boolean) = vaultPrefs.setBiometricEnabled(enabled)
    fun getAutoLockTimeout() = vaultPrefs.getAutoLockTimeout()
    fun setAutoLockTimeout(seconds: Int) = vaultPrefs.setAutoLockTimeout(seconds)
    fun changePin(old: String, new: String): Boolean {
        return if (vaultPrefs.getPin() == old) {
            vaultPrefs.savePin(new)
            true
        } else {
            false
        }
    }
    fun destroyVault(onComplete: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.destroyVault()
            vaultPrefs.savePin("")
            securityManager.lock()
            onComplete()
        }
    }
}
