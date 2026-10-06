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
        sharedPrefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun getPin(): String? {
        return sharedPrefs.getString(KEY_PIN, null)
    }

    fun setRecoveryData(question: String, answer: String) {
        sharedPrefs.edit()
            .putString(KEY_RECOVERY_QUESTION, question)
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
        return getPin() != null && getRecoveryQuestion() != null
    }

    companion object {
        private const val KEY_PIN = "vault_pin"
        private const val KEY_RECOVERY_QUESTION = "recovery_question"
        private const val KEY_RECOVERY_ANSWER = "recovery_answer"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
    }
}
