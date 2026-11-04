package com.vaultt.core.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class IntruderLog(
    val id: String,
    val timestamp: Long,
    val attemptCount: Int,
    val imageFileName: String?
)

@Singleton
class IntruderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager
) {
    private val intruderDir = File(context.filesDir, "intruders").apply {
        if (!exists()) mkdirs()
    }
    private val logsFile = File(intruderDir, "intruder_logs.json")

    suspend fun recordBreakInAttempt(attemptCount: Int) = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPermission) {
            captureFrontSelfie(id) { capturedBytes ->
                var savedFileName: String? = null
                if (capturedBytes != null && capturedBytes.isNotEmpty()) {
                    val encFile = File(intruderDir, "$id.enc")
                    runCatching {
                        val encrypted = cryptoManager.encrypt(capturedBytes)
                        encFile.writeBytes(encrypted)
                        savedFileName = encFile.name
                    }
                }
                saveLogEntry(IntruderLog(id, timestamp, attemptCount, savedFileName))
            }
        } else {
            saveLogEntry(IntruderLog(id, timestamp, attemptCount, null))
        }
    }

    private fun captureFrontSelfie(id: String, onCaptured: (ByteArray?) -> Unit) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            onCaptured(null)
            return
        }

        runCatching {
            val frontCameraId = cameraManager.cameraIdList.firstOrNull { camId ->
                cameraManager.getCameraCharacteristics(camId)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }

            if (frontCameraId == null) {
                onCaptured(null)
                return
            }

            val handlerThread = HandlerThread("IntruderCapture_$id").apply { start() }
            val bgHandler = Handler(handlerThread.looper)
            val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)

            var isFinished = false
            fun cleanup(camera: CameraDevice?) {
                if (!isFinished) {
                    isFinished = true
                    runCatching { camera?.close() }
                    runCatching { imageReader.close() }
                    runCatching { handlerThread.quitSafely() }
                }
            }

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                val bytes = image?.let { img ->
                    val buffer = img.planes[0].buffer
                    val b = ByteArray(buffer.remaining())
                    buffer.get(b)
                    img.close()
                    b
                }
                onCaptured(bytes)
                cleanup(null)
            }, bgHandler)

            cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    runCatching {
                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(imageReader.surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        camera.createCaptureSession(
                            listOf(imageReader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    runCatching {
                                        session.capture(captureBuilder.build(), null, bgHandler)
                                    }.onFailure {
                                        cleanup(camera)
                                        onCaptured(null)
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    cleanup(camera)
                                    onCaptured(null)
                                }
                            },
                            bgHandler
                        )
                    }.onFailure {
                        cleanup(camera)
                        onCaptured(null)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cleanup(camera)
                    onCaptured(null)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cleanup(camera)
                    onCaptured(null)
                }
            }, bgHandler)
        }.onFailure {
            onCaptured(null)
        }
    }

    @Synchronized
    private fun saveLogEntry(entry: IntruderLog) {
        val logs = loadLogsInternal().toMutableList()
        logs.add(0, entry)
        writeLogsInternal(logs)
    }

    suspend fun getIntruderLogs(): List<IntruderLog> = withContext(Dispatchers.IO) {
        loadLogsInternal()
    }

    suspend fun getIntruderImage(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(intruderDir, fileName)
        if (!file.exists()) return@withContext null
        runCatching {
            cryptoManager.decrypt(file.readBytes())
        }.getOrNull()
    }

    suspend fun clearAllLogs() = withContext(Dispatchers.IO) {
        intruderDir.listFiles()?.forEach { it.delete() }
        writeLogsInternal(emptyList())
    }

    suspend fun deleteLog(id: String) = withContext(Dispatchers.IO) {
        val logs = loadLogsInternal().toMutableList()
        val item = logs.find { it.id == id }
        if (item != null) {
            item.imageFileName?.let { File(intruderDir, it).delete() }
            logs.remove(item)
            writeLogsInternal(logs)
        }
    }

    private fun loadLogsInternal(): List<IntruderLog> {
        if (!logsFile.exists()) return emptyList()
        return runCatching {
            val jsonStr = logsFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<IntruderLog>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    IntruderLog(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        attemptCount = obj.getInt("attemptCount"),
                        imageFileName = if (obj.has("imageFileName") && !obj.isNull("imageFileName")) {
                            obj.getString("imageFileName")
                        } else null
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun writeLogsInternal(logs: List<IntruderLog>) {
        runCatching {
            val array = JSONArray()
            logs.forEach { entry ->
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("timestamp", entry.timestamp)
                    put("attemptCount", entry.attemptCount)
                    put("imageFileName", entry.imageFileName ?: JSONObject.NULL)
                }
                array.put(obj)
            }
            logsFile.writeText(array.toString())
        }
    }
}
