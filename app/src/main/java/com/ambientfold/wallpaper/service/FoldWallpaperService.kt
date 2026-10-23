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
        private var wallpaperBitmap: Bitmap? = null
        private var surfaceWidth: Int = 1080
        private var surfaceHeight: Int = 2400

        private var foldProgress: Float = 1.0f // 1.0 = fully open (no blur), 0.0 = closed (max blur)
        private var targetBlurRadius: Float = 0f
        private var currentBlurRadius: Float = 0f
        private var maxBlurRadius: Float = 42f

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
            wallpaperBitmap?.recycle()
            wallpaperBitmap = null
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisibleState = visible
            if (visible) {
                hingeObserver?.start()
                drawFrame()
            } else {
                hingeObserver?.stop()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            renderNode?.setPosition(0, 0, width, height)
            reloadWallpaperBitmap()
            drawFrame()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            activeWallpaper = WallpaperRepository.getActiveWallpaper(this@FoldWallpaperService)
            maxBlurRadius = WallpaperRepository.getMaxBlurRadius(this@FoldWallpaperService)
            reloadWallpaperBitmap()
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

        private fun reloadWallpaperBitmap() {
            val item = activeWallpaper ?: return
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return

            val drawable: Drawable? = AppCompatResources.getDrawable(this@FoldWallpaperService, item.drawableResId)
            drawable?.let {
                wallpaperBitmap?.recycle()
                wallpaperBitmap = it.toBitmap(surfaceWidth, surfaceHeight, Bitmap.Config.ARGB_8888)
            }
        }

        private fun drawFrame() {
            if (!isVisibleState) return

            val holder = surfaceHolder
            var canvas: Canvas? = null

            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }

                if (canvas != null && wallpaperBitmap != null && !wallpaperBitmap!!.isRecycled) {
                    currentBlurRadius = targetBlurRadius

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
                        drawWithRenderEffect(canvas)
                    } else {
                        drawFallback(canvas)
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

        private fun drawWithRenderEffect(canvas: Canvas) {
            val node = renderNode ?: return
            val bmp = wallpaperBitmap ?: return

            // Record drawing inside hardware-accelerated RenderNode
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

        private fun drawFallback(canvas: Canvas) {
            val bmp = wallpaperBitmap ?: return
            val src = Rect(0, 0, bmp.width, bmp.height)
            val dst = Rect(0, 0, surfaceWidth, surfaceHeight)
            canvas.drawBitmap(bmp, src, dst, paint)
        }
    }
}
