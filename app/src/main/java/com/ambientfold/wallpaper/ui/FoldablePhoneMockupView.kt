package com.ambientfold.wallpaper.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.render.DynamicParticleSystem

/**
 * Interactive Foldable Phone Mockup view for MainActivity.
 * Displays a 3D articulated foldable device frame, renders the active 4K wallpaper,
 * simulates real-time hinge angle transitions, and previews live dynamic particles and opening effects.
 */
class FoldablePhoneMockupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 0 = closed (folded), 180 = flat open
    var foldAngle: Float = 180f
        private set

    var onAngleChangedListener: ((Float) -> Unit)? = null

    private var activeWallpaper: WallpaperItem? = null
    private var wallpaperBitmap: Bitmap? = null
    private var openingEffect: String = WallpaperRepository.DEFAULT_OPENING_EFFECT
    private var isDynamicMotionEnabled: Boolean = true

    private val camera = Camera()
    private val matrix3D = Matrix()

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1A24")
    }
    private val bezelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D374D")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val hingeSpinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F0D17")
    }
    private val creaseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44D0BCFF")
        strokeWidth = 2f
    }
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val leftPanelPath = Path()
    private val rightPanelPath = Path()
    private val leftPanelRect = RectF()
    private val rightPanelRect = RectF()

    private val particleSystem = DynamicParticleSystem(28)
    private var animator: ValueAnimator? = null

    init {
        // Continuous animation loop for dynamic particles in mockup preview
        post(object : Runnable {
            override fun run() {
                if (isAttachedToWindow) {
                    invalidate()
                    postDelayed(this, 16L) // ~60fps
                }
            }
        })
    }

    fun setWallpaper(item: WallpaperItem) {
        activeWallpaper = item
        wallpaperBitmap?.recycle()
        wallpaperBitmap = null
        val drawable: Drawable? = AppCompatResources.getDrawable(context, item.drawableResId)
        wallpaperBitmap = drawable?.toBitmap(720, 720, Bitmap.Config.ARGB_8888)
        particleSystem.setPrimaryColor(item.particleColor)
        invalidate()
    }

    fun setOpeningEffect(effect: String) {
        openingEffect = effect
        invalidate()
    }

    fun setDynamicMotionEnabled(enabled: Boolean) {
        isDynamicMotionEnabled = enabled
        invalidate()
    }

    fun setFoldAngleDirect(angle: Float) {
        foldAngle = angle.coerceIn(0f, 180f)
        onAngleChangedListener?.invoke(foldAngle)
        invalidate()
    }

    fun playOpeningAnimation(durationMs: Long = 650L) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(30f, 180f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(2.0f)
            addUpdateListener { anim ->
                foldAngle = anim.animatedValue as Float
                onAngleChangedListener?.invoke(foldAngle)
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        particleSystem.init(w, h, activeWallpaper?.particleColor ?: Color.parseColor("#FFE082"))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        wallpaperBitmap?.recycle()
        wallpaperBitmap = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = 16f
        val deviceWidth = w - (padding * 2)
        val deviceHeight = h - (padding * 2)
        val cx = w / 2f
        val cy = h / 2f

        val halfDeviceW = deviceWidth / 2f
        val panelW = halfDeviceW - 3f
        val panelH = deviceHeight - 12f
        val cornerRadius = 24f

        // 1. Draw Outer Foldable Phone Frame (Left & Right Chassis)
        val frameLeft = cx - halfDeviceW
        val frameRight = cx + halfDeviceW
        val frameTop = cy - (deviceHeight / 2f)
        val frameBottom = cy + (deviceHeight / 2f)

        // Drop shadow for floating phone mockup
        shadowPaint.color = Color.parseColor("#55000000")
        canvas.drawRoundRect(frameLeft + 4f, frameTop + 10f, frameRight + 4f, frameBottom + 12f, cornerRadius + 4f, cornerRadius + 4f, shadowPaint)

        // Matte Titanium Frame
        canvas.drawRoundRect(frameLeft, frameTop, frameRight, frameBottom, cornerRadius, cornerRadius, framePaint)
        canvas.drawRoundRect(frameLeft, frameTop, frameRight, frameBottom, cornerRadius, cornerRadius, bezelStrokePaint)

        // 2. Compute 3D Perspective Rotation based on foldAngle (0 = folded, 180 = flat)
        // Normalized fold progress: 0.0 (open) to 1.0 (closed)
        val foldProgress = (1.0f - (foldAngle / 180f)).coerceIn(0f, 1f)
        val rotationY = foldProgress * 65f // Panels angle inward up to 65 degrees

        // 3. Draw Left Screen Panel with 3D perspective
        leftPanelRect.set(cx - panelW - 2f, cy - (panelH / 2f), cx - 2f, cy + (panelH / 2f))
        leftPanelPath.reset()
        leftPanelPath.addRoundRect(leftPanelRect, floatArrayOf(
            cornerRadius - 4f, cornerRadius - 4f,
            4f, 4f,
            4f, 4f,
            cornerRadius - 4f, cornerRadius - 4f
        ), Path.Direction.CW)

        canvas.save()
        camera.save()
        camera.rotateY(rotationY)
        camera.getMatrix(matrix3D)
        camera.restore()
        matrix3D.preTranslate(-cx, -cy)
        matrix3D.postTranslate(cx, cy)
        canvas.concat(matrix3D)

        canvas.clipPath(leftPanelPath)
        drawPanelContent(canvas, leftPanelRect, isLeft = true, foldProgress = foldProgress)
        canvas.restore()

        // 4. Draw Right Screen Panel with 3D perspective
        rightPanelRect.set(cx + 2f, cy - (panelH / 2f), cx + panelW + 2f, cy + (panelH / 2f))
        rightPanelPath.reset()
        rightPanelPath.addRoundRect(rightPanelRect, floatArrayOf(
            4f, 4f,
            cornerRadius - 4f, cornerRadius - 4f,
            cornerRadius - 4f, cornerRadius - 4f,
            4f, 4f
        ), Path.Direction.CW)

        canvas.save()
        camera.save()
        camera.rotateY(-rotationY)
        camera.getMatrix(matrix3D)
        camera.restore()
        matrix3D.preTranslate(-cx, -cy)
        matrix3D.postTranslate(cx, cy)
        canvas.concat(matrix3D)

        canvas.clipPath(rightPanelPath)
        drawPanelContent(canvas, rightPanelRect, isLeft = false, foldProgress = foldProgress)
        canvas.restore()

        // 5. Draw Central Hinge Spine & Crease Line
        canvas.drawRect(cx - 3f, frameTop + 4f, cx + 3f, frameBottom - 4f, hingeSpinePaint)
        canvas.drawLine(cx, frameTop + 10f, cx, frameBottom - 10f, creaseLinePaint)

        // 6. Draw Hinge shadow deepening as the phone folds
        if (foldProgress > 0.05f) {
            val creaseShadowAlpha = (foldProgress * 160f).toInt().coerceIn(0, 220)
            val creaseShadow = LinearGradient(
                cx - 35f, 0f, cx + 35f, 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(creaseShadowAlpha, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            shadowPaint.shader = creaseShadow
            canvas.drawRect(cx - 35f, frameTop, cx + 35f, frameBottom, shadowPaint)
        }
    }

    private fun drawPanelContent(canvas: Canvas, rect: RectF, isLeft: Boolean, foldProgress: Float) {
        val bmp = wallpaperBitmap
        if (bmp != null && !bmp.isRecycled) {
            val srcRect = if (isLeft) {
                Rect(0, 0, bmp.width / 2, bmp.height)
            } else {
                Rect(bmp.width / 2, 0, bmp.width, bmp.height)
            }
            canvas.drawBitmap(bmp, srcRect, rect, bitmapPaint)
        } else {
            canvas.drawColor(Color.parseColor("#151221"))
        }

        // Live Dynamic Particles inside mockup screen
        if (isDynamicMotionEnabled) {
            particleSystem.updateAndDraw(canvas, foldProgress)
        }

        // Selected Opening Effect Simulation inside mockup screen
        if (foldProgress > 0.01f) {
            drawMockupEffect(canvas, rect, isLeft, foldProgress)
        }
    }

    private fun drawMockupEffect(canvas: Canvas, rect: RectF, isLeft: Boolean, foldProgress: Float) {
        val cx = width / 2f
        val alpha = (foldProgress * 180f).toInt().coerceIn(0, 255)

        when (openingEffect) {
            WallpaperRepository.EFFECT_CREASE_BLOOM -> {
                // Radiant light beam along fold edge
                val startX = if (isLeft) rect.right - 50f else rect.left
                val endX = if (isLeft) rect.right else rect.left + 50f
                val colors = if (isLeft) {
                    intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 255, 255, 255))
                } else {
                    intArrayOf(Color.argb(alpha, 255, 255, 255), Color.TRANSPARENT)
                }
                val grad = LinearGradient(startX, 0f, endX, 0f, colors, null, Shader.TileMode.CLAMP)
                effectPaint.shader = grad
                canvas.drawRect(rect, effectPaint)
            }
            WallpaperRepository.EFFECT_APERTURE_ZOOM -> {
                // Vignette radial focus
                val grad = RadialGradient(
                    cx, rect.centerY(), rect.width() * 1.2f,
                    intArrayOf(Color.TRANSPARENT, Color.argb((foldProgress * 150f).toInt().coerceIn(0, 255), 10, 8, 20)),
                    floatArrayOf(0.4f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                effectPaint.shader = grad
                canvas.drawRect(rect, effectPaint)
            }
            WallpaperRepository.EFFECT_PRISMATIC_WAVE -> {
                // Chromatic edge glow
                val startX = if (isLeft) rect.right - 60f else rect.left
                val endX = if (isLeft) rect.right else rect.left + 60f
                val color = if (isLeft) Color.argb(alpha, 0, 229, 255) else Color.argb(alpha, 224, 64, 251)
                val grad = LinearGradient(startX, 0f, endX, 0f, intArrayOf(Color.TRANSPARENT, color), null, Shader.TileMode.CLAMP)
                effectPaint.shader = grad
                canvas.drawRect(rect, effectPaint)
            }
            WallpaperRepository.EFFECT_DUO_BLUR -> {
                // Duo depth blur overlay simulation
                val blurOverlayAlpha = (foldProgress * 140f).toInt().coerceIn(0, 255)
                canvas.drawColor(Color.argb(blurOverlayAlpha, 25, 20, 45))
            }
        }
    }
}
