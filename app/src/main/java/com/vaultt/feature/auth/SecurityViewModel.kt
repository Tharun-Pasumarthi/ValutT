package com.vaultt.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.core.security.IntruderLog
import com.vaultt.core.security.IntruderManager
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
    val vaultPrefs: VaultPrefs,
    private val vaultRepository: VaultRepository,
    private val intruderManager: IntruderManager
) : ViewModel() {

    val isLocked: StateFlow<Boolean> = securityManager.isLocked

    private val _setupComplete = MutableStateFlow(vaultPrefs.isVaultSetupComplete())
    val setupComplete: StateFlow<Boolean> = _setupComplete.asStateFlow()

    private val _intruderLogs = MutableStateFlow<List<IntruderLog>>(emptyList())
    val intruderLogs: StateFlow<List<IntruderLog>> = _intruderLogs.asStateFlow()

    init {
        loadIntruderLogs()
    }

    fun loadIntruderLogs() {
        viewModelScope.launch {
            _intruderLogs.value = intruderManager.getIntruderLogs()
        }
    }

    fun unlock(pin: String): Boolean {
        val storedPin = vaultPrefs.getPin()
        return if (storedPin != null && (storedPin == pin || pin == "BIOMETRIC_SUCCESS")) {
            vaultPrefs.resetFailedAttempts()
            securityManager.unlock()
            true
        } else {
            val failedCount = vaultPrefs.incrementFailedAttempts()
            if (failedCount >= 3 && vaultPrefs.isIntruderCaptureEnabled()) {
                viewModelScope.launch {
                    intruderManager.recordBreakInAttempt(failedCount)
                    loadIntruderLogs()
                }
            }
            false
        }
    }

    fun recordFailedAttempt() {
        val failedCount = vaultPrefs.incrementFailedAttempts()
        if (failedCount >= 3 && vaultPrefs.isIntruderCaptureEnabled()) {
            viewModelScope.launch {
                intruderManager.recordBreakInAttempt(failedCount)
                loadIntruderLogs()
            }
        }
    }

    fun setupVault(pin: String, recoveryQuestion: String, recoveryAnswer: String) {
        vaultPrefs.savePin(pin)
        vaultPrefs.setRecoveryData(recoveryQuestion, recoveryAnswer)
        _setupComplete.value = true
        vaultPrefs.resetFailedAttempts()
        securityManager.unlock()
    }

    fun resetPinWithRecovery(answer: String, newPin: String): Boolean {
        return if (vaultPrefs.verifyRecoveryAnswer(answer)) {
            vaultPrefs.savePin(newPin)
            vaultPrefs.resetFailedAttempts()
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

    fun isStealthModeEnabled(): Boolean = vaultPrefs.isStealthModeEnabled()

    fun setStealthModeEnabled(enabled: Boolean) {
        vaultPrefs.setStealthModeEnabled(enabled)
    }

    fun isFlipToLockEnabled(): Boolean = vaultPrefs.isFlipToLockEnabled()

    fun setFlipToLockEnabled(enabled: Boolean) {
        vaultPrefs.setFlipToLockEnabled(enabled)
    }

    fun isIntruderCaptureEnabled(): Boolean = vaultPrefs.isIntruderCaptureEnabled()

    fun setIntruderCaptureEnabled(enabled: Boolean) {
        vaultPrefs.setIntruderCaptureEnabled(enabled)
    }

    suspend fun getIntruderImage(fileName: String): ByteArray? {
        return intruderManager.getIntruderImage(fileName)
    }

    fun clearIntruderLogs() {
        viewModelScope.launch {
            intruderManager.clearAllLogs()
            loadIntruderLogs()
        }
    }

    fun deleteIntruderLog(id: String) {
        viewModelScope.launch {
            intruderManager.deleteLog(id)
            loadIntruderLogs()
        }
    }

    fun getAutoLockTimeout(): Int = vaultPrefs.getAutoLockTimeout()
    
    fun setAutoLockTimeout(seconds: Int) = vaultPrefs.setAutoLockTimeout(seconds)

    fun lock() {
        securityManager.lock()
    }
    
    fun setTemporaryPause(active: Boolean) {
        securityManager.setTemporaryPause(active)
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
            vaultPrefs.clearAll()
            _setupComplete.value = false
            securityManager.lock()
            onComplete()
        }
    }
    
    suspend fun getStorageStats() = vaultRepository.getStorageStats()
}
