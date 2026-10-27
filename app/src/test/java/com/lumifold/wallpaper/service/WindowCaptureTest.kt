package com.lumifold.wallpaper.service

import org.junit.Assert.*
import org.junit.Test

class WindowCaptureTest {
    private val left = CaptureWindow(3530, 0, 0, 1038, 2152, true)
    private val right = CaptureWindow(3640, 1038, 0, 2076, 2152, false)

    @Test fun settingsPanesKeepTheirOriginalBoundsRegardlessOfLayerOrder() {
        assertEquals(listOf(left, right), selectCaptureWindows(listOf(right, left), 2076, 2152))
    }

    @Test fun eitherSettingsPaneCanBeActive() {
        val panes = listOf(left.copy(active = false), right.copy(active = true))
        assertEquals(panes, selectCaptureWindows(panes, 2076, 2152))
    }

    @Test fun fullWidthCoverWithSystemBarInsetsRemainsSupported() {
        val cover = CaptureWindow(1, 0, 80, 1080, 2254, true)
        assertEquals(listOf(cover), selectCaptureWindows(listOf(cover), 1080, 2342))
    }

    @Test fun oneSettingsPaneIsNeverStretchedAcrossTheHinge() {
        assertNull(selectCaptureWindows(listOf(left), 2076, 2152))
        assertNull(selectCaptureWindows(listOf(right.copy(active = true)), 2076, 2152))
    }

    @Test fun gapsOverlapsAndUnevenPanesCannotProduceInventedPixels() {
        for (invalid in listOf(right.copy(left = 1039), right.copy(left = 1037), right.copy(top = 1))) {
            assertNull(selectCaptureWindows(listOf(left, invalid), 2076, 2152))
        }
    }

    @Test fun dialogsAndOutOfDisplayWindowsAreRejected() {
        assertNull(selectCaptureWindows(listOf(left.copy(right = 2076, top = 400, bottom = 1700)), 2076, 2152))
        assertNull(selectCaptureWindows(listOf(left.copy(right = 2076, top = -1)), 2076, 2152))
        assertNull(selectCaptureWindows(listOf(left.copy(right = 2077)), 2076, 2152))
        assertNull(selectCaptureWindows(listOf(left.copy(right = 2076), right.copy(active = true)), 2076, 2152))
    }

    @Test fun noActiveWindowOrDuplicateIdsCannotCreateAFrame() {
        assertNull(selectCaptureWindows(emptyList(), 2076, 2152))
        assertNull(selectCaptureWindows(listOf(left.copy(active = false), right), 2076, 2152))
        assertNull(selectCaptureWindows(listOf(left, right.copy(id = left.id)), 2076, 2152))
    }

    @Test fun oldPanelGeometryCannotPassAfterHandoff() {
        assertNull(selectCaptureWindows(listOf(left, right), 1080, 2342))
        assertNull(selectCaptureWindows(listOf(CaptureWindow(1, 0, 0, 1080, 2342, true)), 2076, 2152))
    }

    @Test fun newWindowDoesNotWaitForAnotherWindowsCapture() {
        val throttle = WindowCaptureThrottle()
        throttle.record(1, 100)
        assertEquals(0L, throttle.delayMs(listOf(2, 3), 120))
        assertEquals(320L, throttle.delayMs(listOf(1), 120))
    }

    @Test fun repeatedPaneCaptureHonorsTheLatestIndividualRequest() {
        val throttle = WindowCaptureThrottle()
        throttle.record(1, 100)
        throttle.record(2, 110)
        assertEquals(330L, throttle.delayMs(listOf(1, 2), 120))
        assertEquals(1L, throttle.delayMs(listOf(2), 449))
        assertEquals(0L, throttle.delayMs(listOf(1, 2), 450))
    }

    @Test fun retriesCannotExtendTheOriginalDeadline() {
        assertEquals(16L, captureRetryDelay(100, 600, 0))
        assertEquals(320L, captureRetryDelay(120, 600, 320))
        assertNull(captureRetryDelay(300, 600, 340))
        assertNull(captureRetryDelay(584, 600, 0))
        assertNull(captureRetryDelay(600, 600, 0))
        assertNull(captureRetryDelay(100, 600, null))
    }
}
