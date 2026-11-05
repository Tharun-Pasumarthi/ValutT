package com.vaultt.core.crypto

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class InvalidExportPasswordException(message: String = "Incorrect export password or corrupted archive.") : Exception(message)
class ArchiveCorruptedException(message: String = "Archive integrity check failed. The archive may be corrupted or tampered with.") : Exception(message)

object ArchiveCrypto {

    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val PBKDF2_ITERATIONS = 150_000
    const val KEY_LENGTH_BITS = 256
    const val SALT_LENGTH_BYTES = 32
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val CHUNK_SIZE_BYTES = 64 * 1024 // 64 KB chunking for large file streaming

    private const val VERIFIER_MAGIC = "VAULT_T_AUTH_KEY_VERIFIER_V1"

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Creates an encrypted verifier token using the derived key.
     * Stored in the archive header to validate password correctness instantly on restore.
     */
    fun createVerifierToken(key: SecretKey): ByteArray {
        return encryptBytes(VERIFIER_MAGIC.toByteArray(Charsets.UTF_8), key)
    }

    /**
     * Verifies if the candidate key can successfully decrypt the verifier token.
     */
    fun verifyPassword(verifierToken: ByteArray, key: SecretKey): Boolean {
        return try {
            val decrypted = decryptBytes(verifierToken, key)
            val result = String(decrypted, Charsets.UTF_8)
            result == VERIFIER_MAGIC
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encrypts a small byte array (e.g., manifest, metadata, thumbnail) with AES-256-GCM.
     * Format: [12-byte IV][Ciphertext + 16-byte Auth Tag]
     */
    fun encryptBytes(plaintext: ByteArray, key: SecretKey, associatedData: ByteArray? = null): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }

        val ciphertext = cipher.doFinal(plaintext)
        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        return result
    }

    /**
     * Decrypts a small byte array with AES-256-GCM.
     */
    fun decryptBytes(encryptedWithIv: ByteArray, key: SecretKey, associatedData: ByteArray? = null): ByteArray {
        if (encryptedWithIv.size < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
            throw ArchiveCorruptedException("Encrypted data is too short to be valid.")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(encryptedWithIv, 0, iv, 0, iv.size)
        val ciphertextOffset = iv.size
        val ciphertextSize = encryptedWithIv.size - iv.size

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }

        return cipher.doFinal(encryptedWithIv, ciphertextOffset, ciphertextSize)
    }

    /**
     * Streams encryption of large files (up to multi-GB) using 64KB chunked AES-256-GCM.
     * Each chunk format:
     *   [chunkCiphertextSize: 4 bytes (Int)]
     *   [iv: 12 bytes]
     *   [ciphertext + tag: chunkCiphertextSize bytes]
     * Terminated with a 0-length chunk: [0: 4 bytes]
     */
    fun encryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        key: SecretKey,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ) {
        val buffer = ByteArray(CHUNK_SIZE_BYTES)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        var totalBytesRead = 0L

        while (true) {
            val read = readFullBuffer(inputStream, buffer)
            if (read <= 0) break

            secureRandom.nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val ciphertext = cipher.doFinal(buffer, 0, read)
            
            // Write chunk length (4 bytes)
            val lenBytes = ByteBuffer.allocate(4).putInt(ciphertext.size).array()
            outputStream.write(lenBytes)
            // Write IV (12 bytes)
            outputStream.write(iv)
            // Write Ciphertext + Tag
            outputStream.write(ciphertext)

            totalBytesRead += read
            onProgress?.invoke(totalBytesRead)
        }

        // Write terminal marker (0 bytes length)
        val endMarker = ByteBuffer.allocate(4).putInt(0).array()
        outputStream.write(endMarker)
        outputStream.flush()
    }

    /**
     * Decrypts a chunked AES-256-GCM stream.
     */
    fun decryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        key: SecretKey,
        onProgress: ((bytesWritten: Long) -> Unit)? = null
    ) {
        val lenBuf = ByteArray(4)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        var totalBytesWritten = 0L

        while (true) {
            val lenRead = readFully(inputStream, lenBuf)
            if (lenRead < 4) {
                throw ArchiveCorruptedException("Unexpected end of chunked stream length header.")
            }
            val chunkCiphertextSize = ByteBuffer.wrap(lenBuf).int
            if (chunkCiphertextSize == 0) {
                // End of stream marker reached
                break
            }
            if (chunkCiphertextSize < 0 || chunkCiphertextSize > (CHUNK_SIZE_BYTES + 1024)) {
                throw ArchiveCorruptedException("Invalid chunk size: $chunkCiphertextSize")
            }

            val ivRead = readFully(inputStream, iv)
            if (ivRead < GCM_IV_LENGTH_BYTES) {
                throw ArchiveCorruptedException("Truncated chunk IV.")
            }

            val cipherBytes = ByteArray(chunkCiphertextSize)
            val cipherRead = readFully(inputStream, cipherBytes)
            if (cipherRead < chunkCiphertextSize) {
                throw ArchiveCorruptedException("Truncated chunk payload.")
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val plaintext = cipher.doFinal(cipherBytes)
            outputStream.write(plaintext)

            totalBytesWritten += plaintext.size
            onProgress?.invoke(totalBytesWritten)
        }

        outputStream.flush()
    }

    private fun readFullBuffer(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = input.read(buffer, total, buffer.size - total)
            if (count == -1) break
            total += count
        }
        return total
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = input.read(buffer, total, buffer.size - total)
            if (count == -1) break
            total += count
        }
        return total
    }

    /**
     * Evaluates password strength:
     * Returns Triple(Score 0..4, Label, Feedback)
     */
    fun evaluatePasswordStrength(password: CharArray): Triple<Int, String, String> {
        if (password.isEmpty()) return Triple(0, "Empty", "Please enter a password.")
        var score = 0
        if (password.size >= 8) score++
        if (password.size >= 12) score++
        var hasLower = false
        var hasUpper = false
        var hasDigit = false
        var hasSpecial = false

        for (c in password) {
            when {
                c.isLowerCase() -> hasLower = true
                c.isUpperCase() -> hasUpper = true
                c.isDigit() -> hasDigit = true
                !c.isWhitespace() -> hasSpecial = true
            }
        }

        val charTypes = (if (hasLower) 1 else 0) +
                (if (hasUpper) 1 else 0) +
                (if (hasDigit) 1 else 0) +
                (if (hasSpecial) 1 else 0)

        if (charTypes >= 3) score++
        if (charTypes == 4 && password.size >= 10) score++

        score = score.coerceIn(0, 4)

        return when (score) {
            0, 1 -> Triple(score, "Weak", "Use at least 8 characters with letters, numbers and symbols.")
            2 -> Triple(score, "Fair", "Add capital letters, numbers, or special symbols.")
            3 -> Triple(score, "Good", "Strong password. Ensure you remember or safely record it.")
            4 -> Triple(score, "Very Strong", "Excellent password strength.")
            else -> Triple(score, "Good", "")
        }
    }

    fun wipe(array: CharArray) {
        Arrays.fill(array, '\u0000')
    }

    fun wipe(array: ByteArray) {
        Arrays.fill(array, 0.toByte())
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}

