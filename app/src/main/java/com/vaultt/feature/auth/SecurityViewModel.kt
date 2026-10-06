package com.vaultt.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.core.security.SecurityManager
import com.vaultt.core.security.VaultPrefs
import com.vaultt.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val vaultPrefs: VaultPrefs,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    val isLocked: StateFlow<Boolean> = securityManager.isLocked

    private val _setupComplete = MutableStateFlow(vaultPrefs.isVaultSetupComplete())
    val setupComplete: StateFlow<Boolean> = _setupComplete.asStateFlow()

    fun unlock(pin: String): Boolean {
        return if (vaultPrefs.getPin() == pin || pin == "BIOMETRIC_SUCCESS") {
            securityManager.unlock()
            true
        } else {
            false
        }
    }

    fun setupVault(pin: String, recoveryQuestion: String, recoveryAnswer: String) {
        vaultPrefs.savePin(pin)
        vaultPrefs.setRecoveryData(recoveryQuestion, recoveryAnswer)
        _setupComplete.value = true
        securityManager.unlock()
    }

    fun resetPinWithRecovery(answer: String, newPin: String): Boolean {
        return if (vaultPrefs.verifyRecoveryAnswer(answer)) {
            vaultPrefs.savePin(newPin)
            securityManager.unlock()
            true
        } else {
            false
        }
    }

    fun getRecoveryQuestion(): String? = vaultPrefs.getRecoveryQuestion()

    fun isBiometricEnabled(): Boolean = vaultPrefs.isBiometricEnabled()
    
    fun setBiometricEnabled(enabled: Boolean) {
        vaultPrefs.setBiometricEnabled(enabled)
    }

    fun getAutoLockTimeout(): Int = vaultPrefs.getAutoLockTimeout()
    
    fun setAutoLockTimeout(seconds: Int) = vaultPrefs.setAutoLockTimeout(seconds)

    fun lock() {
        securityManager.lock()
    }
    
    fun changePin(oldPin: String, newPin: String): Boolean {
        return if (vaultPrefs.getPin() == oldPin) {
            vaultPrefs.savePin(newPin)
            true
        } else {
            false
        }
    }

    fun destroyVault(onComplete: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.destroyVault()
            // Clear prefs
            vaultPrefs.savePin("") // Or a more thorough clear
            _setupComplete.value = false
            securityManager.lock()
            onComplete()
        }
    }
    
    suspend fun getStorageStats() = vaultRepository.getStorageStats()
}
