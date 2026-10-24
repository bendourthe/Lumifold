package com.lumifold.wallpaper.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

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

    /**
     * Critically damped spring that follows the hinge. It keeps velocity between frames, so the
     * sensor's 1 degree steps read as one continuous motion, and it never overshoots.
     */
    class Spring(private val omega: Float = 22f) {
        var position = Float.NaN
            private set
        private var velocity = 0f

        fun clear() {
            position = Float.NaN
            velocity = 0f
        }

        fun step(target: Float, dtSec: Float): Float {
            if (position.isNaN()) {
                position = target
                velocity = 0f
                return position
            }
            var remaining = dtSec.coerceIn(0f, 0.1f)
            while (remaining > 0f) {
                val h = min(remaining, 0.004f)
                velocity += (omega * omega * (target - position) - 2f * omega * velocity) * h
                position += velocity * h
                remaining -= h
            }
            return position
        }

        fun isSettled(target: Float): Boolean =
            !position.isNaN() && abs(target - position) < 0.05f && abs(velocity) < 0.5f

        fun snap(target: Float) {
            position = target
            velocity = 0f
        }
    }

    companion object {
        /** Frame-rate independent exponential approach of [current] toward [target]. */
        fun smoothToward(current: Float, target: Float, dtSec: Float, tauSec: Float = 0.045f): Float {
            if (current.isNaN()) return target
            val k = 1f - exp(-dtSec / tauSec)
            return current + (target - current) * k
        }
    }
}
