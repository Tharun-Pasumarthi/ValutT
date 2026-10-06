package com.vaultt.core.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val vaultPrefs: VaultPrefs
) {

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lastActiveTime: Long = SystemClock.elapsedRealtime()
    private var backgroundTimestamp: Long = 0

    fun lock() {
        _isLocked.value = true
        backgroundTimestamp = 0
    }

    fun unlock() {
        _isLocked.value = false
        backgroundTimestamp = 0
        updateActivity()
    }

    fun updateActivity() {
        lastActiveTime = SystemClock.elapsedRealtime()
    }

    /**
     * Called when the app goes into the background.
     */
    fun onAppBackgrounded() {
        if (_isLocked.value) return
        backgroundTimestamp = SystemClock.elapsedRealtime()
        
        // If timeout is "Immediately" (0 seconds)
        if (vaultPrefs.getAutoLockTimeout() == 0) {
            lock()
        }
    }

    /**
     * Called when the app returns to the foreground.
     */
    fun onAppForegrounded() {
        if (_isLocked.value) return
        
        val timeoutSeconds = vaultPrefs.getAutoLockTimeout()
        if (timeoutSeconds < 0) { // Never lock
            updateActivity()
            return
        }

        val currentTime = SystemClock.elapsedRealtime()
        
        // 1. Check if background duration exceeded timeout
        if (backgroundTimestamp > 0) {
            val elapsedSeconds = (currentTime - backgroundTimestamp) / 1000
            if (elapsedSeconds >= timeoutSeconds) {
                lock()
                backgroundTimestamp = 0
                return
            }
        }
        backgroundTimestamp = 0 // Reset since we are foregrounded
        
        // 2. Also check if cumulative inactivity exceeded timeout
        checkInactivityLock()
    }

    /**
     * Periodically called to check for idle timeout while app is open.
     */
    fun checkInactivityLock() {
        if (_isLocked.value) return
        
        val timeoutSeconds = vaultPrefs.getAutoLockTimeout()
        // If timeout is Immediately (0) or Never (-1), ignore timer-based lock
        if (timeoutSeconds <= 0) return 

        val currentTime = SystemClock.elapsedRealtime()
        val elapsedInactivitySeconds = (currentTime - lastActiveTime) / 1000
        
        if (elapsedInactivitySeconds >= timeoutSeconds) {
            lock()
        }
    }

    fun isImmediateLockEnabled(): Boolean = vaultPrefs.getAutoLockTimeout() == 0
}
