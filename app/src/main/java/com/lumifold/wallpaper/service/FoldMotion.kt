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
    private var closing = false
    private var turn = Float.NaN
    private var closingFromInner = false
    private var closedFromInnerAt = Long.MIN_VALUE
    private var revealAt = Long.MIN_VALUE

    fun sample(degrees: Float, now: Long) {
        if (!degrees.isFinite()) return
        latchFinished(now)
        angle = degrees.coerceIn(0f, 180f)
        if (angle <= 1f || angle >= 174f) {
            if (angle <= 1f && closing && closingFromInner) closedFromInnerAt = now
            if (angle >= 174f) revealAt = Long.MIN_VALUE
            finished = false
            startRun(angle >= 174f, angle)
        } else if (turn.isNaN() || (if (closing) angle < turn else angle > turn)) {
            turn = angle
        } else if (abs(angle - turn) >= REVERSE_DEG) {
            // Folding back the other way is a new fold: it shows the effect again, even after a
            // parked finish, with the mapping for its own direction.
            finished = false
            if (closing) revealAt = Long.MIN_VALUE
            startRun(!closing, turn)
        }
        if (smoothed.isNaN()) {
            smoothed = angle * 180f / 175f
            lastFrame = now
        }
        if (!anchor.isNaN() && abs(angle - anchor) >= 0.6f) lastMove = now
        if (anchor.isNaN() || abs(angle - anchor) >= 0.6f) anchor = angle
    }

    /**
     * A panel switch can take a second or two while the hinge is held still. The hold starts again
     * when the new panel shows, so its effect is not already finishing when it appears.
     *
     * The cover is the exception on a close from the inner screen, including one that has already
     * shut: it lights late, below about 45 degrees, in the dimmed state Android's switch brings it up
     * in. It starts from its fully covered pose, which matches that state, and reveals the wallpaper
     * over [FINISH_MS] on its own, whatever the hinge does, then stays clear.
     */
    fun panelShown(inner: Boolean, now: Long) {
        if (!inner && ((isCloseFromInner && angle > 1f) || age(now, closedFromInnerAt) < RECENT_CLOSE_MS)) {
            revealAt = now
            return
        }
        if (!isActive(now)) return
        lastMove = now
    }

    /** Whether this fold is a close that started on the inner screen, which the cover joins late. */
    val isCloseFromInner: Boolean get() = closing && closingFromInner

    private fun revealing(now: Long) = revealAt != Long.MIN_VALUE && now < revealAt + FINISH_MS

    fun isActive(now: Long): Boolean {
        if (revealing(now)) return true
        latchFinished(now)
        return !finished && angle > 1f && angle < 174f && age(now, lastMove) < IDLE_MS
    }

    fun degrees(inner: Boolean, now: Long): Float {
        val rest = if (inner) 180f else 0f
        if (!inner && revealAt != Long.MIN_VALUE) {
            if (now < revealAt + FINISH_MS) return 90f * (1f - smooth((now - revealAt) / FINISH_MS.toFloat()))
            revealAt = Long.MIN_VALUE
            if (angle > 1f && angle < 174f) finished = true
            return rest
        }
        latchFinished(now)
        if (angle.isNaN() || finished) return rest
        // The hardware reads about 175 degrees when flat. Preserve the original Frost curve.
        val raw = (angle * 180f / 175f).coerceIn(0f, 180f)
        val dt = age(now, lastFrame).coerceAtMost(100L)
        lastFrame = now
        smoothed = if (angle <= 1f || angle >= 174f) raw
            else smoothed + (raw - smoothed) * (1f - exp(-dt / 45f))
        val pose = if (closing && (inner || closingFromInner)) closingPose(smoothed) else smoothed
        val target = if (inner) pose.coerceAtLeast(90f) else pose.coerceAtMost(90f)
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
        closing = false
        turn = Float.NaN
        closingFromInner = false
        closedFromInnerAt = Long.MIN_VALUE
        revealAt = Long.MIN_VALUE
    }

    /**
     * Starts a run of hinge travel in one direction from [from]. A close that starts above 90
     * degrees is on the inner screen, so the cover lights only near [CLOSE_SWITCH]; a close that
     * starts lower is on the cover already and keeps its continuous fade.
     */
    private fun startRun(closing: Boolean, from: Float) {
        this.closing = closing
        closingFromInner = closing && from > 90f
        turn = angle
    }

    /**
     * Effects are drawn for a 90-degree handover: cover from closed to 90, inner from 90 to open.
     * A close hands over near [CLOSE_SWITCH] instead, so each panel's span is stretched to the
     * range it is actually lit: the inner screen reaches full effect and the cover starts from it.
     */
    private fun closingPose(degrees: Float): Float =
        if (degrees <= CLOSE_SWITCH) degrees * 90f / CLOSE_SWITCH
        else 90f + (degrees - CLOSE_SWITCH) * 90f / (180f - CLOSE_SWITCH)

    /**
     * A gesture that sat still mid-fold until its effect finished stays finished, so moving on
     * does not bring the effect back. Reaching fully open or closed, or folding back the other
     * way by [REVERSE_DEG], starts a new gesture.
     */
    private fun latchFinished(now: Long) {
        if (lastMove != Long.MIN_VALUE && angle > 1f && angle < 174f && age(now, lastMove) >= IDLE_MS) finished = true
    }

    private fun age(now: Long, then: Long) = if (then == Long.MIN_VALUE) Long.MAX_VALUE else (now - then).coerceAtLeast(0)
    private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    companion object {
        /** How long a still hinge keeps the effect before it finishes on its own. */
        const val HOLD_MS = 2000L

        /** How long the effect takes to finish once the hinge is parked. */
        const val FINISH_MS = 1200L

        const val IDLE_MS = HOLD_MS + FINISH_MS

        /** Android hands a closing fold from the inner screen to the cover below about 45 degrees. */
        const val CLOSE_SWITCH = 45f

        /** A close from the inner screen that shut this recently still gets the cover's reveal. */
        const val RECENT_CLOSE_MS = 1500L

        /** Travel back from a run's furthest angle that counts as folding the other way (two sensor steps). */
        const val REVERSE_DEG = 10f
    }
}
