package com.ambientfold.wallpaper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HingeMathTest {

    @Test
    fun testFullyOpenProducesZeroBlurProgress() {
        val progress = HingeSensorObserver.calculateEaseInProgress(180f)
        assertEquals("Fully unfolded (180°) must produce zero blur progress", 0.0f, progress, 0.0001f)
    }

    @Test
    fun testFullyClosedProducesMaxBlurProgress() {
        val progress = HingeSensorObserver.calculateEaseInProgress(0f)
        assertEquals("Fully closed (0°) must produce max blur progress 1.0", 1.0f, progress, 0.0001f)
    }

    @Test
    fun testHalfFoldedProducesLessThanThirtyFivePercentBlur() {
        val progress = HingeSensorObserver.calculateEaseInProgress(90f)
        assertTrue("At 90° tabletop mode blur progress should be < 0.35, was $progress", progress < 0.35f)
        assertTrue("At 90° tabletop mode blur progress should be > 0.20, was $progress", progress > 0.20f)
    }

    @Test
    fun testClampedBoundaryPreservation() {
        val overProgress = HingeSensorObserver.calculateEaseInProgress(200f)
        assertEquals("Over-range angle must clamp blur progress to 0.0", 0.0f, overProgress, 0.0001f)

        val underProgress = HingeSensorObserver.calculateEaseInProgress(-20f)
        assertEquals("Negative angle must clamp blur progress to 1.0", 1.0f, underProgress, 0.0001f)
    }

    @Test
    fun testExponentialMovingAverageSmoothingConvergence() {
        var currentSmoothed = 180f
        val targetRaw = 90f
        val factor = 0.35f

        // Step 1
        currentSmoothed = HingeSensorObserver.smoothAngle(currentSmoothed, targetRaw, factor)
        assertEquals(148.5f, currentSmoothed, 0.01f)

        // Multiple steps converge towards 90
        repeat(15) {
            currentSmoothed = HingeSensorObserver.smoothAngle(currentSmoothed, targetRaw, factor)
        }
        assertEquals("Smoothed value must converge toward target", 90f, currentSmoothed, 0.1f)
    }

    @Test
    fun testTwoPhaseProgressHandoff() {
        // Phase 1: Cover screen (0° to 90°)
        assertEquals(0.0f, HingeSensorObserver.calculateTwoPhaseProgress(0f, isInnerScreen = false), 0.001f)
        assertEquals(0.5f, HingeSensorObserver.calculateTwoPhaseProgress(45f, isInnerScreen = false), 0.001f)
        assertEquals(1.0f, HingeSensorObserver.calculateTwoPhaseProgress(90f, isInnerScreen = false), 0.001f)

        // Phase 3: Inner screen defrosting (90° to 180°)
        assertEquals(1.0f, HingeSensorObserver.calculateTwoPhaseProgress(90f, isInnerScreen = true), 0.001f)
        assertEquals(0.5f, HingeSensorObserver.calculateTwoPhaseProgress(135f, isInnerScreen = true), 0.001f)
        assertEquals(0.0f, HingeSensorObserver.calculateTwoPhaseProgress(180f, isInnerScreen = true), 0.001f)
    }
}
