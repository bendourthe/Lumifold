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
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.render.DynamicMotionEngine
import kotlin.math.abs

/**
 * Continuous cinematic 3D foldable phone mockup view.
 * Loops smoothly through:
 * 1. Folded state (outer screen viewing shared wallpaper base)
 * 2. 3D perspective unfolding (0° to 180°) showcasing the selected opening effect (iPhone Duo progressive blur, crease bloom, etc.)
 * 3. Opened state showing the full inner display with dynamic ambient motion or static mode
 * 4. Smooth folding transition back to closed state
 */
class FoldablePhoneMockupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Current angle: 0f (closed) to 180f (fully open)
    var currentAngle: Float = 180f
        private set

    // 0.0f = fully open, 1.0f = fully closed
    private var blurProgress: Float = 0.0f

    private var activeWallpaper: WallpaperItem? = null
    private var wallpaperBitmap: Bitmap? = null
    private var loadedResId: Int = 0
    private var openingEffect: String = WallpaperRepository.DEFAULT_OPENING_EFFECT
    private var isDynamicMotionEnabled: Boolean = true

    private val motionEngine = DynamicMotionEngine()
    private val camera = Camera()
    private val matrix3D = Matrix()

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
    }
    private val bezelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val hingeSpinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
    }
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val leftPanelPath = Path()
    private val rightPanelPath = Path()
    private val leftPanelRect = RectF()
    private val rightPanelRect = RectF()

    private var loopAnimator: ValueAnimator? = null

    init {
        startCinematicLoop()
    }

    /**
     * Continuous 4-phase cinematic loop:
     * 0.0 -> 0.2: Resting in folded state (outer cover screen)
     * 0.2 -> 0.5: Unfolding in 3D perspective to 180°
     * 0.5 -> 0.8: Resting in wide unfolded state (full inner display)
     * 0.8 -> 1.0: Folding back to closed state
     */
    private fun startCinematicLoop() {
        loopAnimator?.cancel()
        loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 7200L // 7.2 second continuous loop
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                when {
                    // Phase 1: Folded cover screen (0° to 10°)
                    progress < 0.20f -> {
                        currentAngle = 10f
                        blurProgress = 1.0f
                    }
                    // Phase 2: Unfolding (10° to 180°)
                    progress in 0.20f..0.50f -> {
                        val subProgress = (progress - 0.20f) / 0.30f
                        currentAngle = 10f + (subProgress * 170f)
                        blurProgress = 1.0f - subProgress
                    }
                    // Phase 3: Opened full inner screen (180°)
                    progress in 0.50f..0.80f -> {
                        currentAngle = 180f
                        blurProgress = 0.0f
                    }
                    // Phase 4: Folding back (180° to 10°)
                    else -> {
                        val subProgress = (progress - 0.80f) / 0.20f
                        currentAngle = 180f - (subProgress * 170f)
                        blurProgress = subProgress
                    }
                }
                postInvalidateOnAnimation()
            }
            start()
        }
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

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
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
        val cy = h * 0.5f

        // Mockup phone dimensions
        val phoneH = (h * 0.82f).coerceAtLeast(180f)
        val panelW = (phoneH * 0.52f).coerceAtLeast(90f) // Each foldable panel aspect ratio ~1:1.9
        val cornerRadius = 18f

        // Ambient floor shadow
        val shadowW = (panelW * 2f) * (0.5f + (currentAngle / 180f) * 0.5f) + 30f
        shadowPaint.shader = RadialGradient(
            cx, cy + (phoneH * 0.5f) + 14f, shadowW * 0.6f,
            intArrayOf(Color.argb(90, 0, 0, 0), Color.TRANSPARENT),
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

        if (currentAngle <= 25f) {
            // --- DRAW CLOSED PHONE (COVER DISPLAY) ---
            drawClosedCoverDisplay(canvas, cx, cy, panelW, phoneH, cornerRadius, bmp, item)
        } else {
            // --- DRAW 3D UNFOLDING INNER DISPLAY ---
            drawUnfoldingInnerDisplay(canvas, cx, cy, panelW, phoneH, cornerRadius, bmp, item)
        }
    }

    private fun drawClosedCoverDisplay(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        panelW: Float,
        phoneH: Float,
        cornerRadius: Float,
        bmp: Bitmap?,
        item: WallpaperItem?
    ) {
        val left = cx - (panelW * 0.5f)
        val top = cy - (phoneH * 0.5f)
        val right = cx + (panelW * 0.5f)
        val bottom = cy + (phoneH * 0.5f)
        val rect = RectF(left, top, right, bottom)

        // Outer chassis
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, framePaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bezelStrokePaint)

        // Cover display screen
        val screenInset = 8f
        val screenRect = RectF(left + screenInset, top + screenInset, right - screenInset, bottom - screenInset)
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
                blurProgress = 0f
            )
        } else {
            canvas.drawColor(Color.parseColor("#1E293B"))
        }

        // Camera punch hole
        val camPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#090D16") }
        canvas.drawCircle(cx, screenRect.top + 14f, 4.5f, camPaint)

        canvas.restore()
    }

    private fun drawUnfoldingInnerDisplay(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        panelW: Float,
        phoneH: Float,
        cornerRadius: Float,
        bmp: Bitmap?,
        item: WallpaperItem?
    ) {
        // Hinge angle rotation: 0° (flat open) to -85° (half closed)
        val openFactor = (currentAngle / 180f).coerceIn(0.0f, 1.0f)
        val rotAngle = (1.0f - openFactor) * 75f

        val top = cy - (phoneH * 0.5f)
        val bottom = cy + (phoneH * 0.5f)

        leftPanelRect.set(cx - panelW, top, cx, bottom)
        rightPanelRect.set(cx, top, cx + panelW, bottom)

        val totalInnerW = (panelW * 2f).toInt()
        val totalInnerH = phoneH.toInt()

        // 1. Draw Left Panel (Articulated in 3D)
        canvas.save()
        camera.save()
        camera.rotateY(-rotAngle * 0.6f)
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
        canvas.drawRect(leftPanelRect, framePaint)

        // Inner wallpaper on left panel
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
                blurProgress = blurProgress
            )
            canvas.restore()
        }

        // Opening effect on left panel
        drawMockupOpeningEffect(canvas, leftPanelRect, cx, top, panelW, phoneH, true)

        canvas.drawPath(leftPanelPath, bezelStrokePaint)
        canvas.restore()
        canvas.restore()

        // 2. Draw Right Panel (Articulated in 3D Unfold)
        canvas.save()
        camera.save()
        camera.rotateY(rotAngle)
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
        canvas.drawRect(rightPanelRect, framePaint)

        // Inner wallpaper on right panel (continuous shared base)
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
                blurProgress = blurProgress
            )
            canvas.restore()
        }

        // Opening effect on right panel
        drawMockupOpeningEffect(canvas, rightPanelRect, cx, top, panelW, phoneH, false)

        canvas.drawPath(rightPanelPath, bezelStrokePaint)
        canvas.restore()
        canvas.restore()

        // 3. Central Hinge Crease Spine & Luminous Seam
        val spineW = 4f
        val spineRect = RectF(cx - (spineW * 0.5f), top, cx + (spineW * 0.5f), bottom)
        canvas.drawRect(spineRect, hingeSpinePaint)

        if (blurProgress > 0.05f) {
            val bloomAlpha = (blurProgress * 230f).toInt().coerceIn(0, 255)
            val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(bloomAlpha, 255, 255, 255)
                strokeWidth = 2.5f
            }
            canvas.drawLine(cx, top, cx, bottom, bloomPaint)
        }
    }

    private fun drawMockupOpeningEffect(
        canvas: Canvas,
        panelRect: RectF,
        cx: Float,
        top: Float,
        panelW: Float,
        phoneH: Float,
        isLeft: Boolean
    ) {
        if (blurProgress <= 0.02f) return

        when (openingEffect) {
            WallpaperRepository.EFFECT_DUO_BLUR -> {
                // iPhone Duo Optical Progressive Blur:
                // Sharp at center crease, frosted blur softening toward the moving outer edge
                val edgeAlpha = (blurProgress * 190f).toInt().coerceIn(0, 255)
                val midAlpha = (blurProgress * 100f).toInt().coerceIn(0, 255)

                val gradient = if (isLeft) {
                    LinearGradient(
                        panelRect.left, 0f, panelRect.right, 0f,
                        intArrayOf(Color.argb(edgeAlpha, 240, 245, 255), Color.argb(midAlpha, 230, 240, 250), Color.TRANSPARENT),
                        floatArrayOf(0.0f, 0.6f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                } else {
                    LinearGradient(
                        panelRect.left, 0f, panelRect.right, 0f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(midAlpha, 230, 240, 250), Color.argb(edgeAlpha, 240, 245, 255)),
                        floatArrayOf(0.0f, 0.4f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                }
                effectPaint.shader = gradient
                canvas.drawRect(panelRect, effectPaint)
            }

            WallpaperRepository.EFFECT_CREASE_BLOOM -> {
                val alpha = (blurProgress * 210f).toInt().coerceIn(0, 255)
                val beamW = panelW * (0.15f + blurProgress * 0.4f)
                val gradient = if (isLeft) {
                    LinearGradient(
                        panelRect.right - beamW, 0f, panelRect.right, 0f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 255, 250, 220)),
                        floatArrayOf(0.0f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                } else {
                    LinearGradient(
                        panelRect.left, 0f, panelRect.left + beamW, 0f,
                        intArrayOf(Color.argb(alpha, 255, 250, 220), Color.TRANSPARENT),
                        floatArrayOf(0.0f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                }
                effectPaint.shader = gradient
                canvas.drawRect(panelRect, effectPaint)
            }

            WallpaperRepository.EFFECT_LIQUID_GLASS -> {
                val alpha = (blurProgress * 170f).toInt().coerceIn(0, 255)
                val gradient = if (isLeft) {
                    LinearGradient(
                        panelRect.left, 0f, panelRect.right, 0f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 99, 102, 241), Color.argb(alpha, 236, 72, 153)),
                        floatArrayOf(0.0f, 0.7f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                } else {
                    LinearGradient(
                        panelRect.left, 0f, panelRect.right, 0f,
                        intArrayOf(Color.argb(alpha, 236, 72, 153), Color.argb(alpha, 56, 189, 248), Color.TRANSPARENT),
                        floatArrayOf(0.0f, 0.3f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                }
                effectPaint.shader = gradient
                canvas.drawRect(panelRect, effectPaint)
            }

            WallpaperRepository.EFFECT_APERTURE_HORIZON -> {
                val alpha = (blurProgress * 150f).toInt().coerceIn(0, 255)
                effectPaint.shader = null
                effectPaint.color = Color.argb(alpha, 15, 23, 42)
                canvas.drawRect(panelRect, effectPaint)
            }
        }
    }
}
