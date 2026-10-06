package com.vaultt.core.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyUri = "android-keystore://vault_t_master_key"
    private val keysetName = "vault_t_keyset"
    private val streamingKeysetName = "vault_t_streaming_keyset"
    private val prefFileName = "vault_t_prefs"

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, keysetName, prefFileName)
            .withKeyTemplate(KeyTemplates.get("XCHACHA20_POLY1305"))
            .withMasterKeyUri(masterKeyUri)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    private val streamingAead: StreamingAead by lazy {
        StreamingAeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, streamingKeysetName, prefFileName)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
            .withMasterKeyUri(masterKeyUri)
            .build()
            .keysetHandle
            .getPrimitive(StreamingAead::class.java)
    }

    fun encrypt(data: ByteArray, associatedData: ByteArray? = null): ByteArray {
        return aead.encrypt(data, associatedData)
    }

    fun decrypt(encryptedData: ByteArray, associatedData: ByteArray? = null): ByteArray {
        return aead.decrypt(encryptedData, associatedData)
    }

    fun newEncryptingChannel(
        ciphertextChannel: WritableByteChannel,
        associatedData: ByteArray
    ): WritableByteChannel {
        return streamingAead.newEncryptingChannel(ciphertextChannel, associatedData)
    }

    fun newDecryptingChannel(
        ciphertextChannel: ReadableByteChannel,
        associatedData: ByteArray
    ): ReadableByteChannel {
        return streamingAead.newDecryptingChannel(ciphertextChannel, associatedData)
    }

    fun newEncryptingStream(
        ciphertextStream: OutputStream,
        associatedData: ByteArray
    ): OutputStream {
        return streamingAead.newEncryptingStream(ciphertextStream, associatedData)
    }

    fun newDecryptingStream(
        ciphertextStream: InputStream,
        associatedData: ByteArray
    ): InputStream {
        return streamingAead.newDecryptingStream(ciphertextStream, associatedData)
    }
}
