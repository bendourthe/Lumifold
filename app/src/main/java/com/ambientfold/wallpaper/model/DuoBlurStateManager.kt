package com.ambientfold.wallpaper.model

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * Unified state manager for DuoBlur foldable telemetry.
 * Synchronizes hardware hinge angle, active display context, and optical frost progress
 * across both the Cover Display (Display 0) and Inner Main Display (Display 1).
 */
object DuoBlurStateManager {

    // Current smoothed hinge angle: 0.0° (closed) to 180.0° (flat open)
    var currentAngle: Float = 140.0f
        private set

    // Active display classification: false = Outer Cover Display, true = Inner Main Display
    var isInnerDisplayActive: Boolean = true
        private set

    var activeDisplayId: Int = 1
        private set

    private val telemetryListeners = CopyOnWriteArrayList<TelemetryListener>()

    interface TelemetryListener {
        fun onTelemetryUpdated(
            angle: Float,
            isInner: Boolean,
            displayId: Int,
            frostProgress: Float,
            phaseLabel: String
        )
    }

    fun addListener(listener: TelemetryListener) {
        if (!telemetryListeners.contains(listener)) {
            telemetryListeners.add(listener)
            listener.onTelemetryUpdated(
                currentAngle,
                isInnerDisplayActive,
                activeDisplayId,
                getCurrentFrostProgress(currentAngle, isInnerDisplayActive),
                getPhaseLabel(currentAngle)
            )
        }
    }

    fun removeListener(listener: TelemetryListener) {
        telemetryListeners.remove(listener)
    }

    fun updateHingeAngle(angleDegrees: Float) {
        currentAngle = angleDegrees.coerceIn(0f, 180f)
        notifyUpdate()
    }

    fun updateDisplayContext(displayId: Int, isInner: Boolean) {
        activeDisplayId = displayId
        isInnerDisplayActive = isInner
        notifyUpdate()
    }

    private fun notifyUpdate() {
        val frost = getCurrentFrostProgress(currentAngle, isInnerDisplayActive)
        val phase = getPhaseLabel(currentAngle)
        for (l in telemetryListeners) {
            l.onTelemetryUpdated(currentAngle, isInnerDisplayActive, activeDisplayId, frost, phase)
        }
    }

    /**
     * Calculates the normalized frost progress (0.0 to 1.0):
     * 1. Outer Cover Display (0° to 90°): Starts clear (0.0) at 0° and scales up to peak frost (1.0) at 90°.
     * 2. Inner Main Display (90° to 180°): Starts at peak frost (1.0) at 90° (matching handoff) and clears down to 0.0 at 180°.
     */
    fun getCurrentFrostProgress(angle: Float, isInner: Boolean): Float {
        return if (!isInner) {
            (angle / 90.0f).coerceIn(0f, 1f)
        } else {
            ((180.0f - angle) / 90.0f).coerceIn(0f, 1f)
        }
    }

    fun getPhaseLabel(angle: Float): String {
        return when {
            angle < 5f -> "STATE_CLOSED (0° Cover Resting)"
            angle < 85f -> "Phase 1: Cover Frosting (${String.format("%.1f", angle)}°)"
            angle <= 95f -> "Phase 2: Concurrent Handoff (${String.format("%.1f", angle)}°)"
            angle < 175f -> "Phase 3: Inner Defrosting (${String.format("%.1f", angle)}°)"
            else -> "STATE_OPENED (180° Flat Square Display)"
        }
    }
}
