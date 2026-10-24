package com.lumifold.wallpaper.service

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.lumifold.wallpaper.model.OpeningEffect
import com.lumifold.wallpaper.model.WallpaperItem
import com.lumifold.wallpaper.model.WallpaperRepository as Repo
import com.lumifold.wallpaper.model.WallpaperSource
import com.lumifold.wallpaper.render.FoldOptics
import com.lumifold.wallpaper.render.SurfaceSpec
import com.lumifold.wallpaper.render.WallpaperShader
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/**
 * Live wallpaper for book-style foldables. Every frame maps the current hinge angle straight to
 * the fold shader, so the effect tracks the hinge instead of playing a timed animation.
 */
class FoldWallpaperService : WallpaperService() {

    private val loader = Executors.newSingleThreadExecutor()

    override fun onCreateEngine(): Engine = FoldEngine()

    override fun onDestroy() {
        loader.shutdownNow()
        super.onDestroy()
    }

    inner class FoldEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener,
        Choreographer.FrameCallback {

        private val ctx: Context = this@FoldWallpaperService
        private val prefs = Repo.getPrefs(ctx)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val hinge = HingeTracker(ctx) { onHingeChanged() }
        private val awake = CoverAwakeKeeper(ctx)
        private val paint = Paint()
        private val startMs = SystemClock.uptimeMillis()

        private var item: WallpaperItem = Repo.getActiveWallpaper(ctx)
        private var effect: OpeningEffect = OpeningEffect.DEFAULT
        private var intensity = Repo.DEFAULT_INTENSITY
        private var motionPref = true
        private var foldEffect = true
        private var coverAwakeEnabled = true

        /** Living motion after the battery rules. */
        private var motion = true

        private var shader: WallpaperShader? = null
        private var loadingId: String? = null
        private var spec: SurfaceSpec? = null
        private var lastSurfaceInner: Boolean? = null
        private var visible = false
        private var lastVisibleMs = 0L

        private val spring = HingeTracker.Spring()
        private var lastFrameNanos = 0L
        private var frameScheduled = false
        private var oddFrame = false

        // The fold effect is a transition: a hinge held still at a half-open posture (tabletop,
        // book, tent) eases back to the sharp resting look of the current display.
        private var anchorAngle = Float.NaN
        private var lastMoveMs = 0L
        private var settle = 0f
        private val settleCheck = Runnable { requestFrame() }

        private val screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                hinge.stop()
                awake.release()
            }
        }

        private val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateMotion()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            prefs.registerOnSharedPreferenceChangeListener(this)
            ContextCompat.registerReceiver(
                ctx, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            ContextCompat.registerReceiver(
                ctx, batteryReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            loadPreferences()
        }

        private fun updateMotion() {
            val next = motionPref && MotionPolicy.currentPause(ctx) == MotionPolicy.Pause.NONE
            if (next != motion) {
                motion = next
                requestFrame()
            }
        }

        override fun onDestroy() {
            Choreographer.getInstance().removeFrameCallback(this)
            mainHandler.removeCallbacks(settleCheck)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            ctx.unregisterReceiver(screenOffReceiver)
            ctx.unregisterReceiver(batteryReceiver)
            hinge.stop()
            awake.release()
            shader?.pyramid?.recycle()
            shader = null
            super.onDestroy()
        }

        private fun loadPreferences() {
            effect = Repo.getOpeningEffect(ctx)
            intensity = Repo.getIntensity(ctx)
            motionPref = Repo.isDynamicMotionEnabled(ctx)
            foldEffect = Repo.isFoldEffectEnabled(ctx)
            coverAwakeEnabled = Repo.isCoverAwakeEnabled(ctx)
            updateMotion()
            val next = Repo.getActiveWallpaper(ctx)
            if (next.id != item.id || shader == null) {
                item = next
                ensureShader()
            }
            updateAwake()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == Repo.KEY_MEASURED_OPEN_SWITCH || key == Repo.KEY_MEASURED_CLOSE_SWITCH) return
            loadPreferences()
            requestFrame()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            val rotation = displayContext?.display?.rotation ?: 0
            val (nw, nh) = FoldOptics.naturalSize(width, height, rotation)
            val inner = FoldOptics.isInnerDisplay(nw, nh)
            rememberNaturalSize(inner, nw, nh)
            recordSwitchAngle(inner)

            val innerW = prefs.getInt(Repo.KEY_INNER_W, Repo.DEFAULT_INNER_W)
            val innerH = prefs.getInt(Repo.KEY_INNER_H, Repo.DEFAULT_INNER_H)
            val coverW = prefs.getInt(Repo.KEY_COVER_W, Repo.DEFAULT_COVER_W)
            val coverH = prefs.getInt(Repo.KEY_COVER_H, Repo.DEFAULT_COVER_H)
            val surfaceHeight = if (inner) nh / (nw * 0.5f) else nh.toFloat() / nw
            spec = SurfaceSpec(
                width, height, rotation, inner, surfaceHeight,
                FoldOptics.planeHeight(innerW, innerH, coverW, coverH)
            )
            spring.clear()
            ensureShader()
            updateAwake()
            drawFrame()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            lastVisibleMs = SystemClock.uptimeMillis()
            if (isVisible) {
                hinge.start()
                spring.clear()
                anchorAngle = Float.NaN
                settle = settleTarget()
                updateAwake()
                drawFrame()
                requestFrame()
            } else {
                updateAwake()
                Choreographer.getInstance().removeFrameCallback(this)
                frameScheduled = false
                lastFrameNanos = 0L
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) awake.onUserActivity()
        }

        override fun onCommand(
            action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean
        ): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP) awake.onUserActivity()
            return null
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            awake.onUserActivity()
        }

        private fun onHingeChanged() {
            val angle = hinge.angle
            if (anchorAngle.isNaN()) {
                anchorAngle = angle
            } else if (abs(angle - anchorAngle) > SETTLE_MOVE_DEG) {
                anchorAngle = angle
                lastMoveMs = SystemClock.uptimeMillis()
                mainHandler.removeCallbacks(settleCheck)
                mainHandler.postDelayed(settleCheck, SETTLE_DELAY_MS + 20L)
            }
            updateAwake()
            requestFrame()
        }

        private fun settleTarget(): Float =
            if (SystemClock.uptimeMillis() - lastMoveMs < SETTLE_DELAY_MS) 1f else 0f

        private fun updateAwake() {
            val angle = hinge.angle.takeUnless { it.isNaN() } ?: 0f
            awake.update(
                CoverAwakeKeeper.shouldHold(
                    enabled = coverAwakeEnabled,
                    visible = visible,
                    onInnerDisplay = spec?.isInner ?: true,
                    isPreview = isPreview,
                    interactive = awake.isInteractive,
                    hingeAngle = angle
                )
            )
        }

        private fun rememberNaturalSize(inner: Boolean, w: Int, h: Int) {
            val (kw, kh) = if (inner) Repo.KEY_INNER_W to Repo.KEY_INNER_H else Repo.KEY_COVER_W to Repo.KEY_COVER_H
            if (prefs.getInt(kw, 0) != w || prefs.getInt(kh, 0) != h) {
                prefs.edit().putInt(kw, w).putInt(kh, h).apply()
            }
        }

        /** Stores the hinge angle at which the system moved the wallpaper between displays. */
        private fun recordSwitchAngle(inner: Boolean) {
            val previous = lastSurfaceInner
            lastSurfaceInner = inner
            if (isPreview || previous == null || previous == inner) return
            val wasWatched = visible || SystemClock.uptimeMillis() - lastVisibleMs < 1_500L
            val angle = hinge.angle
            if (!wasWatched || angle.isNaN()) return
            val key = if (inner) Repo.KEY_MEASURED_OPEN_SWITCH else Repo.KEY_MEASURED_CLOSE_SWITCH
            prefs.edit().putFloat(key, angle).apply()
        }

        private fun ensureShader() {
            val current = shader
            if (current != null && current.item.id == item.id) {
                if (current.isReady || loadingId == item.id) return
            }
            val target = item
            val created = WallpaperShader.create(target)
            val photo = target.source as? WallpaperSource.Photo
            if (photo == null) {
                swapShader(created)
                return
            }
            val s = spec ?: return
            loadingId = target.id
            val texW = min(2560, prefs.getInt(Repo.KEY_INNER_W, Repo.DEFAULT_INNER_W))
            val texH = (texW * s.planeHeight / 2f).toInt()
            loader.execute {
                val pyramid = runCatching {
                    WallpaperShader.buildPyramid(resources, photo, texW, texH)
                }.getOrNull()
                mainHandler.post {
                    if (loadingId != target.id || pyramid == null || item.id != target.id) {
                        pyramid?.recycle()
                        return@post
                    }
                    loadingId = null
                    created.attachPyramid(pyramid)
                    swapShader(created)
                }
            }
        }

        private fun swapShader(next: WallpaperShader) {
            val old = shader
            shader = next
            if (old != null && old.pyramid !== next.pyramid) old.pyramid?.recycle()
            requestFrame()
        }

        private fun requestFrame() {
            if (!visible || frameScheduled) return
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            frameScheduled = false
            if (!visible) return
            val dt = if (lastFrameNanos == 0L) 0.016f
            else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
            lastFrameNanos = frameTimeNanos

            val target = targetAngle()
            spring.step(target, dt)
            val hingeMoving = !spring.isSettled(target)
            if (!hingeMoving) spring.snap(target)

            val goal = settleTarget()
            settle = HingeTracker.smoothToward(settle, goal, dt, if (goal > settle) 0.25f else 0.6f)
            val settling = abs(goal - settle) > 0.002f
            if (!settling) settle = goal

            oddFrame = !oddFrame
            if (hingeMoving || settling || !motion || oddFrame) drawFrame()
            if (hingeMoving || settling || motion) requestFrame()
        }

        /** Hinge angle fed to the shader, eased toward the display's resting angle by [settle]. */
        private fun effectAngle(s: SurfaceSpec): Float {
            val angle = spring.position.takeUnless { it.isNaN() } ?: targetAngle()
            val rest = if (s.isInner) 180f else 0f
            return rest + (angle - rest) * settle
        }

        private fun targetAngle(): Float {
            val raw = hinge.angle
            if (!raw.isNaN()) return raw
            return if (spec?.isInner == false) 0f else 180f
        }

        private fun timeSec(): Float =
            if (motion) ((SystemClock.uptimeMillis() - startMs) % 100_000_000L) / 1000f else STILL_TIME

        private fun drawFrame() {
            val s = spec ?: return
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                val ws = shader
                if (ws != null && ws.isReady) {
                    val angle = effectAngle(s)
                    ws.bind(s, Math.toRadians(angle.toDouble()).toFloat(), timeSec(), effect, intensity, motion, foldEffect)
                    paint.shader = ws.shader
                    canvas.drawRect(0f, 0f, s.width.toFloat(), s.height.toFloat(), paint)
                } else {
                    canvas.drawColor(Color.BLACK)
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }

    companion object {
        /** Scene time used when living motion is off. */
        const val STILL_TIME = 42f

        /** Hinge travel that counts as the user moving the hinge. */
        const val SETTLE_MOVE_DEG = 3f

        /** Stillness after which a half-open posture counts as settled. */
        const val SETTLE_DELAY_MS = 4_000L
    }
}
