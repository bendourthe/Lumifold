package com.lumifold.wallpaper.service

import org.junit.Assert.*
import org.junit.Test

class ScreenFrameTest {
    @Test fun rejectsBlackHandoffAndItsObservedNearBlackNoise() {
        assertTrue(isBlankScreenFrame(2076, 2152) { _, _ -> 0xff000000.toInt() })
        assertTrue(isBlankScreenFrame(2076, 2152) { x, y ->
            val gray = (x + y) % 5
            0xff000000.toInt() or (gray shl 16) or (gray shl 8) or gray
        })
    }

    @Test fun acceptsDarkSettingsBackground() {
        assertFalse(isBlankScreenFrame(1080, 2342) { _, _ -> 0xff121212.toInt() })
    }

    @Test fun preservesContentInAnyColorChannel() {
        for (color in intArrayOf(0xff090000.toInt(), 0xff000900.toInt(), 0xff000009.toInt())) {
            assertFalse(isBlankScreenFrame(1080, 2342) { _, _ -> color })
        }
    }

    @Test fun samplesBothCornersAndStaysWithinTheBitmap() {
        for (corner in listOf(0 to 0, 2075 to 2151)) {
            assertFalse(isBlankScreenFrame(2076, 2152) { x, y ->
                assertTrue(x in 0 until 2076 && y in 0 until 2152)
                if (x to y == corner) 0xffffffff.toInt() else 0xff000000.toInt()
            })
        }
    }

    @Test fun emptyOrTransparentCapturesCannotCoverTheScreen() {
        assertTrue(isBlankScreenFrame(0, 2152) { _, _ -> fail("empty bitmap sampled"); 0 })
        assertTrue(isBlankScreenFrame(1080, 2342) { _, _ -> 0x00ffffff })
    }

    @Test fun inspectionCostDoesNotGrowWithPanelResolution() {
        for ((width, height) in listOf(1 to 1, 1080 to 2342, 2076 to 2152)) {
            var reads = 0
            assertTrue(isBlankScreenFrame(width, height) { _, _ -> reads++; 0xff000000.toInt() })
            assertTrue("unbounded capture inspection", reads <= 1024)
        }
    }
}
