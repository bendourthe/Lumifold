package com.lumifold.wallpaper.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Choreographer
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.lumifold.wallpaper.model.OpeningEffect
import com.lumifold.wallpaper.model.WallpaperRepository as Repo
import com.lumifold.wallpaper.render.FoldOptics
import com.lumifold.wallpaper.render.SurfaceSpec
import com.lumifold.wallpaper.render.WallpaperShader
import kotlin.math.min

/**
 * Keeps the inner and cover panels on together while the hinge is between closed and open,
 * and draws the fold on the inner panel. Android's normal policy powers only one panel,
 * and it swaps them near 90 degrees.
 */
class BothScreens(
    private val context: Context,
    private val content: () -> PanelContent
) {
    data class PanelContent(
        val shader: WallpaperShader?,
        val effect: OpeningEffect,
        val intensity: Float,
        val motion: Boolean,
        val foldEffect: Boolean
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val power = context.getSystemService(PowerManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val paint = Paint()

    private var started = false
    private var wanted = false
    private var requested = false
    private var attempts = 0
    private var retryPosted = false
    private var panel: Panel? = null
    private var windowContext: Context? = null
    private var attachedId = Display.INVALID_DISPLAY
    private var loggedFailure = false

    /** True while this controller is asking Android to power both panels. */
    var trackingRawAngle: Boolean = false
        private set

    /** Hinge angle, in degrees, for the inner panel to draw. */
    @Volatile var shownDegrees: Float = Float.NaN

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            mainHandler.post { if (wanted) attach() }
        }
        override fun onDisplayRemoved(displayId: Int) {
            mainHandler.post {
                if (displayId == attachedId) detach()
                if (wanted && findInner() == null) {
                    requested = false
                    scheduleRetry()
                }
            }
        }
        override fun onDisplayChanged(displayId: Int) {
            mainHandler.post {
                if (!wanted) return@post
                if (findInner() == null) {
                    detach()
                    requested = false
                    scheduleRetry()
                } else {
                    attach()
                }
            }
        }
    }

    private val retry = Runnable {
        retryPosted = false
        if (wanted && findInner() == null) attach()
    }

    private val watchdog = Runnable {
        if (!wanted) return@Runnable
        if (findInner() != null) {
            attempts = 0
            attach()
        } else {
            requested = false
            scheduleRetry()
        }
    }

    fun start() {
        if (started) return
        started = true
        FoldCommand.mode = FoldCommand.ONE
        displays?.registerDisplayListener(displayListener, mainHandler)
    }

    private var previousAngle = Float.NaN
    private var opening = true

    fun update(enabled: Boolean, angleDegrees: Float) {
        if (!angleDegrees.isNaN()) {
            if (!previousAngle.isNaN() && kotlin.math.abs(angleDegrees - previousAngle) > 1f) {
                opening = angleDegrees > previousAngle
            }
            previousAngle = angleDegrees
        }
        val interactive = power?.isInteractive != false
        val mode = if (!interactive) FoldCommand.ONE else FoldSpan.commandFor(enabled, angleDegrees, opening)
        mainHandler.post { applyMode(mode) }
    }

    fun onScreenOff() {
        mainHandler.post {
            wanted = false
            trackingRawAngle = false
            applied = FoldCommand.ONE
            attempts = 0
            FoldCommand.mode = FoldCommand.ONE
            detach()
        }
    }

    fun shutdown() {
        mainHandler.removeCallbacks(watchdog)
        mainHandler.removeCallbacks(retry)
        onScreenOff()
        if (started) {
            displays?.unregisterDisplayListener(displayListener)
            started = false
        }
    }

    fun shareFrame(frame: Bitmap) {
        if (Looper.myLooper() == Looper.getMainLooper()) panel?.share(frame)
        else mainHandler.post { panel?.share(frame) }
    }

    private var applied = FoldCommand.ONE

    private fun applyMode(mode: String) {
        val both = mode == FoldCommand.BOTH
        if (mode == applied && wanted == both) return
        applied = mode
        wanted = both
        trackingRawAngle = both
        attempts = 0
        loggedFailure = false
        retryPosted = false
        mainHandler.removeCallbacks(watchdog)
        mainHandler.removeCallbacks(retry)
        FoldCommand.mode = mode
        if (both) {
            Log.i(TAG, "Asked for both screens")
            mainHandler.postDelayed(watchdog, 400)
        } else {
            detach()
        }
    }

    private fun scheduleRetry() {
        if (!wanted || retryPosted) return
        retryPosted = true
        val delay = min(3000L, 350L * (attempts + 1))
        attempts++
        mainHandler.postDelayed(retry, delay)
    }

    private fun findInner(): Display? {
        val all = displays?.displays ?: return null
        val on = all.filter { it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON }
        return on.firstOrNull { it.name.contains("Outer", ignoreCase = true) } ?: on.firstOrNull()
    }

    private fun attach() {
        val display = findInner() ?: return
        if (panel != null && attachedId == display.displayId) {
            panel?.kick()
            return
        }
        detach()
        val cover = display.name.contains("Outer", ignoreCase = true)
        val types = intArrayOf(OVERLAY, PRESENTATION, APPLICATION)
        var lastError: Throwable? = null
        for (type in types) {
            try {
                val windowed = context.createWindowContext(display, type, null)
                val view = Panel(windowed, display.rotation, cover)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.OPAQUE
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    setTitle("Lumifold")
                    // The cover otherwise follows at about 7 nits, which looks fully off.
                    screenBrightness = 0.55f
                }
                windowed.getSystemService(WindowManager::class.java).addView(view, params)
                windowContext = windowed
                panel = view
                attachedId = display.displayId
                view.kick()
                Log.i(TAG, "Fold window on ${display.name} display ${display.displayId} cover=$cover")
                return
            } catch (error: Throwable) {
                lastError = error
            }
        }
        logFailure("Fold window on ${display.name}: ${lastError?.message}")
    }

    private fun detach() {
        val view = panel
        val windowed = windowContext
        panel = null
        windowContext = null
        attachedId = Display.INVALID_DISPLAY
        if (view != null && windowed != null) {
            runCatching { windowed.getSystemService(WindowManager::class.java).removeView(view) }
        }
    }

    private fun logFailure(message: String) {
        if (loggedFailure) return
        loggedFailure = true
        Log.w(TAG, message)
    }

    private inner class Panel(context: Context, private val rotation: Int, private val cover: Boolean) : View(context) {
        private var spec: SurfaceSpec? = null
        private var copy: WallpaperShader? = null
        private var source: WallpaperShader? = null
        private var framed: Bitmap? = null
        private var ticking = false
        private val tick = Choreographer.FrameCallback {
            ticking = false
            invalidate()
            kick()
        }

        fun kick() {
            if (ticking || !isAttachedToWindow) return
            ticking = true
            Choreographer.getInstance().postFrameCallback(tick)
        }

        fun share(frame: Bitmap) {
            val current = copy ?: return
            if (framed === frame) return
            framed = frame
            current.setVideoFrame(frame)
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            kick()
        }

        override fun onDetachedFromWindow() {
            ticking = false
            Choreographer.getInstance().removeFrameCallback(tick)
            super.onDetachedFromWindow()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            val (nw, nh) = FoldOptics.naturalSize(w, h, rotation)
            val prefs = context.getSharedPreferences(Repo.PREFS_NAME, Context.MODE_PRIVATE)
            val plane = FoldOptics.planeHeight(
                prefs.getInt(Repo.KEY_INNER_W, Repo.DEFAULT_INNER_W),
                prefs.getInt(Repo.KEY_INNER_H, Repo.DEFAULT_INNER_H),
                prefs.getInt(Repo.KEY_COVER_W, Repo.DEFAULT_COVER_W),
                prefs.getInt(Repo.KEY_COVER_H, Repo.DEFAULT_COVER_H)
            )
            val surfaceHeight = if (cover) nh.toFloat() / nw else nh / (nw * 0.5f)
            spec = SurfaceSpec(w, h, rotation, !cover, surfaceHeight, plane)
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val src = content().shader ?: return
            if (copy == null || source !== src) {
                source = src
                framed = null
                copy = runCatching { src.duplicate() }.getOrNull()
            }
            val ws = copy ?: return
            val frame = src.videoFrame
            if (frame != null && framed !== frame) {
                framed = frame
                ws.setVideoFrame(frame)
            }
            if (!ws.isReady) return
            val surface = spec ?: return
            val angle = shownDegrees
            if (angle.isNaN()) return
            val cfg = content()
            val time = if (cfg.motion) {
                (android.os.SystemClock.uptimeMillis() % 100_000_000L) / 1000f
            } else {
                FoldWallpaperService.STILL_TIME
            }
            ws.bind(
                surface,
                Math.toRadians(angle.toDouble()).toFloat(),
                time,
                cfg.effect,
                cfg.intensity,
                cfg.motion,
                cfg.foldEffect,
                symmetricInner = Repo.isSymmetricInner(context),
                fullSwipe = Repo.isFullSwipe(context)
            )
            paint.shader = ws.shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    private companion object {
        const val TAG = "LumifoldBoth"

        /** Public window type for a secondary display. The SDK stub used here does not name it. */
        const val PRESENTATION = 2037
        const val APPLICATION = 2
        const val OVERLAY = 2038
    }
}
