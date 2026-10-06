package com.vaultt.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.vaultt.core.security.CryptoManager
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class TinkDataSource(
    private val cryptoManager: CryptoManager,
    private val file: File,
    private val associatedData: ByteArray
) : BaseDataSource(true) {

    private var inputStream: InputStream? = null
    private var opened = false
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val fis = FileInputStream(file)
        inputStream = cryptoManager.newDecryptingStream(fis, associatedData)
        
        // Skip to the requested position
        val skipped = inputStream?.skip(dataSpec.position) ?: 0L
        if (skipped < dataSpec.position) {
            throw Exception("Could not skip to position")
        }

        if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            bytesRemaining = dataSpec.length
        } else {
            // This is tricky with encrypted streams because we don't know the exact plaintext length
            // without reading it all, but for Tink AES-GCM-HKDF, it's roughly file size minus overhead.
            // For now, we'll use a large number or try to estimate.
            bytesRemaining = file.length() // Approximate
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else minOf(bytesRemaining, length.toLong()).toInt()
        val read = inputStream?.read(buffer, offset, bytesToRead) ?: -1
        
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= read
        }
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = Uri.fromFile(file)

    override fun close() {
        inputStream?.close()
        inputStream = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
