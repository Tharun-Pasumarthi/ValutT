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
        try {
            val fis = FileInputStream(file)
            inputStream = cryptoManager.newDecryptingStream(fis, associatedData)

            if (dataSpec.position > 0) {
                var remaining = dataSpec.position
                while (remaining > 0) {
                    val skipped = inputStream?.skip(remaining) ?: 0L
                    if (skipped <= 0) {
                        if (inputStream?.read() == -1) throw java.io.IOException("Unable to seek")
                        remaining--
                    } else {
                        remaining -= skipped
                    }
                }
            }

            bytesRemaining = dataSpec.length
            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (error: Throwable) {
            close()
            throw error
        }
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
