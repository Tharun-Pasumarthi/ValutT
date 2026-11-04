package com.vaultt.core.security

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlipLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager,
    private val vaultPrefs: VaultPrefs
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var isRegistered = false
    private var lastProximityValue = Float.MAX_VALUE
    private var faceDownStartTime = 0L
    private var lastFlipLockTime = 0L

    fun startListening() {
        if (!vaultPrefs.isFlipToLockEnabled()) return
        if (isRegistered || sensorManager == null) return

        lastProximityValue = Float.MAX_VALUE
        faceDownStartTime = 0L
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        isRegistered = true
    }

    fun stopListening() {
        if (!isRegistered || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        faceDownStartTime = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !vaultPrefs.isFlipToLockEnabled()) return

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                lastProximityValue = event.values.getOrNull(0) ?: Float.MAX_VALUE
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val z = event.values.getOrNull(2) ?: 0f
                // Z axis < -8.5 m/s^2 means screen is facing directly down towards ground
                val isFaceDown = z < -8.5f
                // Proximity sensor MUST have detected an object close (e.g. table surface or hand covering)
                val isProximityNear = lastProximityValue < 2.0f && lastProximityValue != Float.MAX_VALUE

                val now = System.currentTimeMillis()
                if (isFaceDown && isProximityNear) {
                    if (faceDownStartTime == 0L) {
                        faceDownStartTime = now
                    } else if (now - faceDownStartTime >= 600L && now - lastFlipLockTime > 3000L) {
                        lastFlipLockTime = now
                        faceDownStartTime = 0L
                        securityManager.lock()
                    }
                } else {
                    faceDownStartTime = 0L
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
