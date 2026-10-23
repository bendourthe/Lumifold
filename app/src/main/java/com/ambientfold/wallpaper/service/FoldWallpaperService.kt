package com.ambientfold.wallpaper.service

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository

class FoldWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return FoldEngine()
    }

    inner class FoldEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val mainHandler = Handler(Looper.getMainLooper())
        private var hingeObserver: HingeSensorObserver? = null

        private var activeWallpaper: WallpaperItem? = null
        private var surfaceWidth: Int = 1080
        private var surfaceHeight: Int = 2400

        // Dual-display bitmap cache for instantaneous fold / unfold transitions
        private var coverBitmap: Bitmap? = null // For aspect ratio > 1.4 (tall cover display)
        private var innerBitmap: Bitmap? = null // For aspect ratio <= 1.4 (square unfolded inner display)

        private var foldProgress: Float = 0.0f // 0.0 = fully open (no blur), 1.0 = closed (max blur)
        private var targetBlurRadius: Float = 0f
        private var currentBlurRadius: Float = 0f
        private var maxBlurRadius: Float = WallpaperRepository.DEFAULT_MAX_BLUR

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var renderNode: RenderNode? = null

        private var isVisibleState = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            WallpaperRepository.getPrefs(this@FoldWallpaperService)
                .registerOnSharedPreferenceChangeListener(this)

            activeWallpaper = WallpaperRepository.getActiveWallpaper(this@FoldWallpaperService)
            maxBlurRadius = WallpaperRepository.getMaxBlurRadius(this@FoldWallpaperService)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                renderNode = RenderNode("FoldWallpaperRenderNode")
            }

            hingeObserver = HingeSensorObserver(this@FoldWallpaperService) { blurProgress, _ ->
                onHingeProgressChanged(blurProgress)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            WallpaperRepository.getPrefs(this@FoldWallpaperService)
                .unregisterOnSharedPreferenceChangeListener(this)
            hingeObserver?.stop()
            mainHandler.removeCallbacksAndMessages(null)
            clearBitmapCache()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisibleState = visible
            if (visible) {
                hingeObserver?.start()
                drawFrame()
            } else {
                // Strict battery conservation: stop sensor polling and cancel pending frame draws
                hingeObserver?.stop()
                mainHandler.removeCallbacksAndMessages(null)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            renderNode?.setPosition(0, 0, width, height)
            drawFrame()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            activeWallpaper = WallpaperRepository.getActiveWallpaper(this@FoldWallpaperService)
            maxBlurRadius = WallpaperRepository.getMaxBlurRadius(this@FoldWallpaperService)
            clearBitmapCache()
            drawFrame()
        }

        private fun onHingeProgressChanged(blurProgress: Float) {
            foldProgress = blurProgress
            targetBlurRadius = blurProgress * maxBlurRadius

            // Trigger redraw on UI thread
            mainHandler.post {
                drawFrame()
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
            val isCoverScreen = aspectRatio > 1.4f

            val existing = if (isCoverScreen) coverBitmap else innerBitmap
            if (existing != null && !existing.isRecycled && existing.width == width && existing.height == height) {
                return existing
            }

            // Recycle old slot allocation before rasterizing new size
            if (isCoverScreen) {
                coverBitmap?.recycle()
                coverBitmap = null
            } else {
                innerBitmap?.recycle()
                innerBitmap = null
            }

            val drawable: Drawable? = AppCompatResources.getDrawable(this@FoldWallpaperService, item.drawableResId)
            val newBitmap = drawable?.toBitmap(width, height, Bitmap.Config.ARGB_8888)

            if (isCoverScreen) {
                coverBitmap = newBitmap
            } else {
                innerBitmap = newBitmap
            }
            return newBitmap
        }

        private fun drawFrame() {
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
                    currentBlurRadius = targetBlurRadius

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
                        drawWithRenderEffect(canvas, bmp)
                    } else {
                        drawFallback(canvas, bmp)
                    }
                }
            } catch (e: Exception) {
                // Ignore transient lock failures during rapid fold display switches
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        // Surface might have been destroyed during fold
                    }
                }
            }
        }

        private fun drawWithRenderEffect(canvas: Canvas, bmp: Bitmap) {
            val node = renderNode ?: return

            val nodeCanvas = node.beginRecording()
            try {
                // Subtle scale zoom-out when folding (from 1.0 to 1.04)
                val scale = 1.0f + (foldProgress * 0.04f)
                val cx = surfaceWidth / 2f
                val cy = surfaceHeight / 2f

                nodeCanvas.save()
                nodeCanvas.scale(scale, scale, cx, cy)
                nodeCanvas.drawBitmap(bmp, 0f, 0f, paint)
                nodeCanvas.restore()
            } finally {
                node.endRecording()
            }

            // Apply Android 12+ hardware blur effect based on fold angle
            if (currentBlurRadius > 0.5f) {
                val blurEffect = RenderEffect.createBlurEffect(
                    currentBlurRadius,
                    currentBlurRadius,
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
    }
}
