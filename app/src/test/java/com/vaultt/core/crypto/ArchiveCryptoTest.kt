package com.vaultt.core.crypto

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Arrays

class ArchiveCryptoTest {

    @Test
    fun `PBKDF2 key derivation produces 256-bit AES key`() {
        val password = "MySecurePassword123!".toCharArray()
        val salt = ArchiveCrypto.generateSalt()
        assertEquals(32, salt.size)

        val key = ArchiveCrypto.deriveKey(password, salt, 1000)
        assertEquals("AES", key.algorithm)
        assertEquals(32, key.encoded.size) // 256 bits = 32 bytes
    }

    @Test
    fun `AES-256-GCM bytes encryption and decryption round trip`() {
        val password = "TestPassword#456".toCharArray()
        val salt = ArchiveCrypto.generateSalt()
        val key = ArchiveCrypto.deriveKey(password, salt, 1000)

        val plaintext = "Vault T Secure Encrypted Payload Content".toByteArray(Charsets.UTF_8)
        val encrypted = ArchiveCrypto.encryptBytes(plaintext, key)

        // Ensure IV is prepended and ciphertext is authenticated
        assertTrue(encrypted.size > plaintext.size + 12)
        assertFalse(Arrays.equals(plaintext, encrypted))

        val decrypted = ArchiveCrypto.decryptBytes(encrypted, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `verifier token correctly validates correct password and rejects wrong password`() {
        val correctPassword = "CorrectPassword789".toCharArray()
        val wrongPassword = "WrongPassword000".toCharArray()
        val salt = ArchiveCrypto.generateSalt()

        val validKey = ArchiveCrypto.deriveKey(correctPassword, salt, 1000)
        val wrongKey = ArchiveCrypto.deriveKey(wrongPassword, salt, 1000)

        val verifierToken = ArchiveCrypto.createVerifierToken(validKey)

        // Correct password must verify true
        assertTrue(ArchiveCrypto.verifyPassword(verifierToken, validKey))

        // Wrong password must verify false
        assertFalse(ArchiveCrypto.verifyPassword(verifierToken, wrongKey))
    }

    @Test(expected = Exception::class)
    fun `tampered ciphertext throws exception during decryption`() {
        val password = "TamperTestPassword".toCharArray()
        val salt = ArchiveCrypto.generateSalt()
        val key = ArchiveCrypto.deriveKey(password, salt, 1000)

        val plaintext = "Tamper check payload".toByteArray(Charsets.UTF_8)
        val encrypted = ArchiveCrypto.encryptBytes(plaintext, key)

        // Flip a byte in the ciphertext payload
        encrypted[encrypted.size - 5] = (encrypted[encrypted.size - 5].toInt() xor 0xFF).toByte()

        ArchiveCrypto.decryptBytes(encrypted, key)
    }

    @Test
    fun `chunked streaming encryption and decryption round trip`() {
        val password = "StreamPassword321".toCharArray()
        val salt = ArchiveCrypto.generateSalt()
        val key = ArchiveCrypto.deriveKey(password, salt, 1000)

        // Generate 200 KB simulated binary data (multiple 64KB chunks)
        val originalData = ByteArray(200 * 1024)
        for (i in originalData.indices) {
            originalData[i] = (i % 256).toByte()
        }

        val encryptedStream = ByteArrayOutputStream()
        ArchiveCrypto.encryptStream(ByteArrayInputStream(originalData), encryptedStream, key)

        val encryptedBytes = encryptedStream.toByteArray()
        assertTrue(encryptedBytes.size > originalData.size)

        val decryptedStream = ByteArrayOutputStream()
        ArchiveCrypto.decryptStream(ByteArrayInputStream(encryptedBytes), decryptedStream, key)

        val decryptedBytes = decryptedStream.toByteArray()
        assertArrayEquals(originalData, decryptedBytes)
    }

    @Test
    fun `password strength evaluation returns proper scores and labels`() {
        val emptyStrength = ArchiveCrypto.evaluatePasswordStrength("".toCharArray())
        assertEquals(0, emptyStrength.first)

        val weakStrength = ArchiveCrypto.evaluatePasswordStrength("abc".toCharArray())
        assertEquals("Weak", weakStrength.second)

        val goodStrength = ArchiveCrypto.evaluatePasswordStrength("GoodPass123!".toCharArray())
        assertTrue(goodStrength.first >= 3)
    }

    @Test
    fun `hex encoding and decoding round trip works`() {
        val bytes = byteArrayOf(0x00, 0x1A, 0x7F, 0xFF.toByte(), 0x42)
        val hex = ArchiveCrypto.toHex(bytes)
        assertEquals("001a7fff42", hex)

        val decoded = ArchiveCrypto.fromHex(hex)
        assertArrayEquals(bytes, decoded)
    }
}

