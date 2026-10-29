package com.lumifold.wallpaper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FoldSpanTest {

    @Test
    fun neverOverridesWhichPanelsAreOn() {
        assertEquals(FoldCommand.ONE, FoldSpan.commandFor(true, 0f))
        assertEquals(FoldCommand.ONE, FoldSpan.commandFor(true, 45f, opening = true))
        assertEquals(FoldCommand.ONE, FoldSpan.commandFor(true, 60f, opening = false))
        assertEquals(FoldCommand.ONE, FoldSpan.commandFor(true, 8f, opening = false))
        assertEquals(FoldCommand.ONE, FoldSpan.commandFor(true, FoldSpan.OPEN_MIN_DEGREES))
        assertEquals(FoldCommand.ONE, FoldSpan.commandFor(true, 175f))
        assertEquals(FoldCommand.ONE, FoldSpan.commandFor(false, 90f))
        assertEquals(FoldCommand.ONE, FoldSpan.commandFor(true, Float.NaN))
        assertFalse(FoldSpan.wantsBothScreens(true, 1.1f))
        assertFalse(FoldSpan.wantsBothScreens(true, 45f))
        assertFalse(FoldSpan.wantsBothScreens(true, 90f))
        assertFalse(FoldSpan.wantsBothScreens(true, 169f))
        assertFalse(FoldSpan.wantsBothScreens(false, 90f))
    }
}
