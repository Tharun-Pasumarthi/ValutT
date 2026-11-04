package com.vaultt.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultt.core.security.IntruderLog
import com.vaultt.core.security.IntruderManager
import com.vaultt.core.security.SecurityManager
import com.vaultt.core.security.VaultPrefs
import com.vaultt.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager,
    private val vaultPrefs: VaultPrefs,
    private val vaultRepository: VaultRepository,
    private val intruderManager: IntruderManager
) : ViewModel() {

    private val _storageStats = MutableStateFlow<Map<String, Long>>(emptyMap())
    val storageStats: StateFlow<Map<String, Long>> = _storageStats

    private val _intruderLogs = MutableStateFlow<List<IntruderLog>>(emptyList())
    val intruderLogs: StateFlow<List<IntruderLog>> = _intruderLogs.asStateFlow()

    init {
        loadStats()
        loadIntruderLogs()
    }

    fun loadStats() {
        viewModelScope.launch {
            _storageStats.value = vaultRepository.getStorageStats()
        }
    }

    fun loadIntruderLogs() {
        viewModelScope.launch {
            _intruderLogs.value = intruderManager.getIntruderLogs()
        }
    }

    fun isBiometricEnabled() = vaultPrefs.isBiometricEnabled()
    fun setBiometricEnabled(enabled: Boolean) = vaultPrefs.setBiometricEnabled(enabled)

    fun isDeleteOriginalsEnabled() = vaultPrefs.isDeleteOriginalsEnabled()
    fun setDeleteOriginalsEnabled(enabled: Boolean) = vaultPrefs.setDeleteOriginalsEnabled(enabled)

    fun isStealthModeEnabled() = vaultPrefs.isStealthModeEnabled()
    fun setStealthModeEnabled(enabled: Boolean) {
        vaultPrefs.setStealthModeEnabled(enabled)
        runCatching {
            val pm = context.packageManager
            val defaultAlias = ComponentName(context, "com.vaultt.DefaultAlias")
            val calcAlias = ComponentName(context, "com.vaultt.CalculatorAlias")
            val mainActivity = ComponentName(context, "com.vaultt.MainActivity")

            // CRITICAL: Ensure MainActivity and DefaultAlias are NEVER disabled.
            // Disabling the active launcher component triggers OS-level task destruction
            // and breaks home screen shortcuts, causing immediate app closure.
            pm.setComponentEnabledSetting(
                mainActivity,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                defaultAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Toggle CalculatorAlias icon safely
            val calcState = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                calcAlias,
                calcState,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    fun isFlipToLockEnabled() = vaultPrefs.isFlipToLockEnabled()
    fun setFlipToLockEnabled(enabled: Boolean) = vaultPrefs.setFlipToLockEnabled(enabled)

    fun isIntruderCaptureEnabled() = vaultPrefs.isIntruderCaptureEnabled()
    fun setIntruderCaptureEnabled(enabled: Boolean) = vaultPrefs.setIntruderCaptureEnabled(enabled)

    suspend fun getIntruderImage(fileName: String): ByteArray? = intruderManager.getIntruderImage(fileName)

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

    fun getAutoLockTimeout() = vaultPrefs.getAutoLockTimeout()
    fun setAutoLockTimeout(seconds: Int) = vaultPrefs.setAutoLockTimeout(seconds)

    fun getZoomSensitivity(): Float = vaultPrefs.getZoomSensitivity()
    fun setZoomSensitivity(sensitivity: Float) = vaultPrefs.setZoomSensitivity(sensitivity)
    
    fun changePin(old: String, new: String): Boolean {
        val storedPin = vaultPrefs.getPin()?.trim()
        val oldTrimmed = old.trim()
        val newTrimmed = new.trim()
        
        return if (storedPin == oldTrimmed && newTrimmed.length in 4..6 && newTrimmed.all { it.isDigit() }) {
            vaultPrefs.savePin(newTrimmed)
            true
        } else {
            false
        }
    }
    
    fun destroyVault(onComplete: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.destroyVault()
            vaultPrefs.clearAll()
            securityManager.lock()
            onComplete()
        }
    }
}
