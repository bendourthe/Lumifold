package com.lumifold.wallpaper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HingeSpringTest {

    @Test
    fun firstStepSnapsToTarget() {
        val s = HingeTracker.Spring()
        assertEquals(37f, s.step(37f, 0.016f), 0f)
    }

    @Test
    fun followsAStepWithoutOvershoot() {
        val s = HingeTracker.Spring()
        s.step(0f, 0.016f)
        var peak = 0f
        repeat(60) { peak = maxOf(peak, s.step(90f, 1f / 60f)) }
        assertTrue("overshoot $peak", peak <= 90.01f)
        assertEquals(90f, s.position, 0.5f)
    }

    @Test
    fun settlesWithinAQuarterSecond() {
        val s = HingeTracker.Spring()
        s.step(0f, 0.016f)
        repeat(15) { s.step(90f, 1f / 60f) }
        assertEquals(90f, s.position, 6f)
    }

    @Test
    fun smoothsSensorStepsIntoContinuousMotion() {
        // A hinge reporting whole degrees at 20 Hz while the display runs at 120 Hz.
        val s = HingeTracker.Spring()
        s.step(0f, 0.016f)
        var last = s.position
        var maxJump = 0f
        for (frame in 1..240) {
            val target = (frame / 6).toFloat()
            val p = s.step(target, 1f / 120f)
            maxJump = maxOf(maxJump, p - last)
            last = p
        }
        assertTrue("largest per-frame jump $maxJump", maxJump < 0.35f)
    }

    @Test
    fun clearForgetsPosition() {
        val s = HingeTracker.Spring()
        s.step(10f, 0.016f)
        s.clear()
        assertTrue(s.position.isNaN())
        assertEquals(120f, s.step(120f, 0.016f), 0f)
    }
}
