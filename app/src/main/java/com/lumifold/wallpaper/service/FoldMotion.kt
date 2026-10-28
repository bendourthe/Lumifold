package com.lumifold.wallpaper.service

import kotlin.math.abs
import kotlin.math.exp

/** Follows current hinge travel without replaying motion after a display or capture delay. */
class FoldMotion {
    private var anchor = Float.NaN
    private var angle = Float.NaN
    private var lastMove = Long.MIN_VALUE
    private var smoothed = Float.NaN
    private var lastFrame = Long.MIN_VALUE
    private var finished = false

    fun sample(degrees: Float, now: Long) {
        if (!degrees.isFinite()) return
        latchFinished(now)
        angle = degrees.coerceIn(0f, 180f)
        if (angle <= 1f || angle >= 174f) finished = false
        if (smoothed.isNaN()) {
            smoothed = angle * 180f / 175f
            lastFrame = now
        }
        if (!anchor.isNaN() && abs(angle - anchor) >= 0.6f) lastMove = now
        if (anchor.isNaN() || abs(angle - anchor) >= 0.6f) anchor = angle
    }

    fun isActive(now: Long): Boolean {
        latchFinished(now)
        return !finished && angle > 1f && angle < 174f && age(now, lastMove) < IDLE_MS
    }

    fun degrees(inner: Boolean, now: Long): Float {
        val rest = if (inner) 180f else 0f
        latchFinished(now)
        if (angle.isNaN() || finished) return rest
        // The hardware reads about 175 degrees when flat. Preserve the original Frost curve.
        val raw = (angle * 180f / 175f).coerceIn(0f, 180f)
        val dt = age(now, lastFrame).coerceAtMost(100L)
        lastFrame = now
        smoothed = if (angle <= 1f || angle >= 174f) raw
            else smoothed + (raw - smoothed) * (1f - exp(-dt / 45f))
        val target = if (inner) smoothed.coerceAtLeast(90f) else smoothed.coerceAtMost(90f)
        val idle = age(now, lastMove)
        // A held hinge keeps the effect (the Pixel also reports 5-degree steps up to about 0.6 s
        // apart on a slow fold). A parked phone then finishes it toward each panel's rest pose:
        // the inner screen plays forward to open, the cover plays back to clean.
        val strength = if (idle < HOLD_MS) 1f else 1f - smooth((idle - HOLD_MS) / FINISH_MS.toFloat())
        return rest + (target - rest) * strength
    }

    fun reset() {
        anchor = Float.NaN
        angle = Float.NaN
        lastMove = Long.MIN_VALUE
        smoothed = Float.NaN
        lastFrame = Long.MIN_VALUE
        finished = false
    }

    /**
     * A gesture that sat still mid-fold until its effect finished stays finished, so moving on
     * does not bring the effect back. Reaching fully open or closed starts a new gesture.
     */
    private fun latchFinished(now: Long) {
        if (lastMove != Long.MIN_VALUE && angle > 1f && angle < 174f && age(now, lastMove) >= IDLE_MS) finished = true
    }

    private fun age(now: Long, then: Long) = if (then == Long.MIN_VALUE) Long.MAX_VALUE else (now - then).coerceAtLeast(0)
    private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    companion object {
        /** How long a still hinge keeps the effect before it finishes on its own. */
        const val HOLD_MS = 1500L

        /** How long the effect takes to finish once the hinge is parked. */
        const val FINISH_MS = 600L

        const val IDLE_MS = HOLD_MS + FINISH_MS
    }
}
