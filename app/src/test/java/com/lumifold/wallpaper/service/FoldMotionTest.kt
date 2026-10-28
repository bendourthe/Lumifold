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
        assertTrue(settled(motion, inner = false, from = 200) in 1f..89f)
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

    @Test fun closingHandsTheFullEffectToTheCoverAtTheCloseSwitch() {
        val closing = FoldMotion()
        closing.sample(175f, 0)
        closing.sample(44f, 100)
        // The cover lights below about 45 degrees and must start at full effect, not mid-fade.
        assertTrue(settled(closing, inner = false, from = 100) > 85f)
        // The inner screen reaches full effect at the same handover angle.
        closing.sample(47f, 400)
        assertTrue(settled(closing, inner = true, from = 400) < 95f)

        val opening = FoldMotion()
        opening.sample(0f, 0)
        opening.sample(44f, 100)
        // Opening still hands over at 90 degrees, so 44 is halfway on the cover.
        assertEquals(45f, settled(opening, inner = false, from = 100), 1f)
    }

    @Test fun closingStretchesTheInnerEffectOverTheRangeItIsLit() {
        val closing = FoldMotion()
        closing.sample(175f, 0)
        closing.sample(120f, 100)
        val opening = FoldMotion()
        opening.sample(0f, 0)
        opening.sample(120f, 100)
        assertTrue(settled(closing, inner = true, from = 100) > settled(opening, inner = true, from = 100) + 10f)
    }

    @Test fun foldingBackAfterAParkedFinishShowsTheClosingEffect() {
        val motion = FoldMotion()
        motion.sample(0f, 0)
        motion.sample(100f, 100)
        val done = 100 + FoldMotion.IDLE_MS
        assertFalse(motion.isActive(done))
        // One sensor step back is jitter, not a new fold.
        motion.sample(95f, done + 100)
        assertFalse(motion.isActive(done + 100))
        motion.sample(85f, done + 200)
        assertTrue(motion.isActive(done + 200))
        // The close started on the inner screen, so the cover lights with the full effect.
        motion.sample(44f, done + 400)
        assertTrue(settled(motion, inner = false, from = done + 400) > 85f)
    }

    @Test fun jitterAtAParkedPoseDoesNotRestartTheEffect() {
        val motion = FoldMotion()
        motion.sample(0f, 0)
        motion.sample(100f, 100)
        val done = 100 + FoldMotion.IDLE_MS
        for ((i, angle) in listOf(95f, 100f, 95f, 100f).withIndex()) {
            motion.sample(angle, done + 100L * (i + 1))
            assertFalse(motion.isActive(done + 100L * (i + 1)))
        }
    }

    private fun settled(motion: FoldMotion, inner: Boolean, from: Long): Float {
        var shown = 0f
        for (now in from..from + 300 step 16) shown = motion.degrees(inner, now)
        return shown
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
