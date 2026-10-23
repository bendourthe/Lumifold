package com.ambientfold.wallpaper.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.pow

class HingeSensorObserver(
    private val context: Context,
    private val onAngleChanged: (blurProgress: Float, angleDegrees: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val hingeSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

    private var smoothedAngle: Float = 180.0f
    private val smoothingFactor = 0.35f // Exponential moving average smoothing factor
    private var isRegistered = false

    val isSensorAvailable: Boolean
        get() = hingeSensor != null

    fun start() {
        if (!isRegistered) {
            hingeSensor?.let { sensor ->
                isRegistered = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME) ?: false
            }
        }
    }

    fun stop() {
        if (isRegistered) {
            sensorManager?.unregisterListener(this)
            isRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HINGE_ANGLE) {
            val rawAngle = event.values.firstOrNull() ?: 180.0f
            smoothedAngle = smoothAngle(smoothedAngle, rawAngle, smoothingFactor)
            val clampedAngle = smoothedAngle.coerceIn(0f, 180f)
            val blurProgress = calculateEaseInProgress(clampedAngle)
            onAngleChanged(blurProgress, clampedAngle)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun simulateAngle(angleDegrees: Float) {
        val clamped = angleDegrees.coerceIn(0f, 180f)
        val blurProgress = calculateEaseInProgress(clamped)
        onAngleChanged(blurProgress, clamped)
    }

    companion object {
        fun smoothAngle(previous: Float, raw: Float, factor: Float = 0.35f): Float {
            return (previous * (1f - factor)) + (raw * factor)
        }

        fun calculateEaseInProgress(angleDegrees: Float): Float {
            val normalized = (angleDegrees / 180.0f).coerceIn(0f, 1f)
            // 1.8 power curve: stays crisp across 180° to 90°, elevates progressively below 90°
            return (1.0f - normalized).toDouble().pow(1.8).toFloat().coerceIn(0f, 1f)
        }

        fun calculateTwoPhaseProgress(angleDegrees: Float, isInnerScreen: Boolean): Float {
            return if (!isInnerScreen) {
                (angleDegrees / 90.0f).coerceIn(0f, 1f)
            } else {
                ((180.0f - angleDegrees) / 90.0f).coerceIn(0f, 1f)
            }
        }
    }
}
