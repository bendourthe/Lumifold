package com.ambientfold.wallpaper.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class FoldOpticsTest {

    private fun rad(deg: Double) = deg * PI / 180.0

    @Test
    fun duoSigmaMatchesPublishedReconstruction() {
        // Empirical Health: at a 30 degree fold, sigma 48 at the free edge and 6 halfway.
        assertEquals(48.0, FoldOptics.duoSigma(rad(30.0), 1.0, sigmaMax = 96.0), 1e-9)
        assertEquals(6.0, FoldOptics.duoSigma(rad(30.0), 0.5, sigmaMax = 96.0), 1e-9)
    }

    @Test
    fun duoSigmaIsSharpAtHingeAndAtBothEndpoints() {
        assertEquals(0.0, FoldOptics.duoSigma(rad(60.0), 0.0), 1e-12)
        assertEquals(0.0, FoldOptics.duoSigma(0.0, 1.0), 1e-12)
        assertEquals(0.0, FoldOptics.duoSigma(PI, 1.0), 1e-9)
    }

    @Test
    fun duoSigmaPeaksAtNinetyDegrees() {
        val peak = FoldOptics.duoSigma(rad(90.0), 1.0)
        assertEquals(FoldOptics.SIGMA_MAX, peak, 1e-12)
        assertTrue(FoldOptics.duoSigma(rad(85.0), 1.0) > 0.99 * peak)
        assertTrue(FoldOptics.duoSigma(rad(45.0), 1.0) < peak)
    }

    @Test
    fun projectionIsIdentityWhenClosedAndMirroredWhenFlat() {
        val h = 2.24
        val closed = FoldOptics.projectMovingPanel(0.7, 1.3, 0.0, h)
        assertEquals(0.7, closed.first, 1e-9)
        assertEquals(1.3, closed.second, 1e-9)
        val flat = FoldOptics.projectMovingPanel(0.7, 1.3, PI, h)
        assertEquals(-0.7, flat.first, 1e-9)
        assertEquals(1.3, flat.second, 1e-9)
    }

    @Test
    fun hingeLinePointsNeverMove() {
        for (deg in listOf(10.0, 45.0, 90.0, 135.0)) {
            val p = FoldOptics.projectMovingPanel(0.0, 0.4, rad(deg), 2.24)
            assertEquals(0.0, p.first, 1e-9)
            assertEquals(0.4, p.second, 1e-9)
        }
    }

    @Test
    fun liftedPanelMagnifiesTowardTheViewer() {
        // A point lifted toward the eye projects farther from the eye axis vertically.
        val h = 2.24
        val p = FoldOptics.projectMovingPanel(1.0, 0.2, rad(45.0), h)
        assertTrue(p.second < 0.2)
        assertTrue(p.first < 1.0)
    }

    @Test
    fun brightnessCurvesKeepEndpointsAtFullBrightness() {
        assertEquals(1.0, FoldOptics.coverBrightness(0.0, 1.0), 1e-12)
        assertEquals(1.0, FoldOptics.innerMovingBrightness(PI, 1.0), 1e-9)
        assertEquals(1.0, FoldOptics.innerStationaryBrightness(PI), 1e-9)
        assertTrue(FoldOptics.innerMovingBrightness(rad(90.0), 1.0) < FoldOptics.innerStationaryBrightness(rad(90.0)))
    }

    @Test
    fun displayClassification() {
        assertTrue(FoldOptics.isInnerDisplay(2076, 2152)) // Pixel 9 Pro Fold inner
        assertTrue(FoldOptics.isInnerDisplay(2208, 1840)) // Pixel Fold inner
        assertFalse(FoldOptics.isInnerDisplay(1080, 2424)) // Pixel 9 Pro Fold cover
        assertFalse(FoldOptics.isInnerDisplay(1080, 2092)) // Pixel Fold cover
        assertFalse(FoldOptics.isInnerDisplay(0, 100))
    }

    @Test
    fun naturalSizeSwapsForQuarterTurns() {
        assertEquals(Pair(2076, 2152), FoldOptics.naturalSize(2152, 2076, 1))
        assertEquals(Pair(2076, 2152), FoldOptics.naturalSize(2076, 2152, 2))
    }

    @Test
    fun rotationNinetyPutsNaturalLeftEdgeAtBottom() {
        // Surface 200 x 100 at ROTATION_90; natural size is 100 x 200.
        val bottomLeft = FoldOptics.toNatural(0f, 100f, 200f, 100f, 1)
        assertEquals(0f, bottomLeft.first, 1e-6f)
        val topRight = FoldOptics.toNatural(200f, 0f, 200f, 100f, 1)
        assertEquals(100f, topRight.first, 1e-6f)
        assertEquals(200f, topRight.second, 1e-6f)
    }

    @Test
    fun planeHeightCoversBothDisplays() {
        val h = FoldOptics.planeHeight(2076, 2152, 1080, 2424)
        assertEquals(2424f / 1080f, h, 1e-5f)
    }
}
