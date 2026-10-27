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

    fun sample(degrees: Float, now: Long) {
        if (!degrees.isFinite()) return
        angle = degrees.coerceIn(0f, 180f)
        if (smoothed.isNaN()) {
            smoothed = angle * 180f / 175f
            lastFrame = now
        }
        if (!anchor.isNaN() && abs(angle - anchor) >= 0.6f) lastMove = now
        if (anchor.isNaN() || abs(angle - anchor) >= 0.6f) anchor = angle
    }

    fun isActive(now: Long): Boolean =
        angle > 1f && angle < 174f && age(now, lastMove) < IDLE_MS

    fun degrees(inner: Boolean, now: Long): Float {
        val rest = if (inner) 180f else 0f
        if (angle.isNaN()) return rest
        // The hardware reads about 175 degrees when flat. Preserve the original Frost curve.
        val raw = (angle * 180f / 175f).coerceIn(0f, 180f)
        val dt = age(now, lastFrame).coerceAtMost(100L)
        lastFrame = now
        smoothed = if (angle <= 1f || angle >= 174f) raw
            else smoothed + (raw - smoothed) * (1f - exp(-dt / 45f))
        val target = if (inner) smoothed.coerceAtLeast(90f) else smoothed.coerceAtMost(90f)
        val idle = age(now, lastMove)
        // The Pixel reports 5-degree steps, with observed gaps up to about 0.6 s on a slow fold.
        val strength = if (idle < 700L) 1f else 1f - smooth((idle - 700L) / (IDLE_MS - 700f))
        return rest + (target - rest) * strength
    }

    fun reset() {
        anchor = Float.NaN
        angle = Float.NaN
        lastMove = Long.MIN_VALUE
        smoothed = Float.NaN
        lastFrame = Long.MIN_VALUE
    }

    private fun age(now: Long, then: Long) = if (then == Long.MIN_VALUE) Long.MAX_VALUE else (now - then).coerceAtLeast(0)
    private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    companion object {
        const val IDLE_MS = 1000L
    }
}
