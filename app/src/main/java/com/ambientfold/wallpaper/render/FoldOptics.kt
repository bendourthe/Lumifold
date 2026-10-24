package com.ambientfold.wallpaper.render

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Geometry of a book-style foldable and the iPhone Duo frosted-glass model.
 *
 * Units: one panel width (the cover display width, or half the inner display width) is 1.0.
 * The hinge runs along x = 0. The stationary panel (inner right half) spans x in [0, 1]; when
 * flat open, the moving panel spans x in [-1, 0]. The hinge angle is 0 when closed and PI when
 * flat. Both displays sample one shared image plane, so content stays put while the glass moves.
 *
 * FoldShaders implements the same formulas in AGSL; keep both in sync.
 */
object FoldOptics {

    /** Eye distance in panel widths (about 30 cm for a 7.5 cm panel). */
    const val VIEW_DISTANCE = 4.0

    /** Peak Gaussian sigma at the free edge, as a fraction of one panel width. */
    const val SIGMA_MAX = 0.17

    /** Inner displays are near-square; covers are tall. */
    private const val INNER_MAX_ASPECT = 1.4f

    /**
     * Duo blur strength: `sigmaMax * sin(angle) * d^3`, where d runs from 0 at the hinge to 1 at
     * the free edge. Both settled endpoints (closed and flat) are sharp.
     */
    fun duoSigma(angleRad: Double, distanceFromHinge: Double, sigmaMax: Double = SIGMA_MAX): Double {
        val d = distanceFromHinge.coerceIn(0.0, 1.0)
        return sigmaMax * sin(angleRad.coerceIn(0.0, PI)) * d * d * d
    }

    /**
     * Projects a point on the moving panel onto the shared image plane along the viewing ray.
     * [u] is the distance from the hinge, [y] the vertical plane coordinate, [planeHeight] the
     * plane height in panel widths. Returns plane coordinates (x, y).
     */
    fun projectMovingPanel(
        u: Double,
        y: Double,
        angleRad: Double,
        planeHeight: Double,
        viewDistance: Double = VIEW_DISTANCE
    ): Pair<Double, Double> {
        val px = u * kotlin.math.cos(angleRad)
        val pz = u * sin(angleRad)
        val vx = 0.5
        val vy = planeHeight * 0.5
        val k = viewDistance / (viewDistance - pz)
        return Pair(vx + (px - vx) * k, vy + (y - vy) * k)
    }

    /** Duo brightness for the cover display (moving panel, outer face). */
    fun coverBrightness(angleRad: Double, d: Double): Double =
        1.0 - 0.55 * sin(angleRad) * (0.4 + 0.6 * d)

    /** Duo brightness for the moving half of the inner display. */
    fun innerMovingBrightness(angleRad: Double, d: Double): Double =
        1.0 - 0.75 * sin(angleRad) * (0.35 + 0.65 * d)

    /** Duo brightness for the stationary half of the inner display. */
    fun innerStationaryBrightness(angleRad: Double): Double = 1.0 - 0.28 * sin(angleRad)

    fun isInnerDisplay(naturalWidth: Int, naturalHeight: Int): Boolean {
        if (naturalWidth <= 0 || naturalHeight <= 0) return false
        val aspect = max(naturalWidth, naturalHeight).toFloat() / min(naturalWidth, naturalHeight)
        return aspect <= INNER_MAX_ASPECT
    }

    /** Natural (rotation 0) size of a surface reported at [rotation] (Surface.ROTATION_*). */
    fun naturalSize(width: Int, height: Int, rotation: Int): Pair<Int, Int> =
        if (rotation == 1 || rotation == 3) Pair(height, width) else Pair(width, height)

    /**
     * Converts a surface pixel to natural-orientation pixel coordinates. ROTATION_90 means the
     * device is turned counter-clockwise, so the natural left edge sits at the bottom.
     */
    fun toNatural(x: Float, y: Float, width: Float, height: Float, rotation: Int): Pair<Float, Float> =
        when (rotation) {
            1 -> Pair(height - y, x)
            2 -> Pair(width - x, height - y)
            3 -> Pair(y, width - x)
            else -> Pair(x, y)
        }

    /** Plane height in panel widths: tall enough for both the cover and the inner display. */
    fun planeHeight(innerW: Int, innerH: Int, coverW: Int, coverH: Int): Float {
        val inner = innerH.toFloat() / (innerW.toFloat() * 0.5f)
        val cover = coverH.toFloat() / coverW.toFloat()
        return max(inner, cover)
    }
}
