package com.lumifold.wallpaper.render

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

    /** Peak Duo Gaussian sigma at the free edge, as a fraction of one panel width. */
    const val DUO_SIGMA = 0.09

    /** Inner displays are near-square; covers are tall. */
    private const val INNER_MAX_ASPECT = 1.4f

    /**
     * Duo transition strength, after Apple's model: 0 at the display's resting angle (closed for
     * the cover, flat for the inner display), rising to 1 three quarters of the way to 90 degrees.
     */
    fun duoMotion(angleRad: Double, inner: Boolean): Double {
        val a = angleRad.coerceIn(0.0, PI)
        val progress = if (inner) (PI - a) / (PI / 2) else a / (PI / 2)
        val t = (progress.coerceIn(0.0, 1.0) / 0.75).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** Duo blur: `DUO_SIGMA * motion * d^1.5`, d from 0 at the hinge to 1 at the free edge. */
    fun duoSigma(motion: Double, distanceFromHinge: Double): Double =
        DUO_SIGMA * motion * Math.pow(distanceFromHinge.coerceIn(0.0, 1.0), 1.5)

    /** Duo brightness on a moving panel: falls to black at the free edge at full motion. */
    fun duoBrightness(motion: Double, distanceFromHinge: Double, strength: Double = 1.0): Double {
        val g = ((distanceFromHinge - 0.2) / 0.8).coerceIn(0.0, 1.0)
        return (1.0 - 1.3 * motion * Math.pow(g, 1.35) * strength).coerceIn(0.0, 1.0)
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
        viewDistance: Double = VIEW_DISTANCE,
        eyeX: Double = 0.5
    ): Pair<Double, Double> {
        val px = u * kotlin.math.cos(angleRad)
        val pz = u * sin(angleRad)
        val vx = eyeX
        val vy = planeHeight * 0.5
        val k = viewDistance / (viewDistance - pz)
        return Pair(vx + (px - vx) * k, vy + (y - vy) * k)
    }

    /**
     * Duo Frost on the inner display. Both halves tilt toward the viewer about the crease by half
     * the remaining opening, and each pixel shows the picture behind it along the ray from an eye
     * above the crease. The map is scaled so each outer edge stays on the picture's edge, so no
     * ray misses the picture. [x] runs from -1 (left edge) through 0 (crease) to 1 (right edge).
     */
    fun projectInnerHalf(
        x: Double,
        y: Double,
        angleRad: Double,
        planeHeight: Double,
        viewDistance: Double = VIEW_DISTANCE
    ): Pair<Double, Double> {
        val tilt = sin((PI - angleRad.coerceIn(0.0, PI)) * 0.5)
        val u = kotlin.math.abs(x).coerceIn(0.0, 1.0)
        // Projection factor at this point over the factor at the outer edge.
        val scale = (viewDistance - tilt) / (viewDistance - u * tilt)
        val vy = planeHeight * 0.5
        return Pair(x * scale, vy + (y - vy) * scale)
    }

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
