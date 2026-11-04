package com.vaultt.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "vault_t_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePin(pin: String) {
        sharedPrefs.edit().putString(KEY_PIN, pin.trim()).apply()
    }

    fun getPin(): String? {
        return sharedPrefs.getString(KEY_PIN, null)
    }

    fun setRecoveryData(question: String, answer: String) {
        sharedPrefs.edit()
            .putString(KEY_RECOVERY_QUESTION, question.trim())
            .putString(KEY_RECOVERY_ANSWER, answer.lowercase().trim())
            .apply()
    }

    fun getRecoveryQuestion(): String? = sharedPrefs.getString(KEY_RECOVERY_QUESTION, null)
    
    fun verifyRecoveryAnswer(answer: String): Boolean {
        val storedAnswer = sharedPrefs.getString(KEY_RECOVERY_ANSWER, null)
        return storedAnswer != null && storedAnswer == answer.lowercase().trim()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setAutoLockTimeout(seconds: Int) {
        sharedPrefs.edit().putInt(KEY_AUTO_LOCK_TIMEOUT, seconds).apply()
    }

    fun getAutoLockTimeout(): Int {
        return sharedPrefs.getInt(KEY_AUTO_LOCK_TIMEOUT, 30)
    }

    fun isVaultSetupComplete(): Boolean {
        val pin = getPin()
        val question = getRecoveryQuestion()
        return !pin.isNullOrBlank() && !question.isNullOrBlank()
    }

    fun setZoomSensitivity(sensitivity: Float) {
        sharedPrefs.edit().putFloat(KEY_ZOOM_SENSITIVITY, sensitivity).apply()
    }

    fun getZoomSensitivity(): Float {
        return sharedPrefs.getFloat(KEY_ZOOM_SENSITIVITY, 1.0f)
    }

    fun setStealthModeEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_STEALTH_MODE, enabled).apply()
    }

    fun isStealthModeEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_STEALTH_MODE, false)
    }

    fun setFlipToLockEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_FLIP_TO_LOCK, enabled).apply()
    }

    fun isFlipToLockEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_FLIP_TO_LOCK, false)
    }

    fun setIntruderCaptureEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_INTRUDER_CAPTURE, enabled).apply()
    }

    fun isIntruderCaptureEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_INTRUDER_CAPTURE, true)
    }

    fun getFailedAttempts(): Int {
        return sharedPrefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    fun incrementFailedAttempts(): Int {
        val count = getFailedAttempts() + 1
        sharedPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, count).apply()
        return count
    }

    fun resetFailedAttempts() {
        sharedPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply()
    }

    fun setDeleteOriginalsEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_DELETE_ORIGINALS, enabled).apply()
    }

    fun isDeleteOriginalsEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_DELETE_ORIGINALS, false)
    }

    fun clearAll() {
        sharedPrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PIN = "vault_pin"
        private const val KEY_RECOVERY_QUESTION = "recovery_question"
        private const val KEY_RECOVERY_ANSWER = "recovery_answer"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val KEY_ZOOM_SENSITIVITY = "zoom_sensitivity"
        private const val KEY_STEALTH_MODE = "stealth_calculator_mode"
        private const val KEY_FLIP_TO_LOCK = "flip_to_lock"
        private const val KEY_INTRUDER_CAPTURE = "intruder_capture"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts_count"
        private const val KEY_DELETE_ORIGINALS = "delete_originals_on_import"
    }
}
