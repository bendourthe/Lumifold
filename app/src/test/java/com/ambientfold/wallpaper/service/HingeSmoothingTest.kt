package com.ambientfold.wallpaper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HingeSmoothingTest {

    @Test
    fun firstReadingSnapsToTarget() {
        assertEquals(42f, HingeTracker.smoothToward(Float.NaN, 42f, 0.016f), 0f)
    }

    @Test
    fun convergesWithinAFewTimeConstants() {
        var v = 0f
        repeat(30) { v = HingeTracker.smoothToward(v, 90f, 1f / 120f) }
        assertEquals(90f, v, 0.5f)
    }

    @Test
    fun isFrameRateIndependent() {
        var at60 = 0f
        repeat(6) { at60 = HingeTracker.smoothToward(at60, 100f, 1f / 60f) }
        var at120 = 0f
        repeat(12) { at120 = HingeTracker.smoothToward(at120, 100f, 1f / 120f) }
        assertEquals(at60, at120, 0.01f)
    }

    @Test
    fun neverOvershoots() {
        val v = HingeTracker.smoothToward(10f, 20f, 1f)
        assertTrue(v <= 20f)
    }
}
