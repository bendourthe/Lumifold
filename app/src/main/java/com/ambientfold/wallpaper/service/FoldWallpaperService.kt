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
import com.ambientfold.wallpaper.model.DuoBlurStateManager
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.render.DuoBlurShader
import com.ambientfold.wallpaper.render.DynamicMotionEngine
import kotlin.math.max

/**
 * Native Android Live Wallpaper Service for Google Pixel Foldables.
 * Implements:
 * 1. Physical Foldable Topology Handoff: Seamless state continuity across Outer Cover Display
 *    (Display 0) and Inner Main Display (Display 1).
 * 2. Synchronized Frosting at 90° Handoff: When the inner display illuminates at ~90°, it begins
 *    at 100% peak frost and darkened luminance matching the outer display, clearing smoothly from
 *    the center crease outward as the phone opens to 180°.
 * 3. Continuous Hinge Telemetry: Shared DuoBlurStateManager keeps sensor observations active across
 *    display reconfiguration and engine recreations.
 * 4. 60fps Dynamic Motion: Living ambient motion active on both cover and inner displays.
 */
class FoldWallpaperService : WallpaperService() {

    private var sharedHingeObserver: HingeSensorObserver? = null

    override fun onCreate() {
        super.onCreate()
        sharedHingeObserver = HingeSensorObserver(this) { _, _ ->
            // DuoBlurStateManager is automatically updated by HingeSensorObserver
        }
        sharedHingeObserver?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedHingeObserver?.stop()
        sharedHingeObserver = null
    }

    override fun onCreateEngine(): Engine {
        return FoldEngine()
    }

    inner class FoldEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener,
        DuoBlurStateManager.TelemetryListener {

        private val mainHandler = Handler(Looper.getMainLooper())
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

        // 0.0 = crystal sharp, 1.0 = peak frost at 90° handoff
        private var currentBlurProgress: Float = 0.0f
        private var isUnfoldingAnimating: Boolean = false
        private var unfoldAnimator: ValueAnimator? = null

        private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val matrix = Matrix()

        // Hardware AGSL RuntimeShader for API 33+ (Android 13/14/15/16/17 on Pixel Fold)
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
                    mainHandler.postDelayed(this, 16L) // 60 fps
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

            DuoBlurStateManager.addListener(this)
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

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
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
            DuoBlurStateManager.removeListener(this)
            WallpaperRepository.getPrefs(this@FoldWallpaperService)
                .unregisterOnSharedPreferenceChangeListener(this)
            unfoldAnimator?.cancel()
            mainHandler.removeCallbacksAndMessages(null)
            sharedBitmap?.recycle()
            sharedBitmap = null
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisibleState = visible
            if (visible) {
                // When inner screen powers on during unfolding, synchronize frost with current angle
                if (isInnerScreenActive) {
                    val angle = DuoBlurStateManager.currentAngle
                    if (angle < 170f) {
                        // Starts at matching frost (e.g. 100% at ~90°) and smoothly clears to 180°
                        currentBlurProgress = DuoBlurStateManager.getCurrentFrostProgress(angle, true)
                        triggerInnerDefrostAnimation()
                    } else {
                        currentBlurProgress = 0.0f
                    }
                }
                startFrameLoop()
            } else {
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

            // Foldable topology detection: Inner tablet display is <= 1.35; Outer cover display is >= 1.8
            val newlyInner = newAspect <= 1.35f
            val displaySwitched = newlyInner != isInnerScreenActive
            isInnerScreenActive = newlyInner

            DuoBlurStateManager.updateDisplayContext(if (newlyInner) 1 else 0, newlyInner)

            if (sharedBitmap == null && activeWallpaper != null) {
                loadSharedBitmap(activeWallpaper!!.drawableResId)
            }

            if (displaySwitched && newlyInner) {
                // Handoff to Inner Main Display: Start at 100% peak frost and unbend toward 180°
                currentBlurProgress = DuoBlurStateManager.getCurrentFrostProgress(DuoBlurStateManager.currentAngle, true)
                triggerInnerDefrostAnimation()
            } else {
                currentBlurProgress = DuoBlurStateManager.getCurrentFrostProgress(DuoBlurStateManager.currentAngle, isInnerScreenActive)
                startFrameLoop()
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            reloadPreferences()
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
         * Telemetry listener called whenever hardware hinge angle changes.
         * Synchronously computes frost progress matching the active display leaf.
         */
        override fun onTelemetryUpdated(
            angle: Float,
            isInner: Boolean,
            displayId: Int,
            frostProgress: Float,
            phaseLabel: String
        ) {
            if (!isUnfoldingAnimating) {
                currentBlurProgress = DuoBlurStateManager.getCurrentFrostProgress(angle, isInnerScreenActive)
                if (!isFrameLoopRunning) {
                    startFrameLoop()
                }
            }
        }

        /**
         * Smoothly animates the inner screen from its handoff frost (1.0) down to crystal clarity (0.0).
         */
        private fun triggerInnerDefrostAnimation() {
            unfoldAnimator?.cancel()
            isUnfoldingAnimating = true

            val startFrost = currentBlurProgress.coerceAtLeast(0.85f)
            unfoldAnimator = ValueAnimator.ofFloat(startFrost, 0.0f).apply {
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

                    // If DuoBlur AGSL transition is active
                    if (openingEffect == WallpaperRepository.EFFECT_DUO_BLUR &&
                        currentBlurProgress > 0.005f &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        duoBlurRuntimeShader != null
                    ) {
                        drawAgslDuoBlurFrame(canvas, bmp, w, h)
                    } else {
                        // Render wallpaper with Center-Crop and living motion engine
                        motionEngine.render(
                            canvas = canvas,
                            bitmap = bmp,
                            width = w,
                            height = h,
                            wallpaper = item,
                            isDynamic = isDynamicMotionEnabled,
                            blurProgress = currentBlurProgress
                        )

                        // Render fallback overlay if effect is active
                        if (currentBlurProgress > 0.01f) {
                            drawFallbackOpeningOverlay(canvas)
                        }
                    }
                }
            } catch (e: Exception) {
                // Surface transient during display power cycle
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

        private fun drawAgslDuoBlurFrame(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int) {
            val shader = duoBlurRuntimeShader as? RuntimeShader ?: return

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

            // Crease light bloom on inner folding screen (centered at x = 50%)
            if (isInnerScreenActive && currentBlurProgress > 0.02f) {
                val cx = width * 0.5f
                val beamW = (6f + currentBlurProgress * 16f)
                val alpha = (currentBlurProgress * 235f).toInt().coerceIn(0, 255)
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

        private fun drawFallbackOpeningOverlay(canvas: Canvas) {
            val w = surfaceWidth.toFloat()
            val h = surfaceHeight.toFloat()
            val cx = w * 0.5f
            val cy = h * 0.5f

            when (openingEffect) {
                WallpaperRepository.EFFECT_DUO_BLUR -> {
                    val edgeAlpha = (currentBlurProgress * 195f).toInt().coerceIn(0, 255)
                    val midAlpha = (currentBlurProgress * 110f).toInt().coerceIn(0, 255)

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
