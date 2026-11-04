package com.vaultt.core.security

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
    private var externalActivityInProgress = false
    private val lifecycleHandler by lazy { Handler(Looper.getMainLooper()) }
    private val delayedLock = Runnable {
        if (!externalActivityInProgress) {
            lock()
        }
    }

    fun lock() {
        _isLocked.value = true
    }

    fun unlock() {
        lifecycleHandler.removeCallbacks(delayedLock)
        _isLocked.value = false
    }

    fun setTemporaryPause(active: Boolean) {
        externalActivityInProgress = active
        if (active) {
            lifecycleHandler.removeCallbacks(delayedLock)
        }
    }

    fun beginExternalActivity() {
        lifecycleHandler.removeCallbacks(delayedLock)
        externalActivityInProgress = true
    }

    fun endExternalActivity() {
        externalActivityInProgress = false
    }

    fun onAppPaused() {
        if (!_isLocked.value && !externalActivityInProgress) {
            val timeoutSeconds = vaultPrefs.getAutoLockTimeout()
            // Respect the user's chosen auto-lock timeout. If 0 ("immediately"), provide a safe 3s grace period for dialogs.
            val delayMs = if (timeoutSeconds <= 0) 3000L else timeoutSeconds * 1000L
            lifecycleHandler.postDelayed(delayedLock, delayMs)
        }
    }

    fun onAppResumed() {
        lifecycleHandler.removeCallbacks(delayedLock)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SecurityManagerEntryPoint {
    fun securityManager(): SecurityManager
}

@Composable
fun rememberSecurityManager(): SecurityManager {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SecurityManagerEntryPoint::class.java
        ).securityManager()
    }
}
