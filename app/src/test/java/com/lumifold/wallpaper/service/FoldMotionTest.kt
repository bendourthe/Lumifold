package com.lumifold.wallpaper.service

import org.junit.Assert.*
import org.junit.Test

class FoldMotionTest {
    @Test fun initialHalfOpenIsNotMotion() {
        val motion = FoldMotion()
        motion.sample(80f, 0)
        assertFalse(motion.isActive(0))
        assertEquals(0f, motion.degrees(false, 0), 0f)
    }

    @Test fun flatHardwareReadingIsClear() {
        val motion = FoldMotion()
        motion.sample(160f, 0)
        motion.sample(175f, 20)
        assertEquals(180f, motion.degrees(true, 20), 0f)
    }

    @Test fun closingFollowsTheHingeOnBothPanelsThenClears() {
        val motion = FoldMotion()
        motion.sample(175f, 0)
        motion.sample(155f, 20)
        assertTrue(motion.degrees(true, 20) < 180f)
        motion.sample(35f, 200)
        assertTrue(motion.isActive(200))
        assertTrue(motion.degrees(false, 200) in 1f..89f)
        motion.sample(0f, 300)
        assertEquals(0f, motion.degrees(false, 300), 0f)
        assertFalse(motion.isActive(300))
    }

    @Test fun openingDoesNotReplayAfterTheHingeHasReachedFlat() {
        val motion = FoldMotion()
        motion.sample(0f, 0)
        motion.sample(90f, 50)
        assertTrue(motion.isActive(50))
        motion.sample(175f, 100)
        for (now in listOf(100L, 400L, 600L, 950L)) {
            assertFalse(motion.isActive(now))
            assertEquals(180f, motion.degrees(true, now), 0f)
        }
    }

    @Test fun stationaryAndNoisyHingeExpiresWithoutMoreEvents() {
        val motion = FoldMotion()
        motion.sample(60f, 0)
        motion.sample(65f, 10)
        motion.sample(65.2f, 200)
        assertTrue(motion.isActive(200))
        val settled = 10 + FoldMotion.IDLE_MS + 10
        assertFalse(motion.isActive(settled))
        assertEquals(180f, motion.degrees(true, settled), 0f)
    }

    @Test fun unrelatedSurfaceChangeDoesNotReplayOldFold() {
        val motion = FoldMotion()
        motion.sample(160f, 0)
        motion.sample(0f, 100)
        assertFalse(motion.isActive(3000))
        assertEquals(0f, motion.degrees(false, 3000), 0f)
    }

    @Test fun invalidSamplesAndResetCannotKeepOverlayAlive() {
        val motion = FoldMotion()
        motion.sample(0f, 0)
        motion.sample(30f, 20)
        motion.sample(Float.NaN, 30)
        motion.sample(Float.POSITIVE_INFINITY, 40)
        assertFalse(motion.isActive(20 + FoldMotion.IDLE_MS + 80))
        motion.reset()
        assertEquals(180f, motion.degrees(true, 1101), 0f)
    }

    @Test fun slowFiveDegreeStepsDoNotPulseBackToClearBetweenSamples() {
        val motion = FoldMotion()
        motion.sample(20f, 0)
        var previous = 0f
        for ((angle, time) in listOf(25f to 500L, 30f to 1100L, 35f to 1700L)) {
            motion.sample(angle, time)
            for (now in time until time + 600L step 16L) {
                assertTrue("motion lost between sensor steps at $now", motion.isActive(now))
                val shown = motion.degrees(false, now)
                assertTrue("effect faded despite continuing fold at $now", shown >= previous)
                previous = shown
            }
        }
        val settled = 1700 + FoldMotion.IDLE_MS + 100
        assertFalse(motion.isActive(settled))
        assertEquals(0f, motion.degrees(false, settled), 0f)
    }

    @Test fun changingPanelsDoesNotRewindTheCurrentPose() {
        val motion = FoldMotion()
        motion.sample(150f, 0)
        motion.sample(155f, 100)
        assertEquals(90f, motion.degrees(false, 100), 0f)
        assertTrue(motion.degrees(true, 100) > 150f)
        assertTrue(motion.isActive(100))
    }

    @Test fun lateCoverAvailabilityDoesNotManufactureNewMotion() {
        val motion = FoldMotion()
        motion.sample(175f, 0)
        motion.sample(0f, 100)
        for (now in listOf(100L, 350L, 1000L, 2100L)) {
            assertFalse(motion.isActive(now))
            assertEquals(0f, motion.degrees(false, now), 0f)
        }
    }

    @Test fun partialFoldReversalFollowsTheNewDirection() {
        val motion = FoldMotion()
        motion.sample(30f, 0)
        motion.sample(70f, 100)
        val peak = motion.degrees(false, 100)
        motion.sample(40f, 200)
        assertTrue(motion.degrees(false, 200) < peak)
        assertTrue(motion.isActive(200))
    }

    @Test fun settledEndpointsDoNotHoldAFrozenScreen() {
        val motion = FoldMotion()
        motion.sample(140f, 0)
        motion.sample(175f, 100)
        assertFalse(motion.isActive(100))
        motion.sample(0f, 200)
        assertFalse(motion.isActive(200))
    }

    @Test fun heldMidFoldKeepsTheEffectThenFinishesToEachPanelsRest() {
        val motion = FoldMotion()
        motion.sample(0f, 0)
        motion.sample(60f, 100)
        // Held: the effect stays at the current pose instead of fading.
        assertTrue(motion.degrees(false, 100 + FoldMotion.HOLD_MS - 100) > 50f)
        assertTrue(motion.isActive(100 + FoldMotion.IDLE_MS - 50))
        // Then it finishes: the inner screen plays forward to open, the cover back to clean.
        val inner = motion.degrees(true, 100 + FoldMotion.HOLD_MS + FoldMotion.FINISH_MS / 2)
        assertTrue(inner in 91f..179f)
        val done = 100 + FoldMotion.HOLD_MS + FoldMotion.FINISH_MS
        assertEquals(180f, motion.degrees(true, done), 1e-3f)
        assertEquals(0f, motion.degrees(false, done), 1e-3f)
    }

    @Test fun finishedEffectStaysFinishedUntilTheHingeReachesRest() {
        val motion = FoldMotion()
        motion.sample(0f, 0)
        motion.sample(60f, 100)
        val done = 100 + FoldMotion.IDLE_MS
        assertEquals(0f, motion.degrees(false, done), 1e-3f)
        // Continuing the same gesture after the finish does not bring the effect back.
        motion.sample(80f, done + 100)
        assertFalse(motion.isActive(done + 100))
        assertEquals(0f, motion.degrees(false, done + 100), 1e-3f)
        motion.sample(140f, done + 300)
        assertEquals(180f, motion.degrees(true, done + 300), 1e-3f)
        // Reaching rest ends the gesture; the next fold shows the effect again.
        motion.sample(175f, done + 500)
        motion.sample(150f, done + 700)
        assertTrue(motion.isActive(done + 700))
        assertTrue(motion.degrees(true, done + 700) < 180f)
    }
}
