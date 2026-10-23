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
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.render.DynamicMotionEngine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Authentic 3D Foldable Phone Mockup View.
 * Accurately models a book/passport-style foldable (Google Pixel Fold):
 * 1. Closed state (0°): A standard tall phone with front cover screen.
 * 2. Phase 1 Cover Frosting (0° to 85°): Opens like a passport; directional blur sweeps from hinge,
 *    perspective vertical compression, and luminance darkening.
 * 3. Phase 2 Concurrent Handoff (85° to 95°): Hands off to the dual inner display in a 3D V-shape
 *    (matching the Gemini hero design).
 * 4. Phase 3 Inner Defrosting (95° to 180°): Book panels unbend into the square tablet; frost recedes
 *    toward the illuminated crease and snaps crystal-sharp at 180°.
 * 5. Full 60fps dynamic ambient motion rendering in all phases.
 */
class FoldablePhoneMockupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 0° (fully closed) to 180° (fully open square display)
    var currentAngle: Float = 140f
        private set

    // Normalized blur/frost: 0.0 (sharp) to 1.0 (peak frost)
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

    // Chassis styling matching premium dark titanium foldable
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B0F19")
    }
    private val rimStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38445A")
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }
    private val hingeSpinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#151D2A")
    }
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        textSize = 28f
        isFakeBoldText = true
    }

    private val leftPanelPath = Path()
    private val rightPanelPath = Path()
    private val leftPanelRect = RectF()
    private val rightPanelRect = RectF()

    private var loopAnimator: ValueAnimator? = null
    private var isUserScrubbing = false

    var onAngleChangedListener: ((angle: Float, phaseText: String) -> Unit)? = null

    init {
        startCinematicLoop()
    }

    /**
     * Continuous 8-second realistic unfolding loop:
     * 0.0 -> 0.18: Closed cover display (0° to 10°)
     * 0.18 -> 0.38: Phase 1 & 2 Cover Frosting & Handoff (10° to 95°)
     * 0.38 -> 0.58: Phase 3 Inner Defrosting to Flat Square (95° to 180°)
     * 0.58 -> 0.78: Resting in fully open 180° tablet state with dynamic motion
     * 0.78 -> 1.00: Smoothly folding back to closed state
     */
    fun startCinematicLoop() {
        loopAnimator?.cancel()
        loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 8400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                if (isUserScrubbing) return@addUpdateListener
                val p = anim.animatedValue as Float
                when {
                    // Closed cover state
                    p < 0.16f -> {
                        setAngle(0f)
                    }
                    // Opening from 0° to 180°
                    p in 0.16f..0.54f -> {
                        val sub = (p - 0.16f) / 0.38f
                        // Ease-in-out curve
                        val curved = (1.0f - cos(sub * Math.PI.toFloat())) * 0.5f
                        setAngle(curved * 180f)
                    }
                    // Resting in fully unfolded 180° state
                    p in 0.54f..0.76f -> {
                        setAngle(180f)
                    }
                    // Folding back to 0°
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
        isInnerPhase = currentAngle >= 85f

        // Two-phase progress calculation:
        // 0° to 85°: Cover screen frosting (0.0 -> 1.0)
        // 85° to 95°: Peak handoff (1.0)
        // 95° to 180°: Inner screen defrosting (1.0 -> 0.0)
        foldProgress = if (!isInnerPhase) {
            (currentAngle / 85f).coerceIn(0f, 1f)
        } else {
            val dist = abs(currentAngle - 180f)
            (dist / 95f).coerceIn(0f, 1f)
        }

        val phaseText = when {
            currentAngle < 15f -> "Cover Screen (0° Closed)"
            currentAngle < 85f -> "Phase 1: Cover Frosting (${currentAngle.toInt()}°)"
            currentAngle <= 95f -> "Phase 2: Handoff Peak (${currentAngle.toInt()}°)"
            currentAngle < 175f -> "Phase 3: Inner Defrosting (${currentAngle.toInt()}°)"
            else -> "Inner Screen (180° Flat Square)"
        }
        onAngleChangedListener?.invoke(currentAngle, phaseText)
        invalidate()
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
        if (loopAnimator?.isRunning != true) {
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
        val cy = h * 0.52f

        // Foldable device proportions:
        // Height ~ 78% of card height
        // Each panel width: height * 0.55 (so unfolded 2 panels form ~1.1:1 nearly square screen)
        val phoneH = (h * 0.74f).coerceAtLeast(180f)
        val panelW = phoneH * 0.54f
        val cornerRadius = 18f

        // Soft floor ambient shadow
        val shadowW = (panelW * 2f) * (0.55f + (currentAngle / 180f) * 0.45f) + 40f
        shadowPaint.shader = RadialGradient(
            cx, cy + (phoneH * 0.5f) + 12f, shadowW * 0.55f,
            intArrayOf(Color.argb(120, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0.0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(
            cx - shadowW * 0.5f,
            cy + (phoneH * 0.5f) - 6f,
            cx + shadowW * 0.5f,
            cy + (phoneH * 0.5f) + 26f,
            shadowPaint
        )

        val bmp = wallpaperBitmap
        val item = activeWallpaper

        if (!isInnerPhase) {
            // --- PHASE 1: COVER SCREEN (Passport Front) ---
            drawCoverPhaseDisplay(canvas, cx, cy, panelW, phoneH, cornerRadius, bmp, item)
        } else {
            // --- PHASE 2 & 3: INNER DISPLAY (3D Book V-Fold to Square) ---
            drawInnerBookDisplay(canvas, cx, cy, panelW, phoneH, cornerRadius, bmp, item)
        }

        // Top Status Badge
        drawStatusOverlay(canvas, w)
    }

    private fun drawCoverPhaseDisplay(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        panelW: Float,
        phoneH: Float,
        cornerRadius: Float,
        bmp: Bitmap?,
        item: WallpaperItem?
    ) {
        canvas.save()

        // 3D perspective rotation: as it cracks open (0° to 85°), the cover screen tilts away
        val tiltAngle = (currentAngle / 85f) * 35f
        camera.save()
        camera.rotateY(-tiltAngle)
        camera.getMatrix(matrix3D)
        camera.restore()
        matrix3D.preTranslate(-cx, -cy)
        matrix3D.postTranslate(cx, cy)
        canvas.concat(matrix3D)

        val left = cx - (panelW * 0.5f)
        val top = cy - (phoneH * 0.5f)
        val right = cx + (panelW * 0.5f)
        val bottom = cy + (phoneH * 0.5f)
        val outerRect = RectF(left, top, right, bottom)

        // Chassis & metallic bezel
        canvas.drawRoundRect(outerRect, cornerRadius, cornerRadius, chassisPaint)
        canvas.drawRoundRect(outerRect, cornerRadius, cornerRadius, rimStrokePaint)

        // Screen bezel inset
        val inset = 7f
        val screenRect = RectF(left + inset, top + inset, right - inset, bottom - inset)
        val screenRadius = cornerRadius - 4f

        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(screenRect, screenRadius, screenRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

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
            canvas.drawColor(Color.parseColor("#151D2A"))
        }

        // DuoBlur Cover Frosting: Sweeps inward from the left hinge edge toward outer edge
        if (foldProgress > 0.01f) {
            val frostAlpha = (foldProgress * 210f).toInt().coerceIn(0, 255)
            val darkeningAlpha = (foldProgress * 140f).toInt().coerceIn(0, 255)

            // Frost gradient starting strong at hinge (left = 0)
            val frostGradient = LinearGradient(
                screenRect.left, 0f, screenRect.right, 0f,
                intArrayOf(
                    Color.argb(frostAlpha, 230, 238, 252),
                    Color.argb((frostAlpha * 0.65f).toInt(), 220, 230, 245),
                    Color.argb((frostAlpha * 0.20f).toInt(), 210, 225, 240)
                ),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            effectPaint.shader = frostGradient
            canvas.drawRect(screenRect, effectPaint)

            // Luminance darkening attenuation
            effectPaint.shader = null
            effectPaint.color = Color.argb(darkeningAlpha, 0, 0, 0)
            canvas.drawRect(screenRect, effectPaint)
        }

        // Camera punch hole
        val camPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#05080E") }
        canvas.drawCircle(cx, screenRect.top + 13f, 4.2f, camPaint)

        canvas.restore()
        canvas.restore()
    }

    private fun drawInnerBookDisplay(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        panelW: Float,
        phoneH: Float,
        cornerRadius: Float,
        bmp: Bitmap?,
        item: WallpaperItem?
    ) {
        // Book V-fold articulation:
        // At 90°: panels form a 3D book V-shape (angle = 45° each side)
        // At 180°: panels flatten completely (0° each side)
        val openFactor = ((currentAngle - 85f) / 95f).coerceIn(0f, 1f)
        val vAngle = (1.0f - openFactor) * 44f

        val top = cy - (phoneH * 0.5f)
        val bottom = cy + (phoneH * 0.5f)

        leftPanelRect.set(cx - panelW, top, cx, bottom)
        rightPanelRect.set(cx, top, cx + panelW, bottom)

        val totalInnerW = (panelW * 2f).toInt()
        val totalInnerH = phoneH.toInt()

        // 1. LEFT PANEL (Book Left Page)
        canvas.save()
        camera.save()
        camera.rotateY(-vAngle)
        camera.getMatrix(matrix3D)
        camera.restore()
        matrix3D.preTranslate(-cx, -cy)
        matrix3D.postTranslate(cx, cy)
        canvas.concat(matrix3D)

        leftPanelPath.reset()
        leftPanelPath.addRoundRect(
            leftPanelRect,
            floatArrayOf(cornerRadius, cornerRadius, 0f, 0f, 0f, 0f, cornerRadius, cornerRadius),
            Path.Direction.CW
        )

        canvas.save()
        canvas.clipPath(leftPanelPath)
        canvas.drawRect(leftPanelRect, chassisPaint)

        if (bmp != null && item != null) {
            canvas.save()
            canvas.translate(cx - panelW, top)
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

        drawInnerDefrostOverlay(canvas, leftPanelRect, true)
        canvas.drawPath(leftPanelPath, rimStrokePaint)
        canvas.restore()
        canvas.restore()

        // 2. RIGHT PANEL (Book Right Page)
        canvas.save()
        camera.save()
        camera.rotateY(vAngle)
        camera.getMatrix(matrix3D)
        camera.restore()
        matrix3D.preTranslate(-cx, -cy)
        matrix3D.postTranslate(cx, cy)
        canvas.concat(matrix3D)

        rightPanelPath.reset()
        rightPanelPath.addRoundRect(
            rightPanelRect,
            floatArrayOf(0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f),
            Path.Direction.CW
        )

        canvas.save()
        canvas.clipPath(rightPanelPath)
        canvas.drawRect(rightPanelRect, chassisPaint)

        if (bmp != null && item != null) {
            canvas.save()
            canvas.translate(cx - panelW, top)
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

        drawInnerDefrostOverlay(canvas, rightPanelRect, false)
        canvas.drawPath(rightPanelPath, rimStrokePaint)
        canvas.restore()
        canvas.restore()

        // 3. CENTRAL HINGE CREASE SPINE & ILLUMINATED LIGHT SEAM (matching Gemini hero image)
        val spineW = 5f
        val spineRect = RectF(cx - (spineW * 0.5f), top, cx + (spineW * 0.5f), bottom)
        canvas.drawRect(spineRect, hingeSpinePaint)

        // Illuminated crease seam
        val creaseAlpha = ((0.35f + foldProgress * 0.65f) * 245f).toInt().coerceIn(70, 255)
        val creaseWidth = 3f + (foldProgress * 6f)
        val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(creaseAlpha, 255, 255, 255)
            strokeWidth = creaseWidth
        }
        canvas.drawLine(cx, top + 6f, cx, bottom - 6f, beamPaint)

        // Crease cyan/violet glow bloom
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((creaseAlpha * 0.5f).toInt(), 96, 165, 250)
            strokeWidth = creaseWidth * 2.8f
        }
        canvas.drawLine(cx, top + 6f, cx, bottom - 6f, glowPaint)
    }

    private fun drawInnerDefrostOverlay(canvas: Canvas, panelRect: RectF, isLeft: Boolean) {
        if (foldProgress <= 0.01f) return

        // Phase 3: Frost gradient recedes from the outer edges toward the center crease
        val frostAlpha = (foldProgress * 205f).toInt().coerceIn(0, 255)
        val darkAlpha = (foldProgress * 110f).toInt().coerceIn(0, 255)

        val gradient = if (isLeft) {
            LinearGradient(
                panelRect.left, 0f, panelRect.right, 0f,
                intArrayOf(Color.argb(frostAlpha, 235, 242, 255), Color.argb((frostAlpha * 0.5f).toInt(), 225, 235, 250), Color.TRANSPARENT),
                floatArrayOf(0.0f, 0.65f, 1.0f),
                Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                panelRect.left, 0f, panelRect.right, 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb((frostAlpha * 0.5f).toInt(), 225, 235, 250), Color.argb(frostAlpha, 235, 242, 255)),
                floatArrayOf(0.0f, 0.35f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        effectPaint.shader = gradient
        canvas.drawRect(panelRect, effectPaint)

        // Darkening attenuation as angle deviates from flat
        effectPaint.shader = null
        effectPaint.color = Color.argb(darkAlpha, 0, 0, 0)
        canvas.drawRect(panelRect, effectPaint)
    }

    private fun drawStatusOverlay(canvas: Canvas, w: Float) {
        val label = when {
            currentAngle < 15f -> "Cover Screen (0° Closed)"
            currentAngle < 85f -> "Phase 1: Cover Frosting (${currentAngle.toInt()}°)"
            currentAngle <= 95f -> "Phase 2: Handoff (${currentAngle.toInt()}°)"
            currentAngle < 175f -> "Phase 3: Inner Defrosting (${currentAngle.toInt()}°)"
            else -> "Inner Screen (180° Flat Square)"
        }

        val textW = textPaint.measureText(label)
        val padH = 24f
        val padV = 14f
        val badgeW = textW + (padH * 2f)
        val badgeH = 50f
        val badgeLeft = (w - badgeW) * 0.5f
        val badgeTop = 16f
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH)

        badgePaint.color = Color.argb(200, 15, 23, 42)
        canvas.drawRoundRect(badgeRect, 25f, 25f, badgePaint)

        rimStrokePaint.color = Color.argb(120, 99, 102, 241)
        canvas.drawRoundRect(badgeRect, 25f, 25f, rimStrokePaint)

        canvas.drawText(label, badgeLeft + padH, badgeTop + 33f, textPaint)
    }
}
