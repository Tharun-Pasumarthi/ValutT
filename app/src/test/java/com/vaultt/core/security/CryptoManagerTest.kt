package com.vaultt.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CryptoManagerTest {

    private lateinit var cryptoManager: CryptoManager
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        cryptoManager = CryptoManager(context)
        AeadConfig.register()
    }

    @Test
    fun `encryption and decryption round trip works`() {
        val originalData = "Sensitive Information".toByteArray()
        val associatedData = "Context".toByteArray()

        val encryptedData = cryptoManager.encrypt(originalData, associatedData)
        val decryptedData = cryptoManager.decrypt(encryptedData, associatedData)

        assertFalse(originalData.contentEquals(encryptedData))
        assertArrayEquals(originalData, decryptedData)
    }

    @Test(expected = Exception::class)
    fun `decryption fails with wrong associated data`() {
        val originalData = "Sensitive Information".toByteArray()
        val encryptedData = cryptoManager.encrypt(originalData, "Right Context".toByteArray())
        
        cryptoManager.decrypt(encryptedData, "Wrong Context".toByteArray())
    }
}
