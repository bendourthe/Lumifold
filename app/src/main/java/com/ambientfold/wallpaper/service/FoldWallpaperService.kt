package com.ambientfold.wallpaper.service

import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.animation.DecelerateInterpolator
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.render.DuoBlurShader
import com.ambientfold.wallpaper.render.DynamicMotionEngine
import kotlin.math.max

/**
 * Native Android Live Wallpaper Service for Google Pixel Foldables.
 * Implements:
 * 1. DuoBlur iPhone Duo Optical Opening Transition: Directional progressive frost sweeps from hinge,
 *    perspective compression, luminance attenuation, and crease defrosting via GPU AGSL RuntimeShader.
 * 2. Shared Image Base & Center-Crop: Preserves true 1:1 image aspect ratio on folded and unfolded displays.
 * 3. Dynamic Motion Engine: Continuous 60fps living motion with atmospheric clouds, golden sunlight sweeps,
 *    and fluid liquid glass flow.
 * 4. Two-Phase Sensor Handoff: Smooth continuous transition between cover screen (0°-90°) and inner screen (90°-180°).
 */
class FoldWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return FoldEngine()
    }

    inner class FoldEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val mainHandler = Handler(Looper.getMainLooper())
        private var hingeObserver: HingeSensorObserver? = null
        private val motionEngine = DynamicMotionEngine()

        private var activeWallpaper: WallpaperItem? = null
        private var surfaceWidth: Int = 1080
        private var surfaceHeight: Int = 2400

        // Shared Image Base: Single decoded high-resolution bitmap with preserved aspect ratio
        private var sharedBitmap: Bitmap? = null
        private var currentLoadedResId: Int = 0

        // Opening effect and animation state
        private var openingEffect: String = WallpaperRepository.DEFAULT_OPENING_EFFECT
        private var isDynamicMotionEnabled: Boolean = true
        private var maxBlurRadius: Float = WallpaperRepository.DEFAULT_MAX_BLUR
        private var animationDurationMs: Long = WallpaperRepository.DEFAULT_ANIMATION_DURATION

        // 0.0 = fully open (crystal sharp), 1.0 = peak frost / closed
        private var currentBlurProgress: Float = 0.0f
        private var isUnfoldingAnimating: Boolean = false
        private var unfoldAnimator: ValueAnimator? = null

        private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val matrix = Matrix()

        // Hardware AGSL RuntimeShader for API 33+ (Pixel Fold series)
        private var duoBlurRuntimeShader: Any? = null

        private var isVisibleState = false
        private var isInnerScreenActive = false
        private var isFrameLoopRunning = false

        private val frameRunnable = object : Runnable {
            override fun run() {
                if (!isVisibleState) {
                    isFrameLoopRunning = false
                    return
                }

                drawCurrentFrame()

                if (isVisibleState && (isDynamicMotionEnabled || isUnfoldingAnimating || currentBlurProgress > 0.005f)) {
                    mainHandler.postDelayed(this, 16L) // Solid 60 fps
                } else {
                    isFrameLoopRunning = false
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            WallpaperRepository.getPrefs(this@FoldWallpaperService)
                .registerOnSharedPreferenceChangeListener(this)

            reloadPreferences()
            initDuoBlurShaderIfSupported()

            hingeObserver = HingeSensorObserver(this@FoldWallpaperService) { _, angleDegrees ->
                if (!isUnfoldingAnimating) {
                    val twoPhaseProgress = HingeSensorObserver.calculateTwoPhaseProgress(angleDegrees, isInnerScreenActive)
                    currentBlurProgress = twoPhaseProgress
                    if (!isFrameLoopRunning) {
                        startFrameLoop()
                    }
                }
            }
        }

        private fun initDuoBlurShaderIfSupported() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    duoBlurRuntimeShader = RuntimeShader(DuoBlurShader.DUO_FOLD_SHADER_SRC)
                } catch (e: Throwable) {
                    duoBlurRuntimeShader = null
                }
            }
        }

        private fun reloadPreferences() {
            activeWallpaper = WallpaperRepository.getActiveWallpaper(this@FoldWallpaperService)
            maxBlurRadius = WallpaperRepository.getMaxBlurRadius(this@FoldWallpaperService)
            openingEffect = WallpaperRepository.getOpeningEffect(this@FoldWallpaperService)
            isDynamicMotionEnabled = WallpaperRepository.isDynamicMotionEnabled(this@FoldWallpaperService)
            animationDurationMs = WallpaperRepository.getAnimationDuration(this@FoldWallpaperService)

            activeWallpaper?.let { item ->
                if (currentLoadedResId != item.drawableResId || sharedBitmap == null) {
                    loadSharedBitmap(item.drawableResId)
                }
            }
        }

        private fun loadSharedBitmap(drawableResId: Int) {
            try {
                sharedBitmap?.recycle()
                sharedBitmap = null

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeResource(resources, drawableResId, options)

                val maxDimension = 2560
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                sharedBitmap = BitmapFactory.decodeResource(resources, drawableResId, decodeOptions)
                currentLoadedResId = drawableResId
            } catch (e: Throwable) {
                // Fallback
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            WallpaperRepository.getPrefs(this@FoldWallpaperService)
                .unregisterOnSharedPreferenceChangeListener(this)
            hingeObserver?.stop()
            unfoldAnimator?.cancel()
            mainHandler.removeCallbacksAndMessages(null)
            sharedBitmap?.recycle()
            sharedBitmap = null
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisibleState = visible
            if (visible) {
                hingeObserver?.start()
                triggerUnfoldEntranceAnimation()
                startFrameLoop()
            } else {
                hingeObserver?.stop()
                unfoldAnimator?.cancel()
                mainHandler.removeCallbacksAndMessages(null)
                isFrameLoopRunning = false
            }
        }

        private fun startFrameLoop() {
            if (!isFrameLoopRunning) {
                isFrameLoopRunning = true
                mainHandler.post(frameRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            val newAspect = if (width > 0) height.toFloat() / width.toFloat() else 2.0f

            surfaceWidth = width
            surfaceHeight = height

            // Foldables: inner tablet aspect ratio is <= 1.35; cover display is >= 1.9
            val newlyInner = newAspect <= 1.35f
            val displaySwitched = newlyInner != isInnerScreenActive
            isInnerScreenActive = newlyInner

            if (sharedBitmap == null && activeWallpaper != null) {
                loadSharedBitmap(activeWallpaper!!.drawableResId)
            }

            if (displaySwitched && newlyInner) {
                // Phase 3: Inner Defrosting as device opens to 180°
                triggerUnfoldEntranceAnimation()
            } else {
                startFrameLoop()
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            reloadPreferences()
            triggerUnfoldEntranceAnimation()
            startFrameLoop()
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                motionEngine.addTouchRipple(event.x, event.y)
                if (!isFrameLoopRunning) {
                    startFrameLoop()
                }
            }
        }

        /**
         * Triggers the signature DuoBlur unfolding entrance transition.
         * Smoothly animates blur from 100% peak frost down to razor-sharp 4K clarity.
         */
        fun triggerUnfoldEntranceAnimation() {
            unfoldAnimator?.cancel()
            isUnfoldingAnimating = true

            unfoldAnimator = ValueAnimator.ofFloat(1.0f, 0.0f).apply {
                duration = animationDurationMs
                interpolator = DecelerateInterpolator(2.2f)
                addUpdateListener { anim ->
                    currentBlurProgress = anim.animatedValue as Float
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        isUnfoldingAnimating = false
                        currentBlurProgress = 0.0f
                    }
                })
                start()
            }
            startFrameLoop()
        }

        private fun drawCurrentFrame() {
            if (!isVisibleState) return

            val item = activeWallpaper ?: return
            val bmp = sharedBitmap ?: return
            if (bmp.isRecycled) return

            val holder = surfaceHolder
            var canvas: Canvas? = null

            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }

                if (canvas != null) {
                    val w = surfaceWidth
                    val h = surfaceHeight

                    // If DuoBlur AGSL transition is active on API 33+
                    if (openingEffect == WallpaperRepository.EFFECT_DUO_BLUR &&
                        currentBlurProgress > 0.005f &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        duoBlurRuntimeShader != null
                    ) {
                        drawAgslDuoBlurFrame(canvas, bmp, w, h)
                    } else {
                        // Render standard frame with dynamic motion engine and Center-Crop
                        motionEngine.render(
                            canvas = canvas,
                            bitmap = bmp,
                            width = w,
                            height = h,
                            wallpaper = item,
                            isDynamic = isDynamicMotionEnabled,
                            blurProgress = currentBlurProgress
                        )

                        // Render overlay if non-AGSL opening effect is active
                        if (currentBlurProgress > 0.01f) {
                            drawFallbackOpeningOverlay(canvas)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore transient surface interruptions during physical display handoff
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        // Surface released during fold
                    }
                }
            }
        }

        /**
         * Renders the authentic iPhone Duo progressive blur using AGSL RuntimeShader.
         */
        private fun drawAgslDuoBlurFrame(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int) {
            val shader = duoBlurRuntimeShader as? RuntimeShader ?: return

            // Compute Center-Crop transformation matrix for the input shader
            val scaleBase = max(width.toFloat() / bitmap.width.toFloat(), height.toFloat() / bitmap.height.toFloat())
            val scaledW = bitmap.width * scaleBase
            val scaledH = bitmap.height * scaleBase
            val dx = (width - scaledW) * 0.5f
            val dy = (height - scaledH) * 0.5f

            matrix.reset()
            matrix.postScale(scaleBase, scaleBase)
            matrix.postTranslate(dx, dy)

            val bmpShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(matrix)
            }

            shader.setInputShader("content", bmpShader)
            shader.setFloatUniform("resolution", width.toFloat(), height.toFloat())
            shader.setFloatUniform("foldProgress", currentBlurProgress)
            shader.setFloatUniform("isInnerScreen", if (isInnerScreenActive) 1.0f else 0.0f)

            effectPaint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), effectPaint)

            // Luminous optical hinge crease seam (on inner folding display)
            if (isInnerScreenActive && currentBlurProgress > 0.03f) {
                val cx = width * 0.5f
                val beamW = (6f + currentBlurProgress * 16f)
                val alpha = (currentBlurProgress * 230f).toInt().coerceIn(0, 255)
                val beam = LinearGradient(
                    cx - beamW, 0f, cx + beamW, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 255, 255, 255), Color.TRANSPARENT),
                    floatArrayOf(0.0f, 0.5f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                seamPaint.shader = beam
                canvas.drawRect(cx - beamW, 0f, cx + beamW, height.toFloat(), seamPaint)
            }
        }

        /**
         * Fallback overlay for opening effects (or older Android versions).
         */
        private fun drawFallbackOpeningOverlay(canvas: Canvas) {
            val w = surfaceWidth.toFloat()
            val h = surfaceHeight.toFloat()
            val cx = w * 0.5f
            val cy = h * 0.5f

            when (openingEffect) {
                WallpaperRepository.EFFECT_DUO_BLUR -> {
                    val edgeAlpha = (currentBlurProgress * 190f).toInt().coerceIn(0, 255)
                    val midAlpha = (currentBlurProgress * 105f).toInt().coerceIn(0, 255)

                    val duoBlurGradient = LinearGradient(
                        0f, 0f, w, 0f,
                        intArrayOf(
                            Color.argb(edgeAlpha, 235, 240, 250),
                            Color.argb(midAlpha, 225, 235, 245),
                            Color.TRANSPARENT,
                            Color.argb(midAlpha, 225, 235, 245),
                            Color.argb(edgeAlpha, 235, 240, 250)
                        ),
                        floatArrayOf(0.0f, 0.28f, 0.50f, 0.72f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    effectPaint.shader = duoBlurGradient
                    canvas.drawRect(0f, 0f, w, h, effectPaint)

                    val creaseWidth = (24f + (currentBlurProgress * 60f))
                    val beamAlpha = (currentBlurProgress * 220f).toInt().coerceIn(0, 255)
                    val creaseBeam = LinearGradient(
                        cx - creaseWidth, 0f, cx + creaseWidth, 0f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(beamAlpha, 255, 255, 255), Color.TRANSPARENT),
                        floatArrayOf(0.0f, 0.5f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    effectPaint.shader = creaseBeam
                    canvas.drawRect(cx - creaseWidth, 0f, cx + creaseWidth, h, effectPaint)
                }

                WallpaperRepository.EFFECT_CREASE_BLOOM -> {
                    val beamWidth = (40f + (currentBlurProgress * 150f)).coerceAtLeast(10f)
                    val alpha = (currentBlurProgress * 220f).toInt().coerceIn(0, 255)
                    val gradient = LinearGradient(
                        cx - beamWidth, 0f, cx + beamWidth, 0f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 255, 248, 220), Color.TRANSPARENT),
                        floatArrayOf(0.0f, 0.5f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    effectPaint.shader = gradient
                    canvas.drawRect(cx - beamWidth, 0f, cx + beamWidth, h, effectPaint)
                }

                WallpaperRepository.EFFECT_LIQUID_GLASS -> {
                    val waveWidth = (1.0f - currentBlurProgress) * (w * 0.55f)
                    val alpha = (currentBlurProgress * 160f).toInt().coerceIn(0, 255)
                    val gradient = LinearGradient(
                        cx - waveWidth, 0f, cx + waveWidth, 0f,
                        intArrayOf(
                            Color.TRANSPARENT,
                            Color.argb(alpha, 99, 102, 241),
                            Color.argb(alpha, 236, 72, 153),
                            Color.argb(alpha, 56, 189, 248),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0.0f, 0.35f, 0.50f, 0.65f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    effectPaint.shader = gradient
                    canvas.drawRect(0f, 0f, w, h, effectPaint)
                }

                WallpaperRepository.EFFECT_APERTURE_HORIZON -> {
                    val radius = w * (0.5f + (1.0f - currentBlurProgress) * 0.8f)
                    val alpha = (currentBlurProgress * 160f).toInt().coerceIn(0, 255)
                    val gradient = RadialGradient(
                        cx, cy, radius.coerceAtLeast(10f),
                        intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 15, 23, 42)),
                        floatArrayOf(0.45f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    effectPaint.shader = gradient
                    canvas.drawRect(0f, 0f, w, h, effectPaint)
                }
            }
        }
    }
}
