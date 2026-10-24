package com.ambientfold.wallpaper.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.exp

/** Reports the hinge angle in degrees: 0 closed, 180 flat. */
class HingeTracker(
    context: Context,
    private val onChange: (Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    private var registered = false

    val isAvailable: Boolean get() = sensor != null

    /** Latest reading, or NaN before the first event. */
    var angle: Float = Float.NaN
        private set

    fun start() {
        val s = sensor ?: return
        if (!registered) {
            registered = sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        if (registered) {
            sensorManager.unregisterListener(this)
            registered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HINGE_ANGLE) return
        angle = (event.values.firstOrNull() ?: return).coerceIn(0f, 180f)
        onChange(angle)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        /** Frame-rate independent exponential approach of [current] toward [target]. */
        fun smoothToward(current: Float, target: Float, dtSec: Float, tauSec: Float = 0.045f): Float {
            if (current.isNaN()) return target
            val k = 1f - exp(-dtSec / tauSec)
            return current + (target - current) * k
        }
    }
}
