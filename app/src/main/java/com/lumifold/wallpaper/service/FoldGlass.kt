package com.lumifold.wallpaper.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.lumifold.wallpaper.model.OpeningEffect
import com.lumifold.wallpaper.model.WallpaperRepository

/**
 * Dims the app on screen while the hinge is moving, in a soft sweep that follows the fold. It
 * works over live content and never waits for a screenshot, which apps that lay themselves out for
 * the new panel would delay. Android can blur another app only in uniform rectangles, whose hard
 * edges would cut across the sweep, so apps are dimmed rather than blurred.
 */
class FoldGlass(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val window = context.getSystemService(WindowManager::class.java)
    private var view: GlassView? = null
    private var logged = false
    private var wanted = false
    private var latestAngle = 0f
    private var latestInner = true
    private var latestIntensity = 1f

    fun update(show: Boolean, angleDegrees: Float, inner: Boolean, effect: OpeningEffect, intensity: Float) {
        if (Looper.myLooper() == Looper.getMainLooper()) apply(show, angleDegrees, inner, intensity)
        else main.post { apply(show, angleDegrees, inner, intensity) }
    }

    fun hide() = update(false, 0f, false, OpeningEffect.DEFAULT, 1f)

    private fun apply(show: Boolean, angleDegrees: Float, inner: Boolean, intensity: Float) {
        val moving = show && angleDegrees.isFinite()
        if (!moving || !Settings.canDrawOverlays(context)) {
            detach()
            return
        }
        if (inner != latestInner) detach()
        latestAngle = angleDegrees
        latestInner = inner
        latestIntensity = intensity
        wanted = true
        render()
    }

    private fun render() {
        if (!wanted) return
        val glass = view ?: attach() ?: return
        glass.dim = appDim(latestInner, latestAngle, WallpaperRepository.isFullSwipe(context))
        glass.alpha = (APP_DIM * latestIntensity).coerceAtMost(MAX_APP_DIM)
        glass.invalidate()
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
                        Log.d(TAG, "First frame committed inner=$latestInner angle=$latestAngle elapsedMs=${SystemClock.uptimeMillis() - glass.createdAt}")
                    }
                }
            }
            Log.d(TAG, "Attached inner=$latestInner")
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
        if (glass != null) {
            runCatching { window?.removeView(glass) }
            Log.d(TAG, "Detached")
        }
    }

    private class GlassView(context: Context) : View(context) {
        val createdAt = SystemClock.uptimeMillis()
        var dim = AppDim(front = 2f, dimLeft = true)
        private val paint = Paint()
        private val placement = Matrix()
        // One unit-wide ramp per direction, placed on the front each frame.
        private val dimLeft = LinearGradient(0f, 0f, 1f, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        private val dimRight = LinearGradient(0f, 0f, 1f, 0f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)

        override fun onDraw(canvas: Canvas) {
            if (width <= 0 || height <= 0) return
            val w = width.toFloat()
            val ramp = if (dim.dimLeft) dimLeft else dimRight
            placement.setScale(2f * DIM_BAND * w, 1f)
            placement.postTranslate((dim.front - DIM_BAND) * w, 0f)
            ramp.setLocalMatrix(placement)
            paint.shader = ramp
            canvas.drawRect(0f, 0f, w, height.toFloat(), paint)
        }
    }

    private companion object {
        const val TAG = "LumifoldGlass"
    }
}

/** Dimming over an app at 100% effect strength, and the most any strength may reach. */
private const val APP_DIM = 0.6f
private const val MAX_APP_DIM = 0.85f

/** Half-width of the soft edge between dimmed and lit, in panel widths. */
internal const val DIM_BAND = 0.15f

/** Where the dim sweep's soft front sits, in panel widths from the left, and which side is dim. */
internal data class AppDim(val front: Float, val dimLeft: Boolean)

/**
 * The dim sweep over an app for one hinge pose. The cover darkens from its right edge to its left
 * between closed and 90 degrees. The inner screen starts dark and clears from its right edge to
 * its left between 90 degrees and flat; without Full swipe only its left half starts dark and it
 * clears from the hinge. Closing plays the same sweep back.
 */
internal fun appDim(inner: Boolean, degrees: Float, fullSwipe: Boolean): AppDim {
    if (!inner) {
        val q = (degrees / 90f).coerceIn(0f, 1f)
        return AppDim(1f + DIM_BAND - q * (1f + 2f * DIM_BAND), dimLeft = false)
    }
    val p = ((degrees - 90f) / 90f).coerceIn(0f, 1f)
    val start = if (fullSwipe) 1f + DIM_BAND else 0.5f
    return AppDim(start - p * (start + DIM_BAND), dimLeft = true)
}

