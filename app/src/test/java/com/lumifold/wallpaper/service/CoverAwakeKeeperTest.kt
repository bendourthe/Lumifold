package com.lumifold.wallpaper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverAwakeKeeperTest {

    @Test
    fun holdEndsJustBeforeTheUsersScreenTimeout() {
        assertEquals(29_000L, CoverAwakeKeeper.holdTimeoutMs(30_000L, keyguardLocked = false))
        assertEquals(119_000L, CoverAwakeKeeper.holdTimeoutMs(120_000L, keyguardLocked = false))
    }

    @Test
    fun lockScreenHoldIsCapped() {
        assertEquals(9_000L, CoverAwakeKeeper.holdTimeoutMs(120_000L, keyguardLocked = true))
    }

    @Test
    fun holdIsBoundedForUnusualTimeouts() {
        assertEquals(CoverAwakeKeeper.MIN_HOLD_MS, CoverAwakeKeeper.holdTimeoutMs(2_000L, false))
        assertEquals(29_000L, CoverAwakeKeeper.holdTimeoutMs(0L, false))
        assertEquals(CoverAwakeKeeper.MAX_HOLD_MS, CoverAwakeKeeper.holdTimeoutMs(Int.MAX_VALUE.toLong(), false))
    }

    @Test
    fun holdsOnlyForAVisibleInteractiveCoverDisplay() {
        val base = { enabled: Boolean, visible: Boolean, inner: Boolean, preview: Boolean, interactive: Boolean, angle: Float, closing: Boolean ->
            CoverAwakeKeeper.shouldHold(enabled, visible, inner, preview, interactive, angle, closing)
        }
        assertTrue(base(true, true, false, false, true, 0f, false))
        assertTrue(base(true, true, false, false, true, 85f, false))
        assertFalse("disabled", base(false, true, false, false, true, 0f, false))
        assertFalse("hidden", base(true, false, false, false, true, 0f, false))
        assertFalse("inner display while opening", base(true, true, true, false, true, 40f, false))
        assertTrue("inner display while closing", base(true, true, true, false, true, 40f, true))
        assertFalse("still open while closing", base(true, true, true, false, true, 150f, true))
        assertFalse("picker preview", base(true, true, false, true, true, 0f, false))
        assertFalse("screen off", base(true, true, false, false, false, 0f, false))
        assertFalse("cover faces away", base(true, true, false, false, true, 130f, false))
    }
}
