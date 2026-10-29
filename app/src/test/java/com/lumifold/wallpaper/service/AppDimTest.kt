package com.lumifold.wallpaper.service

import org.junit.Assert.*
import org.junit.Test

class AppDimTest {
    // The ramp ends are fully dim or fully lit, so the comparisons include them.
    private fun dimAt(dim: AppDim, x: Float): Boolean = if (dim.dimLeft) x <= dim.front - DIM_BAND + 1e-4f else x >= dim.front + DIM_BAND - 1e-4f
    private fun litAt(dim: AppDim, x: Float): Boolean = if (dim.dimLeft) x >= dim.front + DIM_BAND - 1e-4f else x <= dim.front - DIM_BAND + 1e-4f

    @Test fun coverDarkensFromItsRightEdgeAndIsFullyDimAt90() {
        val closed = appDim(inner = false, degrees = 0f, fullSwipe = true)
        assertTrue(litAt(closed, 0f) && litAt(closed, 1f))
        val half = appDim(inner = false, degrees = 45f, fullSwipe = true)
        assertTrue(dimAt(half, 0.9f) && litAt(half, 0.1f))
        val open = appDim(inner = false, degrees = 90f, fullSwipe = true)
        assertTrue(dimAt(open, 0f) && dimAt(open, 1f))
    }

    @Test fun innerStartsFullyDimAndClearsFromTheRightEdge() {
        val lit = appDim(inner = true, degrees = 90f, fullSwipe = true)
        assertTrue(dimAt(lit, 0f) && dimAt(lit, 1f))
        val half = appDim(inner = true, degrees = 135f, fullSwipe = true)
        assertTrue(dimAt(half, 0.1f) && litAt(half, 0.9f))
        val flat = appDim(inner = true, degrees = 180f, fullSwipe = true)
        assertTrue(litAt(flat, 0f) && litAt(flat, 1f))
    }

    @Test fun withoutFullSwipeOnlyTheLeftHalfStartsDimAndClearsFromTheHinge() {
        val lit = appDim(inner = true, degrees = 90f, fullSwipe = false)
        assertTrue(dimAt(lit, 0.2f) && litAt(lit, 0.9f))
        assertEquals(0.5f, lit.front, 1e-4f)
        val flat = appDim(inner = true, degrees = 180f, fullSwipe = false)
        assertTrue(litAt(flat, 0f))
    }
}
