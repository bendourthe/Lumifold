package com.ambientfold.wallpaper.service

import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.render.DynamicParticleSystem

/**
 * Native Android Live Wallpaper Service for Google Pixel Foldables.
 * Implements hardware-accelerated depth blur opening transitions, 4 selectable unfolding effects,
 * real 4K photoreal imagery, live dynamic atmospheric particles, and touch responsiveness.
 */
class FoldWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return FoldEngine()
    }

    inner class FoldEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener,
        Choreographer.FrameCallback {

        private val mainHandler = Handler(Looper.getMainLooper())
        private var hingeObserver: HingeSensorObserver? = null
        private val particleSystem = DynamicParticleSystem(45)

        private var activeWallpaper: WallpaperItem? = null
        private var surfaceWidth: Int = 1080
        private var surfaceHeight: Int = 2400

        // Dual-display bitmap cache
        private var coverBitmap: Bitmap? = null
        private var innerBitmap: Bitmap? = null

        // Opening effect and animation state
        private var openingEffect: String = WallpaperRepository.DEFAULT_OPENING_EFFECT
        private var isDynamicMotionEnabled: Boolean = true
        private var maxBlurRadius: Float = WallpaperRepository.DEFAULT_MAX_BLUR
        private var animationDurationMs: Long = WallpaperRepository.DEFAULT_ANIMATION_DURATION

        // 0.0 = fully open (sharp, flat), 1.0 = fully closed (maximum blur)
        private var currentBlurProgress: Float = 0.0f
        private var isUnfoldingAnimating: Boolean = false
        private var unfoldAnimator: ValueAnimator? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var renderNode: RenderNode? = null

        private var isVisibleState = false
        private var isInnerScreenActive = false
        private var choreographer: Choreographer? = null

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
                }
            }

            choreographer = Choreographer.getInstance()
        }

        private fun reloadPreferences() {
            activeWallpaper = WallpaperRepository.getActiveWallpaper(this@FoldWallpaperService)
            maxBlurRadius = WallpaperRepository.getMaxBlurRadius(this@FoldWallpaperService)
            openingEffect = WallpaperRepository.getOpeningEffect(this@FoldWallpaperService)
            isDynamicMotionEnabled = WallpaperRepository.isDynamicMotionEnabled(this@FoldWallpaperService)
            animationDurationMs = WallpaperRepository.getAnimationDuration(this@FoldWallpaperService)

            activeWallpaper?.let {
                particleSystem.setPrimaryColor(it.particleColor)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            WallpaperRepository.getPrefs(this@FoldWallpaperService)
                .unregisterOnSharedPreferenceChangeListener(this)
            hingeObserver?.stop()
            choreographer?.removeFrameCallback(this)
            unfoldAnimator?.cancel()
            mainHandler.removeCallbacksAndMessages(null)
            clearBitmapCache()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisibleState = visible
            if (visible) {
                hingeObserver?.start()
                // Play cinematic unfold transition when screen turns on
                triggerUnfoldEntranceAnimation()
                choreographer?.postFrameCallback(this)
            } else {
                hingeObserver?.stop()
                unfoldAnimator?.cancel()
                choreographer?.removeFrameCallback(this)
                mainHandler.removeCallbacksAndMessages(null)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            val previousAspect = if (surfaceWidth > 0) surfaceHeight.toFloat() / surfaceWidth.toFloat() else 2.0f
            val newAspect = if (width > 0) height.toFloat() / width.toFloat() else 2.0f

            surfaceWidth = width
            surfaceHeight = height
            renderNode?.setPosition(0, 0, width, height)

            val newlyInner = newAspect <= 1.35f
            val displaySwitched = newlyInner != isInnerScreenActive
            isInnerScreenActive = newlyInner

            activeWallpaper?.let {
                particleSystem.init(width, height, it.particleColor)
            }

            if (displaySwitched && newlyInner) {
                // Switching from folded cover display to unfolded inner display
                triggerUnfoldEntranceAnimation()
            } else {
                drawCurrentFrame()
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            reloadPreferences()
            clearBitmapCache()
            triggerUnfoldEntranceAnimation()
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                particleSystem.addTouchBurst(event.x, event.y)
            }
        }

        /**
         * Triggers the signature iPhone Duo / foldable unfolding entrance effect.
         * Smoothly animates blur from 100% blur down to 0% with ease-out curve.
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
                doOnEnd {
                    isUnfoldingAnimating = false
                    currentBlurProgress = 0.0f
                }
                start()
            }
        }

        private inline fun ValueAnimator.doOnEnd(crossinline action: (animator: ValueAnimator) -> Unit) {
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    action(this@doOnEnd)
                }
            })
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!isVisibleState) return

            drawCurrentFrame()

            // Continue dynamic render loop if animated or motion enabled
            if (isVisibleState && (isDynamicMotionEnabled || isUnfoldingAnimating)) {
                choreographer?.postFrameCallback(this)
            }
        }

        private fun clearBitmapCache() {
            coverBitmap?.recycle()
            coverBitmap = null
            innerBitmap?.recycle()
            innerBitmap = null
        }

        private fun getOrCreateCachedBitmap(width: Int, height: Int, item: WallpaperItem): Bitmap? {
            if (width <= 0 || height <= 0) return null

            val aspectRatio = height.toFloat() / width.toFloat()
            val isCover = aspectRatio > 1.35f

            val existing = if (isCover) coverBitmap else innerBitmap
            if (existing != null && !existing.isRecycled && existing.width == width && existing.height == height) {
                return existing
            }

            if (isCover) {
                coverBitmap?.recycle()
                coverBitmap = null
            } else {
                innerBitmap?.recycle()
                innerBitmap = null
            }

            val drawable: Drawable? = AppCompatResources.getDrawable(this@FoldWallpaperService, item.drawableResId)
            val newBitmap = drawable?.toBitmap(width, height, Bitmap.Config.ARGB_8888)

            if (isCover) {
                coverBitmap = newBitmap
            } else {
                innerBitmap = newBitmap
            }
            return newBitmap
        }

        private fun drawCurrentFrame() {
            if (!isVisibleState) return

            val item = activeWallpaper ?: return
            val bmp = getOrCreateCachedBitmap(surfaceWidth, surfaceHeight, item) ?: return
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
                        drawWithRenderEffect(canvas, bmp)
                    } else {
                        drawFallback(canvas, bmp)
                    }

                    // Render selected opening effect layer (bloom, flare, wave)
                    if (currentBlurProgress > 0.01f) {
                        drawOpeningEffectOverlay(canvas)
                    }

                    // Render dynamic atmospheric particles
                    if (isDynamicMotionEnabled) {
                        particleSystem.updateAndDraw(canvas, currentBlurProgress)
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

        private fun drawWithRenderEffect(canvas: Canvas, bmp: Bitmap) {
            val node = renderNode ?: return

            val nodeCanvas = node.beginRecording()
            try {
                // Depth scale zoom out: 1.08x when folded down to 1.0x when open
                val scale = 1.0f + (currentBlurProgress * 0.08f)
                val cx = surfaceWidth / 2f
                val cy = surfaceHeight / 2f

                nodeCanvas.save()
                nodeCanvas.scale(scale, scale, cx, cy)
                nodeCanvas.drawBitmap(bmp, 0f, 0f, paint)
                nodeCanvas.restore()
            } finally {
                node.endRecording()
            }

            val blurRadius = currentBlurProgress * maxBlurRadius
            if (blurRadius > 0.5f) {
                val blurEffect = RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                )
                node.setRenderEffect(blurEffect)
            } else {
                node.setRenderEffect(null)
            }

            canvas.drawRenderNode(node)
        }

        private fun drawFallback(canvas: Canvas, bmp: Bitmap) {
            val src = Rect(0, 0, bmp.width, bmp.height)
            val dst = Rect(0, 0, surfaceWidth, surfaceHeight)
            canvas.drawBitmap(bmp, src, dst, paint)
        }

        private fun drawOpeningEffectOverlay(canvas: Canvas) {
            val cx = surfaceWidth / 2f
            val cy = surfaceHeight / 2f

            when (openingEffect) {
                WallpaperRepository.EFFECT_CREASE_BLOOM -> {
                    // Luminous vertical light beam expanding from center crease
                    val beamWidth = (40f + (currentBlurProgress * 120f)).coerceAtLeast(10f)
                    val alpha = (currentBlurProgress * 180f).toInt().coerceIn(0, 255)
                    val gradient = LinearGradient(
                        cx - beamWidth, 0f, cx + beamWidth, 0f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 255, 255, 255), Color.TRANSPARENT),
                        floatArrayOf(0.0f, 0.5f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    effectPaint.shader = gradient
                    canvas.drawRect(cx - beamWidth, 0f, cx + beamWidth, surfaceHeight.toFloat(), effectPaint)
                }
                WallpaperRepository.EFFECT_APERTURE_ZOOM -> {
                    // Radial aperture zoom vignette focusing outward
                    val radius = surfaceWidth * (0.8f + (1.0f - currentBlurProgress) * 0.6f)
                    val alpha = (currentBlurProgress * 140f).toInt().coerceIn(0, 255)
                    val gradient = RadialGradient(
                        cx, cy, radius,
                        intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 10, 8, 20)),
                        floatArrayOf(0.4f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    effectPaint.shader = gradient
                    canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), effectPaint)
                }
                WallpaperRepository.EFFECT_PRISMATIC_WAVE -> {
                    // Subtle chromatic dispersion wave along the fold crease
                    val waveDist = (1.0f - currentBlurProgress) * (surfaceWidth * 0.5f)
                    val alpha = (currentBlurProgress * 130f).toInt().coerceIn(0, 255)
                    val gradient = LinearGradient(
                        cx - waveDist, 0f, cx + waveDist, 0f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 0, 229, 255), Color.argb(alpha, 224, 64, 251), Color.TRANSPARENT),
                        floatArrayOf(0.0f, 0.45f, 0.55f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    effectPaint.shader = gradient
                    canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), effectPaint)
                }
                WallpaperRepository.EFFECT_DUO_BLUR -> {
                    // Pure classic Duo depth blur (RenderEffect does the work)
                }
            }
        }
    }
}
