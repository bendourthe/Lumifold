package com.ambientfold.wallpaper.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class HingeSensorObserver(
    private val context: Context,
    private val onAngleChanged: (progress: Float, angleDegrees: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val hingeSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

    private var smoothedAngle: Float = 180.0f
    private val smoothingFactor = 0.35f // Exponential moving average smoothing

    val isSensorAvailable: Boolean
        get() = hingeSensor != null

    fun start() {
        hingeSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HINGE_ANGLE) {
            val rawAngle = event.values.firstOrNull() ?: 180.0f
            // Smooth raw mechanical readings to avoid jitter
            smoothedAngle = (smoothedAngle * (1f - smoothingFactor)) + (rawAngle * smoothingFactor)
            val clampedAngle = smoothedAngle.coerceIn(0f, 180f)
            val progress = clampedAngle / 180.0f
            onAngleChanged(progress, clampedAngle)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun simulateAngle(angleDegrees: Float) {
        val clamped = angleDegrees.coerceIn(0f, 180f)
        val progress = clamped / 180.0f
        onAngleChanged(progress, clamped)
    }
}
