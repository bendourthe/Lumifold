package com.ambientfold.wallpaper.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.ambientfold.wallpaper.model.DuoBlurStateManager
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.render.DynamicMotionEngine
import kotlin.math.abs
import kotlin.math.cos

/**
 * Physically Accurate Two-Leaf Book Foldable Mockup View.
 * Accurately models the hardware topology of a book-style foldable (Pixel Fold):
 * 1. Physical Hardware Topology:
 *    - Left Leaf: Chassis back on exterior, left half of inner flexible display on interior.
 *    - Right Leaf: Outer Cover Display on exterior, right half of inner flexible display on interior.
 *    - Central Hinge Spine: Left edge for the cover screen; center crease (x = 50%) for the inner display.
 * 2. Phase 1 (0° to 85° Cover Frosting):
 *    - As the book cracks open, the Outer Cover Display remains powered on and visible on the exterior right leaf.
 *    - Directional progressive frost sweeps from the left hinge edge across the screen with perspective tilt and darkening.
 * 3. Phase 2 (85° to 95° Handoff):
 *    - Cover display fades to black as the inner flexible display illuminates at matching 100% peak frost in a 3D V-shape.
 * 4. Phase 3 (95° to 180° Inner Defrosting):
 *    - Both leaves open flat into the square display. Frost clears radiating outward from the central crease (x = 50%)
 *      until snapping razor-sharp at 180°.
 */
class FoldablePhoneMockupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var currentAngle: Float = 140f
        private set

    private var foldProgress: Float = 0.0f
    private var isInnerPhase: Boolean = true

    private var activeWallpaper: WallpaperItem? = null
    private var wallpaperBitmap: Bitmap? = null
    private var loadedResId: Int = 0
    private var openingEffect: String = WallpaperRepository.DEFAULT_OPENING_EFFECT
    private var isDynamicMotionEnabled: Boolean = true

    private val motionEngine = DynamicMotionEngine()
    private val camera = Camera()
    private val matrix3D = Matrix()

    // Chassis finishes (matte dark titanium and polished chamfer)
    private val chassisExteriorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#090D16")
    }
    private val chassisInteriorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#070A10")
    }
    private val chamferStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#283548")
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    private val hingeSpinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#151D2C")
    }
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val leftLeafPath = Path()
    private val rightLeafPath = Path()
    private val leftLeafRect = RectF()
    private val rightLeafRect = RectF()

    private var loopAnimator: ValueAnimator? = null
    private var isUserScrubbing = false

    var onAngleChangedListener: ((angle: Float, phaseLabel: String, frostProgress: Float) -> Unit)? = null

    init {
        startCinematicLoop()
    }

    /**
     * Continuous 8.4-second realistic book unfolding loop:
     * 0.00 -> 0.16: Resting closed (0° Outer Cover Display active)
     * 0.16 -> 0.54: Opening from 0° -> 180° through Cover Frosting, Handoff, and Inner Defrosting
     * 0.54 -> 0.76: Resting open (180° Flat Square Display with 60fps dynamic motion)
     * 0.76 -> 1.00: Smoothly folding back to 0°
     */
    fun startCinematicLoop() {
        isUserScrubbing = false
        loopAnimator?.cancel()
        loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 8400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                if (isUserScrubbing) return@addUpdateListener
                val p = anim.animatedValue as Float
                when {
                    p < 0.16f -> {
                        setAngle(0f)
                    }
                    p in 0.16f..0.54f -> {
                        val sub = (p - 0.16f) / 0.38f
                        val curved = (1.0f - cos(sub * Math.PI.toFloat())) * 0.5f
                        setAngle(curved * 180f)
                    }
                    p in 0.54f..0.76f -> {
                        setAngle(180f)
                    }
                    else -> {
                        val sub = (p - 0.76f) / 0.24f
                        val curved = (1.0f - cos(sub * Math.PI.toFloat())) * 0.5f
                        setAngle(180f - (curved * 180f))
                    }
                }
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    fun setAngle(angleDegrees: Float) {
        currentAngle = angleDegrees.coerceIn(0f, 180f)
        isInnerPhase = currentAngle >= 88f

        // Two-phase handoff progress:
        // Outer Cover (0° to 90°): 0.0 -> 1.0 (scales up to peak frost)
        // Inner Display (90° to 180°): 1.0 -> 0.0 (starts at 100% peak frost and unbends to 0.0)
        foldProgress = DuoBlurStateManager.getCurrentFrostProgress(currentAngle, isInnerPhase)

        val phaseLabel = DuoBlurStateManager.getPhaseLabel(currentAngle)
        onAngleChangedListener?.invoke(currentAngle, phaseLabel, foldProgress)
        invalidate()
    }

    fun pauseAutoLoop() {
        isUserScrubbing = true
        loopAnimator?.cancel()
    }

    fun setWallpaper(wallpaper: WallpaperItem, isDynamic: Boolean) {
        this.activeWallpaper = wallpaper
        this.isDynamicMotionEnabled = isDynamic
        loadBitmapIfNeeded(wallpaper.drawableResId)
        invalidate()
    }

    fun setOpeningEffect(effect: String) {
        this.openingEffect = effect
        invalidate()
    }

    fun setMotionEnabled(enabled: Boolean) {
        this.isDynamicMotionEnabled = enabled
        invalidate()
    }

    private fun loadBitmapIfNeeded(drawableResId: Int) {
        if (loadedResId == drawableResId && wallpaperBitmap != null && !wallpaperBitmap!!.isRecycled) return

        try {
            wallpaperBitmap?.recycle()
            wallpaperBitmap = null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, drawableResId, options)

            var sampleSize = 1
            while (options.outWidth / sampleSize > 1600 || options.outHeight / sampleSize > 1600) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            wallpaperBitmap = BitmapFactory.decodeResource(resources, drawableResId, decodeOptions)
            loadedResId = drawableResId
        } catch (e: Throwable) {
            // Fallback
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (loopAnimator?.isRunning != true && !isUserScrubbing) {
            startCinematicLoop()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loopAnimator?.cancel()
        wallpaperBitmap?.recycle()
        wallpaperBitmap = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * 0.5f
        val cy = h * 0.50f

        // Physical proportions:
        // Height ~ 74% of view height
        // Each leaf width: height * 0.54 (when both leaves are open flat, width = 1.08 * height -> nearly square)
        val leafH = (h * 0.74f).coerceAtLeast(180f)
        val leafW = leafH * 0.54f
        val cornerRadius = 18f

        // Ambient floor shadow under the chassis
        val shadowW = (leafW * 2f) * (0.55f + (currentAngle / 180f) * 0.45f) + 40f
        shadowPaint.shader = RadialGradient(
            cx, cy + (leafH * 0.5f) + 12f, shadowW * 0.55f,
            intArrayOf(Color.argb(125, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0.0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(
            cx - shadowW * 0.5f,
            cy + (leafH * 0.5f) - 6f,
            cx + shadowW * 0.5f,
            cy + (leafH * 0.5f) + 26f,
            shadowPaint
        )

        val bmp = wallpaperBitmap
        val item = activeWallpaper

        if (!isInnerPhase) {
            // --- TOPOLOGY 1: EXTERIOR COVER DISPLAY (0° to 88°) ---
            // Outer Cover Display sits on the exterior face of the Right Leaf.
            // Hinge spine is along its left edge.
            drawExteriorCoverLeaf(canvas, cx, cy, leafW, leafH, cornerRadius, bmp, item)
        } else {
            // --- TOPOLOGY 2: INTERIOR MAIN FLEXIBLE DISPLAY (88° to 180°) ---
            // Continuous flexible display spans across both interior leaves.
            // Physical hinge crease runs vertically down the exact center (x = 50%).
            drawInteriorFlexibleLeaves(canvas, cx, cy, leafW, leafH, cornerRadius, bmp, item)
        }
    }

    /**
     * Accurately renders the Exterior Cover Display on the front leaf.
     * The physical hinge spine is on the left edge (x = 0).
     */
    private fun drawExteriorCoverLeaf(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        leafW: Float,
        leafH: Float,
        cornerRadius: Float,
        bmp: Bitmap?,
        item: WallpaperItem?
    ) {
        canvas.save()

        // 3D perspective tilt as the book cracks open:
        // Hinge is on the left edge: Leaf rotates around Y-axis anchored at the spine
        val tiltAngle = (currentAngle / 88f) * 38f
        val spineX = cx - (leafW * 0.5f)

        camera.save()
        camera.rotateY(-tiltAngle)
        camera.getMatrix(matrix3D)
        camera.restore()
        matrix3D.preTranslate(-spineX, -cy)
        matrix3D.postTranslate(spineX, cy)
        canvas.concat(matrix3D)

        val left = cx - (leafW * 0.5f)
        val top = cy - (leafH * 0.5f)
        val right = cx + (leafW * 0.5f)
        val bottom = cy + (leafH * 0.5f)
        val leafRect = RectF(left, top, right, bottom)

        // Exterior chassis back and chamfered perimeter
        canvas.drawRoundRect(leafRect, cornerRadius, cornerRadius, chassisExteriorPaint)
        canvas.drawRoundRect(leafRect, cornerRadius, cornerRadius, chamferStrokePaint)

        // Exterior display bezel
        val inset = 7f
        val screenRect = RectF(left + inset, top + inset, right - inset, bottom - inset)
        val screenRadius = cornerRadius - 4f

        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(screenRect, screenRadius, screenRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        // Render wallpaper with dynamic motion
        if (bmp != null && item != null) {
            motionEngine.render(
                canvas = canvas,
                bitmap = bmp,
                width = screenRect.width().toInt(),
                height = screenRect.height().toInt(),
                wallpaper = item,
                isDynamic = isDynamicMotionEnabled,
                blurProgress = foldProgress
            )
        } else {
            canvas.drawColor(Color.parseColor("#0C111C"))
        }

        // Phase 1 DuoBlur: Directional progressive frost sweeps from left hinge edge (x = 0) towards right
        if (foldProgress > 0.01f) {
            val frostAlpha = (foldProgress * 215f).toInt().coerceIn(0, 255)
            val darkeningAlpha = (foldProgress * 150f).toInt().coerceIn(0, 255)

            // Frost gradient: peak density at hinge (left = 0) fading toward outer edge
            val frostGradient = LinearGradient(
                screenRect.left, 0f, screenRect.right, 0f,
                intArrayOf(
                    Color.argb(frostAlpha, 235, 242, 255),
                    Color.argb((frostAlpha * 0.65f).toInt(), 220, 232, 248),
                    Color.argb((frostAlpha * 0.25f).toInt(), 210, 225, 242)
                ),
                floatArrayOf(0.0f, 0.45f, 1.0f),
                Shader.TileMode.CLAMP
            )
            effectPaint.shader = frostGradient
            canvas.drawRect(screenRect, effectPaint)

            // Luminance drop / darkening attenuation as surface angles away
            effectPaint.shader = null
            effectPaint.color = Color.argb(darkeningAlpha, 0, 0, 0)
            canvas.drawRect(screenRect, effectPaint)
        }

        // Cover selfie camera punch hole
        val camPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#04070D") }
        canvas.drawCircle(cx, screenRect.top + 13f, 4.2f, camPaint)

        canvas.restore()
        canvas.restore()
    }

    /**
     * Accurately renders the Interior Main Flexible Display across both leaves.
     * The physical hinge crease runs vertically down the exact center (x = 50%).
     */
    private fun drawInteriorFlexibleLeaves(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        leafW: Float,
        leafH: Float,
        cornerRadius: Float,
        bmp: Bitmap?,
        item: WallpaperItem?
    ) {
        // Book V-fold articulation:
        // At 90° handoff: leaves form a 42° V-shape open book
        // At 180° flat: leaves form 0° flat tablet
        val openFactor = ((currentAngle - 88f) / 92f).coerceIn(0f, 1f)
        val vAngle = (1.0f - openFactor) * 42f

        val top = cy - (leafH * 0.5f)
        val bottom = cy + (leafH * 0.5f)

        leftLeafRect.set(cx - leafW, top, cx, bottom)
        rightLeafRect.set(cx, top, cx + leafW, bottom)

        val totalInnerW = (leafW * 2f).toInt()
        val totalInnerH = leafH.toInt()

        // 1. LEFT LEAF (Interior Left Page)
        canvas.save()
        camera.save()
        camera.rotateY(-vAngle)
        camera.getMatrix(matrix3D)
        camera.restore()
        matrix3D.preTranslate(-cx, -cy)
        matrix3D.postTranslate(cx, cy)
        canvas.concat(matrix3D)

        leftLeafPath.reset()
        leftLeafPath.addRoundRect(
            leftLeafRect,
            floatArrayOf(cornerRadius, cornerRadius, 0f, 0f, 0f, 0f, cornerRadius, cornerRadius),
            Path.Direction.CW
        )

        canvas.save()
        canvas.clipPath(leftLeafPath)
        canvas.drawRect(leftLeafRect, chassisInteriorPaint)

        if (bmp != null && item != null) {
            canvas.save()
            canvas.translate(cx - leafW, top)
            motionEngine.render(
                canvas = canvas,
                bitmap = bmp,
                width = totalInnerW,
                height = totalInnerH,
                wallpaper = item,
                isDynamic = isDynamicMotionEnabled,
                blurProgress = foldProgress
            )
            canvas.restore()
        }

        drawInteriorFrostOverlay(canvas, leftLeafRect, isLeftLeaf = true)
        canvas.drawPath(leftLeafPath, chamferStrokePaint)
        canvas.restore()
        canvas.restore()

        // 2. RIGHT LEAF (Interior Right Page)
        canvas.save()
        camera.save()
        camera.rotateY(vAngle)
        camera.getMatrix(matrix3D)
        camera.restore()
        matrix3D.preTranslate(-cx, -cy)
        matrix3D.postTranslate(cx, cy)
        canvas.concat(matrix3D)

        rightLeafPath.reset()
        rightLeafPath.addRoundRect(
            rightLeafRect,
            floatArrayOf(0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f),
            Path.Direction.CW
        )

        canvas.save()
        canvas.clipPath(rightLeafPath)
        canvas.drawRect(rightLeafRect, chassisInteriorPaint)

        if (bmp != null && item != null) {
            canvas.save()
            canvas.translate(cx - leafW, top)
            motionEngine.render(
                canvas = canvas,
                bitmap = bmp,
                width = totalInnerW,
                height = totalInnerH,
                wallpaper = item,
                isDynamic = isDynamicMotionEnabled,
                blurProgress = foldProgress
            )
            canvas.restore()
        }

        drawInteriorFrostOverlay(canvas, rightLeafRect, isLeftLeaf = false)
        canvas.drawPath(rightLeafPath, chamferStrokePaint)
        canvas.restore()
        canvas.restore()

        // 3. CENTRAL HINGE CREASE (Exact Center x = 50%)
        val spineW = 5f
        val spineRect = RectF(cx - (spineW * 0.5f), top, cx + (spineW * 0.5f), bottom)
        canvas.drawRect(spineRect, hingeSpinePaint)

        // Illuminated crease seam (optical glint along hinge axis)
        val creaseAlpha = ((0.35f + foldProgress * 0.65f) * 255f).toInt().coerceIn(80, 255)
        val creaseWidth = 3f + (foldProgress * 6f)
        val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(creaseAlpha, 255, 255, 255)
            strokeWidth = creaseWidth
        }
        canvas.drawLine(cx, top + 6f, cx, bottom - 6f, beamPaint)

        // Crease cyan/blue glow bloom
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((creaseAlpha * 0.55f).toInt(), 99, 102, 241)
            strokeWidth = creaseWidth * 2.6f
        }
        canvas.drawLine(cx, top + 6f, cx, bottom - 6f, glowPaint)
    }

    /**
     * Phase 3: Frost clears radiating outward from the center crease (x = 50%).
     * At 90° handoff: 100% peak frost everywhere.
     * At 180° flat: 0% blur / crystal clear.
     */
    private fun drawInteriorFrostOverlay(canvas: Canvas, leafRect: RectF, isLeftLeaf: Boolean) {
        if (foldProgress <= 0.01f) return

        val frostAlpha = (foldProgress * 210f).toInt().coerceIn(0, 255)
        val darkAlpha = (foldProgress * 125f).toInt().coerceIn(0, 255)

        // Gradient anchored at center crease (x = 50%)
        val gradient = if (isLeftLeaf) {
            LinearGradient(
                leafRect.left, 0f, leafRect.right, 0f,
                intArrayOf(
                    Color.argb(frostAlpha, 235, 242, 255),
                    Color.argb((frostAlpha * 0.55f).toInt(), 225, 235, 250),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.0f, 0.65f, 1.0f),
                Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                leafRect.left, 0f, leafRect.right, 0f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb((frostAlpha * 0.55f).toInt(), 225, 235, 250),
                    Color.argb(frostAlpha, 235, 242, 255)
                ),
                floatArrayOf(0.0f, 0.35f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        effectPaint.shader = gradient
        canvas.drawRect(leafRect, effectPaint)

        // Luminance darkening attenuation
        effectPaint.shader = null
        effectPaint.color = Color.argb(darkAlpha, 0, 0, 0)
        canvas.drawRect(leafRect, effectPaint)
    }
}
