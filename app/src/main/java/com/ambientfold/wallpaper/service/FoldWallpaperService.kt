package com.ambientfold.wallpaper.service

import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.RenderNode
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
import com.ambientfold.wallpaper.render.DynamicMotionEngine
import kotlin.math.abs

/**
 * Native Android Live Wallpaper Service for Google Pixel Foldables.
 * Implements:
 * 1. Shared Image Base & Center-Crop: Preserves true 1:1 image aspect ratio on folded and unfolded displays.
 * 2. iPhone Duo Optical Opening Effect: Selective hinge sharpening, progressive horizontal blur towards moving edges,
 *    and luminous crease specular bloom.
 * 3. Dynamic Motion Engine: Subtle organic landscape breathing, 60fps AGSL liquid glass flow, and texture shimmer.
 * 4. Reliable main-looper 60fps render loop with battery-saving static mode.
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

        // 0.0 = fully open (crystal sharp), 1.0 = fully closed (maximum optical blur)
        private var currentBlurProgress: Float = 0.0f
        private var isUnfoldingAnimating: Boolean = false
        private var unfoldAnimator: ValueAnimator? = null

        private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var renderNode: RenderNode? = null

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

                if (isVisibleState && (isDynamicMotionEnabled || isUnfoldingAnimating)) {
                    mainHandler.postDelayed(this, 16L) // Target 60 fps
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                renderNode = RenderNode("FoldWallpaperRenderNode")
            }

            hingeObserver = HingeSensorObserver(this@FoldWallpaperService) { sensorProgress, _ ->
                if (!isUnfoldingAnimating) {
                    currentBlurProgress = sensorProgress
                    if (!isFrameLoopRunning) {
                        drawCurrentFrame()
                    }
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
                if (currentLoadedResId != item.drawableResId) {
                    loadSharedBitmap(item.drawableResId)
                }
            }
        }

        private fun loadSharedBitmap(drawableResId: Int) {
            try {
                sharedBitmap?.recycle()
                sharedBitmap = null

                // Decode with sample size to preserve full detail while avoiding OOM on 4K assets
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
                // Fallback to null
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
            renderNode?.setPosition(0, 0, width, height)

            val newlyInner = newAspect <= 1.35f
            val displaySwitched = newlyInner != isInnerScreenActive
            isInnerScreenActive = newlyInner

            if (sharedBitmap == null && activeWallpaper != null) {
                loadSharedBitmap(activeWallpaper!!.drawableResId)
            }

            if (displaySwitched && newlyInner) {
                triggerUnfoldEntranceAnimation()
            } else {
                drawCurrentFrame()
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            reloadPreferences()
            triggerUnfoldEntranceAnimation()
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
         * Triggers the signature iPhone Duo / foldable unfolding entrance effect.
         * Smoothly animates blur from 100% optical blur down to crystal clarity with ease-out curve.
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
                    // Render base wallpaper with dynamic motion engine and Center-Crop
                    motionEngine.render(
                        canvas = canvas,
                        bitmap = bmp,
                        width = surfaceWidth,
                        height = surfaceHeight,
                        wallpaper = item,
                        isDynamic = isDynamicMotionEnabled,
                        blurProgress = currentBlurProgress
                    )

                    // Render opening effect overlay if opening transition active
                    if (currentBlurProgress > 0.01f) {
                        drawOpeningEffectOverlay(canvas)
                    }
                }
            } catch (e: Exception) {
                // Ignore transient surface interruptions during display handoff
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        // Surface released during rapid fold
                    }
                }
            }
        }

        /**
         * Renders the selected optical opening effect over the wallpaper.
         */
        private fun drawOpeningEffectOverlay(canvas: Canvas) {
            val w = surfaceWidth.toFloat()
            val h = surfaceHeight.toFloat()
            val cx = w * 0.5f
            val cy = h * 0.5f

            when (openingEffect) {
                WallpaperRepository.EFFECT_DUO_BLUR -> {
                    // iPhone Duo Optical Blur Effect:
                    // 1. Content is sharp near the central hinge crease.
                    // 2. Softens progressively toward the moving outer edges.
                    // 3. Illuminated crease bloom and specular glass reflection.
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

                    // Crease specular bloom beam (optical glint along hinge seam)
                    val creaseWidth = (28f + (currentBlurProgress * 80f)).coerceAtLeast(8f)
                    val beamAlpha = (currentBlurProgress * 220f).toInt().coerceIn(0, 255)
                    val creaseBeam = LinearGradient(
                        cx - creaseWidth, 0f, cx + creaseWidth, 0f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(beamAlpha, 255, 255, 255), Color.TRANSPARENT),
                        floatArrayOf(0.0f, 0.5f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    effectPaint.shader = creaseBeam
                    canvas.drawRect(cx - creaseWidth, 0f, cx + creaseWidth, h, effectPaint)

                    // Ambient luminous brightness shift
                    val shiftAlpha = (currentBlurProgress * 45f).toInt().coerceIn(0, 255)
                    effectPaint.shader = null
                    effectPaint.color = Color.argb(shiftAlpha, 255, 255, 255)
                    canvas.drawRect(0f, 0f, w, h, effectPaint)
                }

                WallpaperRepository.EFFECT_CREASE_BLOOM -> {
                    // Luminous vertical light beam expanding from center crease
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
                    // Chromatic dispersion fluid wave expanding from crease
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
                    // Cinematic horizontal aperture sweep
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
