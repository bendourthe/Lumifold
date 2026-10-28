package com.lumifold.wallpaper.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.lumifold.wallpaper.R
import com.lumifold.wallpaper.model.OpeningEffect
import com.lumifold.wallpaper.model.WallpaperCategory
import com.lumifold.wallpaper.model.WallpaperItem
import com.lumifold.wallpaper.model.WallpaperSource
import com.lumifold.wallpaper.render.SurfaceSpec
import com.lumifold.wallpaper.render.FoldShaders
import com.lumifold.wallpaper.render.WallpaperShader

/**
 * Draws the selected opening effect over the app on screen while the hinge is moving.
 * A screen picture is taken once per fold. Before it arrives, the same effect's shading follows
 * the hinge over live content, without a full-display blur that would change the stationary pane.
 */
class FoldGlass(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val window = context.getSystemService(WindowManager::class.java)
    private var view: GlassView? = null
    private var logged = false
    private var capturing = false
    private var captureAttempted = false
    private var generation = 0
    private var wanted = false
    private var latestAngle = 0f
    private var latestInner = true
    private var latestEffect = OpeningEffect.DEFAULT
    private var latestIntensity = 1f
    private var captureDeadline: Runnable? = null
    private var captureRequest: Runnable? = null
    private var captureUntil = 0L
    private var cancelCapture: (() -> Unit)? = null
    private var frame: Bitmap? = null
    private var frameBounds: Rect? = null
    private var shader: WallpaperShader? = null
    // Compile before movement, then reuse the same effect and angle for both rendering paths.
    private val shading = runCatching {
        WallpaperShader.create(SCREEN).also {
            it.setVideoFrame(createBitmap(1, 1).apply { eraseColor(Color.WHITE) })
        }
    }.onFailure { Log.w(TAG, "Fold shading unavailable", it) }.getOrNull()
    private val shadingMask = runCatching {
        RuntimeShader(FoldShaders.shadingMaskSource())
    }.onFailure { Log.w(TAG, "Fold shading mask unavailable", it) }.getOrNull()

    fun update(show: Boolean, angleDegrees: Float, inner: Boolean, effect: OpeningEffect, intensity: Float) {
        if (Looper.myLooper() == Looper.getMainLooper()) apply(show, angleDegrees, inner, effect, intensity)
        else main.post { apply(show, angleDegrees, inner, effect, intensity) }
    }

    fun hide() = update(false, 0f, false, OpeningEffect.DEFAULT, 1f)

    private fun apply(
        show: Boolean,
        angleDegrees: Float,
        inner: Boolean,
        effect: OpeningEffect,
        intensity: Float
    ) {
        val moving = show && angleDegrees.isFinite()
        if (!moving || !Settings.canDrawOverlays(context)) {
            detach()
            return
        }
        if (inner != latestInner) detach()
        latestAngle = angleDegrees
        latestInner = inner
        latestEffect = effect
        latestIntensity = intensity
        wanted = true
        // Window capture excludes this overlay, so matching shading can start immediately.
        if (!captureAttempted) captureOnce()
        renderView()
    }

    private fun renderView() {
        if (!wanted) return
        val glass = view ?: attach() ?: return
        glass.angle = latestAngle
        glass.inner = latestInner
        glass.effect = latestEffect
        glass.intensity = latestIntensity
        glass.shader = shader
        glass.shading = shading
        glass.shadingMask = shadingMask
        glass.captureBounds = frameBounds
        glass.invalidate()
    }

    private fun captureOnce() {
        captureAttempted = true
        val service = FoldAccessibilityService.instance ?: return
        capturing = true
        val token = ++generation
        captureUntil = SystemClock.uptimeMillis() + CAPTURE_WINDOW_MS
        captureDeadline = Runnable {
            if (token == generation && capturing && wanted) {
                capturing = false
                cancelCapture?.invoke()
                cancelCapture = null
                captureRequest?.let { main.removeCallbacks(it) }
                captureRequest = null
                Log.d(TAG, "Screenshot deadline; retaining live shading")
                renderView()
            }
        }.also { main.postDelayed(it, CAPTURE_WINDOW_MS) }
        captureRequest = Runnable {
            if (token == generation && wanted && capturing) requestCapture(service, token)
        }.also { main.post(it) }
    }

    private fun requestCapture(service: FoldAccessibilityService, token: Int) {
        captureRequest = null
        cancelCapture?.invoke()
        cancelCapture = service.capture(
            isCurrent = { token == generation && wanted && capturing && SystemClock.uptimeMillis() < captureUntil },
            onFrame = { bmp, bounds ->
                if (token != generation || !wanted || !capturing || SystemClock.uptimeMillis() >= captureUntil) {
                    bmp.recycle()
                } else {
                    capturing = false
                    captureDeadline?.let { main.removeCallbacks(it) }
                    frame?.recycle()
                    frame = bmp
                    frameBounds = bounds
                    shader = runCatching { WallpaperShader.create(SCREEN).also { it.setVideoFrame(bmp) } }
                        .onFailure { Log.w(TAG, "Screen shader unavailable", it) }.getOrNull()
                    Log.d(TAG, "Screenshot ready ${bmp.width}x${bmp.height}")
                    renderView()
                }
            },
            onMiss = { retryAfter ->
                if (token == generation && wanted && capturing) {
                    val delay = captureRetryDelay(SystemClock.uptimeMillis(), captureUntil, retryAfter)
                    if (delay == null) {
                        capturing = false
                        captureDeadline?.let { main.removeCallbacks(it) }
                    } else {
                        captureRequest = Runnable {
                            if (token == generation && wanted && capturing) requestCapture(service, token)
                        }.also { main.postDelayed(it, delay) }
                    }
                    renderView()
                }
            }
        )
    }

    private fun attach(): GlassView? {
        val glass = GlassView(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (context is AccessibilityService) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setTitle("Lumifold glass")
        }
        return try {
            val manager = window ?: return null
            manager.addView(glass, params)
            view = glass
            glass.viewTreeObserver.registerFrameCommitCallback {
                main.post {
                    if (view !== glass || !wanted) return@post
                    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                        Log.d(TAG, "First frame committed inner=${glass.inner} angle=${glass.angle} elapsedMs=${SystemClock.uptimeMillis() - glass.createdAt}")
                    }
                }
            }
            Log.d(TAG, "Attached inner=$latestInner screenshot=${shader != null}")
            glass
        } catch (error: Throwable) {
            if (!logged) {
                logged = true
                Log.w(TAG, "Fold glass: ${error.message}")
            }
            null
        }
    }

    private fun detach() {
        val glass = view
        view = null
        wanted = false
        generation++
        capturing = false
        captureAttempted = false
        cancelCapture?.invoke()
        cancelCapture = null
        captureRequest?.let { main.removeCallbacks(it) }
        captureRequest = null
        captureDeadline?.let { main.removeCallbacks(it) }
        captureDeadline = null
        glass?.shader = null
        frame?.recycle()
        frame = null
        frameBounds = null
        shader = null
        if (glass != null) {
            runCatching { window?.removeView(glass) }
            Log.d(TAG, "Detached")
        }
    }

    private class GlassView(context: Context) : View(context) {
        val createdAt = SystemClock.uptimeMillis()
        private var firstDraw = true
        var angle = 0f
        var inner = true
        var effect = OpeningEffect.DEFAULT
        var intensity = 1f
        var shader: WallpaperShader? = null
        var shading: WallpaperShader? = null
        var shadingMask: RuntimeShader? = null
        var captureBounds: Rect? = null
        private val paint = Paint()
        private val surfaceBounds = Rect()
        private var spec: SurfaceSpec? = null

        /** The surface spec for this size and panel, rebuilt only when either changes. */
        private fun specFor(w: Int, h: Int): SurfaceSpec {
            spec?.let { if (it.width == w && it.height == h && it.isInner == inner) return it }
            val plane = if (inner) h / (w * 0.5f) else h / w.toFloat()
            return SurfaceSpec(w, h, 0, inner, plane, plane.coerceAtLeast(0.5f)).also { spec = it }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            if (firstDraw) {
                firstDraw = false
                if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.d(TAG, "First draw inner=$inner angle=$angle size=${width}x$height shader=${shader?.isReady} hardware=${canvas.isHardwareAccelerated} elapsedMs=${SystemClock.uptimeMillis() - createdAt}")
                }
            }
            val captured = shader?.takeIf { it.isReady }
            val ws = captured ?: shading
            val bounds = if (captured != null) captureBounds
                else surfaceBounds.apply { set(0, 0, width, height) }
            if (ws != null && ws.isReady && bounds != null) {
                val spec = specFor(bounds.width(), bounds.height())
                ws.bind(
                    spec,
                    Math.toRadians(angle.toDouble()).toFloat(),
                    FoldWallpaperService.STILL_TIME,
                    effect,
                    intensity,
                    false,
                    true,
                    symmetricInner = com.lumifold.wallpaper.model.WallpaperRepository.isSymmetricInner(context)
                )
                paint.shader = if (captured != null) ws.shader else (shadingMask ?: return).apply {
                    // A child shader snapshots uniforms; refresh it after binding this hinge pose.
                    setInputShader("effect", ws.shader)
                }
                canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
                    clipRect(0f, 0f, spec.width.toFloat(), spec.height.toFloat())
                    drawRect(0f, 0f, spec.width.toFloat(), spec.height.toFloat(), paint)
                }
            }
        }
    }

    private companion object {
        const val TAG = "LumifoldGlass"

        /**
         * How long a fold keeps retrying the screenshot: as long as a held hinge keeps the effect
         * on screen. After a panel switch, two-pane apps such as Settings take about 1.8 s to settle.
         */
        const val CAPTURE_WINDOW_MS = FoldMotion.IDLE_MS
        val SCREEN = WallpaperItem(
            id = "screen_fold",
            title = "Screen",
            category = WallpaperCategory.SCENES,
            source = WallpaperSource.Video("", R.drawable.ic_settings_gear, 1f),
            description = ""
        )
    }
}

/** A display can report ON while its capture still contains the black handoff frame. */
internal fun isBlankScreenFrame(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Boolean {
    if (width <= 0 || height <= 0) return true
    // Bound work independently of the two panel resolutions, including their outermost pixels.
    for (y in 0..31) for (x in 0..31) {
        val pixel = pixelAt(x * (width - 1) / 31, y * (height - 1) / 31)
        if (pixel ushr 24 != 0 &&
            ((pixel shr 16 and 255) > 4 || (pixel shr 8 and 255) > 4 || (pixel and 255) > 4)) return false
    }
    return true
}
