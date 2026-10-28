package com.lumifold.wallpaper.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class FoldOpticsTest {

    private fun rad(deg: Double) = deg * PI / 180.0

    @Test
    fun duoMotionIsZeroAtEachDisplaysRestingAngle() {
        assertEquals(0.0, FoldOptics.duoMotion(0.0, inner = false), 1e-12)
        assertEquals(0.0, FoldOptics.duoMotion(PI, inner = true), 1e-12)
        assertEquals(1.0, FoldOptics.duoMotion(rad(90.0), inner = false), 1e-12)
        assertEquals(1.0, FoldOptics.duoMotion(rad(90.0), inner = true), 1e-12)
        assertTrue(FoldOptics.duoMotion(rad(30.0), inner = false) < FoldOptics.duoMotion(rad(50.0), inner = false))
        assertTrue(FoldOptics.duoMotion(rad(150.0), inner = true) < FoldOptics.duoMotion(rad(120.0), inner = true))
    }

    @Test
    fun duoBlurIsSharpAtHingeAndGrowsTowardFreeEdge() {
        assertEquals(0.0, FoldOptics.duoSigma(1.0, 0.0), 1e-12)
        assertEquals(FoldOptics.DUO_SIGMA, FoldOptics.duoSigma(1.0, 1.0), 1e-12)
        assertTrue(FoldOptics.duoSigma(1.0, 0.5) < 0.4 * FoldOptics.DUO_SIGMA)
        assertEquals(0.0, FoldOptics.duoSigma(0.0, 1.0), 1e-12)
    }

    @Test
    fun duoFreeEdgeFallsIntoBlackAtFullMotion() {
        assertEquals(0.0, FoldOptics.duoBrightness(1.0, 1.0), 1e-12)
        assertEquals(1.0, FoldOptics.duoBrightness(1.0, 0.15), 1e-12)
        assertEquals(1.0, FoldOptics.duoBrightness(0.0, 1.0), 1e-12)
        assertTrue(FoldOptics.duoBrightness(0.5, 0.7) in 0.2..0.8)
    }

    @Test
    fun strengthVisiblyChangesShadingWithoutSaturatingAtDefault() {
        val low = FoldOptics.duoBrightness(1.0, 0.8, strength = 0.25)
        val normal = FoldOptics.duoBrightness(1.0, 0.8, strength = 1.0)
        val high = FoldOptics.duoBrightness(1.0, 0.8, strength = 1.5)
        assertTrue(low - normal > 0.5)
        assertTrue(normal > high)
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
    fun innerHalvesAreUndistortedWhenFlat() {
        val h = 2.07
        for (x in listOf(-1.0, -0.4, 0.0, 0.6, 1.0)) {
            val p = FoldOptics.projectInnerHalf(x, 0.3, PI, h)
            assertEquals(x, p.first, 1e-9)
            assertEquals(0.3, p.second, 1e-9)
        }
    }

    @Test
    fun innerOuterEdgesStayOnThePictureEdges() {
        val h = 2.07
        for (deg in listOf(90.0, 120.0, 150.0, 175.0)) {
            for (y in listOf(0.0, 0.7, h)) {
                val left = FoldOptics.projectInnerHalf(-1.0, y, rad(deg), h)
                val right = FoldOptics.projectInnerHalf(1.0, y, rad(deg), h)
                assertEquals(-1.0, left.first, 1e-9)
                assertEquals(1.0, right.first, 1e-9)
                assertEquals(y, left.second, 1e-9)
                assertEquals(y, right.second, 1e-9)
            }
        }
    }

    @Test
    fun innerHalvesMirrorEachOther() {
        val h = 2.07
        for (x in listOf(0.1, 0.5, 0.9)) {
            val left = FoldOptics.projectInnerHalf(-x, 0.4, rad(110.0), h)
            val right = FoldOptics.projectInnerHalf(x, 0.4, rad(110.0), h)
            assertEquals(-right.first, left.first, 1e-12)
            assertEquals(right.second, left.second, 1e-12)
        }
    }

    @Test
    fun innerRaysNeverLeaveThePicture() {
        val h = 2.07
        for (deg in 90..180 step 5) {
            for (i in -10..10) {
                val x = i / 10.0
                for (y in listOf(0.0, 0.5, h * 0.5, h)) {
                    val p = FoldOptics.projectInnerHalf(x, y, rad(deg.toDouble()), h)
                    assertTrue(kotlin.math.abs(p.first) <= kotlin.math.abs(x) + 1e-12)
                    assertTrue(kotlin.math.abs(p.second - h * 0.5) <= kotlin.math.abs(y - h * 0.5) + 1e-12)
                }
            }
        }
    }

    @Test
    fun foldedInnerCreaseMagnifiesAndKeepsOrder() {
        val h = 2.07
        val crease = FoldOptics.projectInnerHalf(0.0, 0.2, rad(90.0), h)
        assertEquals(0.0, crease.first, 1e-12)
        // Far from the viewer, the crease shows less of the picture, so content looks larger.
        assertTrue(crease.second > 0.2)
        var previous = -1.0
        for (i in 0..20) {
            val x = i / 20.0
            val p = FoldOptics.projectInnerHalf(x, 0.2, rad(90.0), h)
            assertTrue(p.first >= previous)
            previous = p.first
        }
    }

    @Test
    fun innerProjectionKeepsStraightLinesStraight() {
        // The anchored-picture illusion is a projective map: a picture row stays a straight line.
        val h = 2.07
        val a = FoldOptics.projectInnerHalf(0.1, 0.3, rad(100.0), h)
        val b = FoldOptics.projectInnerHalf(0.5, 0.3, rad(100.0), h)
        val c = FoldOptics.projectInnerHalf(0.9, 0.3, rad(100.0), h)
        val cross = (b.first - a.first) * (c.second - a.second) - (b.second - a.second) * (c.first - a.first)
        assertEquals(0.0, cross, 1e-12)
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
