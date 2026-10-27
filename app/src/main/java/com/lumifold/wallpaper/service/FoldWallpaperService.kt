package com.lumifold.wallpaper.service

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Display
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.lumifold.wallpaper.model.OpeningEffect
import com.lumifold.wallpaper.model.WallpaperItem
import com.lumifold.wallpaper.model.WallpaperRepository as Repo
import com.lumifold.wallpaper.model.WallpaperSource
import com.lumifold.wallpaper.render.ClipSource
import com.lumifold.wallpaper.render.FoldOptics
import com.lumifold.wallpaper.render.SurfaceSpec
import com.lumifold.wallpaper.render.VideoFrames
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
        private var both: BothScreens? = null
        private val foldMotion = FoldMotion()
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
        private var video: VideoFrames? = null
        private var videoItemId: String? = null
        private var spec: SurfaceSpec? = null
        private var reloadPhoto = false
        private var lastSurfaceInner: Boolean? = null
        private var drawnSpec: SurfaceSpec? = null
        private var surfaceGeneration = 0

        // When the system moves the wallpaper to the other display, that display starts from the
        // shared midpoint of the effect and eases to the hinge, wherever the switch happened.
        private var handoffStartMs = 0L
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
        private var previousHinge = Float.NaN
        private var closingHinge = false

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        hinge.stop()
                        awake.release()
                        both?.onScreenOff()
                        foldMotion.reset()
                    }
                    Intent.ACTION_SCREEN_ON -> if (!isPreview && visible) {
                        hinge.start()
                        both?.update(coverAwakeEnabled, hinge.angle)
                    }
                }
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
            if (!isPreview) {
                both = BothScreens(ctx) {
                    BothScreens.PanelContent(shader, effect, intensity, motion, foldEffect)
                }
                both?.start()
                ctx.stopService(Intent(ctx, ScreenCaptureService::class.java))
            }
            ContextCompat.registerReceiver(
                ctx,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
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
                updateVideoPlayback()
                requestFrame()
            }
        }

        private fun startVideo(target: WallpaperItem) {
            stopVideo()
            if (target.source !is WallpaperSource.Video) return
            val source = ClipSource.best(ctx, target) ?: return
            video = runCatching {
                VideoFrames(ctx, source, mainHandler) { frame ->
                    val ws = shader
                    if (ws != null && ws.item.id == target.id) {
                        ws.setVideoFrame(frame)
                        both?.shareFrame(frame)
                        requestFrame()
                    }
                }
            }.getOrNull()
            videoItemId = target.id
            updateVideoPlayback()
        }

        private fun stopVideo() {
            video?.close()
            video = null
            videoItemId = null
        }

        private fun updateVideoPlayback() {
            if (visible && motion) video?.play() else video?.pause()
        }

        override fun onDestroy() {
            if (backgroundEngine === this) backgroundEngine = null
            surfaceGeneration++
            Choreographer.getInstance().removeFrameCallback(this)
            mainHandler.removeCallbacks(settleCheck)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            ctx.unregisterReceiver(screenReceiver)
            ctx.unregisterReceiver(batteryReceiver)
            stopVideo()
            hinge.stop()
            awake.release()
            both?.shutdown()
            both = null
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
            both?.update(coverAwakeEnabled && !isPreview, hinge.angle)
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
            surfaceGeneration++
            drawnSpec = null
            val rotation = 0
            val (nw, nh) = FoldOptics.naturalSize(width, height, rotation)
            val inner = FoldOptics.isInnerDisplay(nw, nh)
            // Picker previews are scaled or cropped surfaces: they must not redefine the panels.
            if (!isPreview) rememberNaturalSize(inner, nw, nh)
            if (!isPreview && lastSurfaceInner != null && lastSurfaceInner != inner &&
                both?.trackingRawAngle != true
            ) startHandoff()
            recordSwitchAngle(inner)

            val innerW = prefs.getInt(Repo.KEY_INNER_W, Repo.DEFAULT_INNER_W)
            val innerH = prefs.getInt(Repo.KEY_INNER_H, Repo.DEFAULT_INNER_H)
            val coverW = prefs.getInt(Repo.KEY_COVER_W, Repo.DEFAULT_COVER_W)
            val coverH = prefs.getInt(Repo.KEY_COVER_H, Repo.DEFAULT_COVER_H)
            val surfaceHeight = if (inner) nh / (nw * 0.5f) else nh.toFloat() / nw
            val next = SurfaceSpec(
                width, height, rotation, inner, surfaceHeight,
                FoldOptics.planeHeight(innerW, innerH, coverW, coverH)
            )
            Log.d(TAG, "surface ${width}x$height rot=$rotation inner=$inner preview=$isPreview plane=${next.planeHeight}")
            val texChanged = spec?.let { it.planeHeight != next.planeHeight } ?: false
            spec = next
            spring.clear()
            // A photo is cropped to the plane when loaded; rebuild it if the plane changed since.
            if (texChanged && (item.source as? WallpaperSource.Photo)?.globe == null) reloadPhoto = true
            ensureShader()
            updateAwake()
            drawFrame()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceGeneration++
            drawnSpec = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            surfaceGeneration++
            if (!isPreview && displayContext?.display?.displayId == Display.DEFAULT_DISPLAY) {
                if (isVisible) backgroundEngine = this
                else if (backgroundEngine === this) backgroundEngine = null
            }
            lastVisibleMs = SystemClock.uptimeMillis()
            updateVideoPlayback()
            if (isVisible) {
                hinge.start()
                spring.clear()
                anchorAngle = Float.NaN
                settle = settleTarget()
                updateAwake()
                drawFrame()
                requestFrame()
            } else {
                hinge.stop()
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
            foldMotion.sample(angle, SystemClock.uptimeMillis())
            if (!angle.isNaN() && !previousHinge.isNaN() && abs(angle - previousHinge) > 1f) {
                closingHinge = angle < previousHinge
            }
            if (!angle.isNaN()) previousHinge = angle
            if (anchorAngle.isNaN()) {
                anchorAngle = angle
            } else if (abs(angle - anchorAngle) > SETTLE_MOVE_DEG) {
                anchorAngle = angle
                lastMoveMs = SystemClock.uptimeMillis()
                mainHandler.removeCallbacks(settleCheck)
                mainHandler.postDelayed(settleCheck, SETTLE_DELAY_MS + 20L)
            }
            both?.update(coverAwakeEnabled && !isPreview, angle)
            if (!visible) both?.shownDegrees = angle
            updateAwake()
            requestFrame()
        }

        private fun startHandoff() {
            val now = SystemClock.uptimeMillis()
            handoffStartMs = now
            lastMoveMs = now
            settle = 1f
            mainHandler.removeCallbacks(settleCheck)
            mainHandler.postDelayed(settleCheck, SETTLE_DELAY_MS + 20L)
        }

        /** 1 right after a display switch, easing to 0 over [HANDOFF_MS]. */
        private fun handoffWeight(): Float {
            if (handoffStartMs == 0L) return 0f
            val t = (SystemClock.uptimeMillis() - handoffStartMs) / HANDOFF_MS.toFloat()
            if (t >= 1f) return 0f
            val k = 1f - t
            return k * k * (3f - 2f * k)
        }

        /**
         * Stretches the hinge range a display actually sees so its effect reaches the shared
         * midpoint (90 degrees in shader terms) where this phone switches displays.
         */
        private fun remapForSwitch(angle: Float, inner: Boolean): Float {
            return if (inner) {
                val c = saneSwitch(prefs.getFloat(Repo.KEY_MEASURED_CLOSE_SWITCH, 90f))
                (180f - (180f - angle) * 90f / (180f - c)).coerceIn(90f, 180f)
            } else {
                val o = saneSwitch(prefs.getFloat(Repo.KEY_MEASURED_OPEN_SWITCH, 90f))
                (angle * 90f / o).coerceIn(0f, 90f)
            }
        }

        /** A stored switch near 175 came from the dual-screen override, not a real handoff. */
        private fun saneSwitch(stored: Float): Float =
            if (stored.isNaN() || stored < 30f || stored > 120f) 90f else stored

        private fun settleTarget(): Float =
            if (SystemClock.uptimeMillis() - lastMoveMs < SETTLE_DELAY_MS) 1f else 0f

        private fun updateAwake() {
            val angle = hinge.angle.takeUnless { it.isNaN() } ?: 0f
            // Holds the cover until Android's own switch, about 90 degrees. Releasing once the
            // inner surface is up keeps a fast open from leaving that screen dark.
            awake.update(
                CoverAwakeKeeper.shouldHold(
                    enabled = coverAwakeEnabled,
                    visible = visible,
                    onInnerDisplay = spec?.isInner == true,
                    isPreview = isPreview,
                    interactive = awake.isInteractive,
                    hingeAngle = angle,
                    closing = closingHinge
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
            if (isPreview || coverAwakeEnabled || previous == null || previous == inner) return
            val wasWatched = visible || SystemClock.uptimeMillis() - lastVisibleMs < 1_500L
            val angle = hinge.angle
            if (!wasWatched || angle.isNaN()) return
            val key = if (inner) Repo.KEY_MEASURED_OPEN_SWITCH else Repo.KEY_MEASURED_CLOSE_SWITCH
            prefs.edit().putFloat(key, angle).apply()
        }

        private fun ensureShader() {
            val current = shader
            val reload = reloadPhoto && item.source is WallpaperSource.Photo
            reloadPhoto = false
            if (current != null && current.item.id == item.id && !reload) {
                if (current.isReady && video == null && loadingId == null) startVideo(item)
                if (current.isReady || loadingId == item.id) return
            }
            val target = item
            if (videoItemId != null && videoItemId != target.id) stopVideo()
            val created = WallpaperShader.create(target)
            val clip = target.source as? WallpaperSource.Video
            if (clip != null) {
                loadingId = target.id
                loader.execute {
                    val poster = runCatching { WallpaperShader.decodePoster(resources, clip) }.getOrNull()
                    val needsDownload = !VideoCache.isCached(ctx, target)
                    mainHandler.post {
                        if (loadingId != target.id || item.id != target.id) return@post
                        loadingId = null
                        poster?.let { created.setVideoFrame(it) }
                        swapShader(created)
                        startVideo(target)
                    }
                    if (needsDownload && VideoCache.download(ctx, target) { }) {
                        mainHandler.post {
                            if (item.id == target.id) startVideo(target)
                        }
                    }
                }
                return
            }
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

            val handingOff = handoffWeight() > 0f
            oddFrame = !oddFrame
            if (hingeMoving || settling || handingOff || !motion || oddFrame) drawFrame()
            if (hingeMoving || settling || handingOff || motion || foldMotion.isActive(SystemClock.uptimeMillis())) requestFrame()
        }

        /** Use the current hinge pose even when this surface became available late. */
        private fun effectAngle(s: SurfaceSpec): Float {
            return foldMotion.degrees(s.isInner, SystemClock.uptimeMillis())
        }

        private fun targetAngle(): Float {
            val raw = hinge.angle
            if (!raw.isNaN()) return raw
            return if (spec?.isInner == false) 0f else 180f
        }

        private fun timeSec(): Float =
            if (motion) ((SystemClock.uptimeMillis() - startMs) % 100_000_000L) / 1000f else STILL_TIME

        private fun drawFrame() {
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                val ws = shader
                val s = specFor(canvas.width, canvas.height)
                if (ws != null && ws.isReady && s != null) {
                    val angle = effectAngle(s)
                    both?.shownDegrees = angle
                    // The overlay copies this flat surface beneath transparent Home icons.
                    val wallpaperFold = foldEffect && (isPreview || FoldAccessibilityService.instance?.coversUnlockedScreen != true)
                    ws.bind(s, Math.toRadians(angle.toDouble()).toFloat(), timeSec(), effect, intensity, motion, wallpaperFold)
                    paint.shader = ws.shader
                    canvas.drawRect(0f, 0f, s.width.toFloat(), s.height.toFloat(), paint)
                    drawnSpec = s
                } else {
                    drawnSpec = null
                    canvas.drawColor(Color.BLACK)
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        /** Copy only our visible, fully drawn surface; never scale a previous panel's buffer. */
        @Suppress("DEPRECATION")
        internal fun copyBackground(bounds: Rect, onResult: (Bitmap?) -> Unit) {
            val display = displayContext?.display
            val metrics = DisplayMetrics().also { display?.getRealMetrics(it) }
            val holder = surfaceHolder
            val drawn = drawnSpec
            val frame = Rect(holder.surfaceFrame)
            if (backgroundEngine !== this || !visible || isPreview || display?.state != Display.STATE_ON ||
                drawn == null || !holder.surface.isValid ||
                drawn.width != metrics.widthPixels || drawn.height != metrics.heightPixels ||
                frame.width() != drawn.width || frame.height() != drawn.height ||
                bounds.isEmpty || !frame.contains(bounds)) {
                onResult(null)
                return
            }
            val generation = surfaceGeneration
            val wallpaperId = item.id
            val image = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(holder.surface, Rect(bounds), image, { result ->
                    if (result == PixelCopy.SUCCESS && backgroundEngine === this && visible &&
                        surfaceGeneration == generation && item.id == wallpaperId && drawnSpec == drawn) {
                        onResult(image)
                    } else {
                        image.recycle()
                        onResult(null)
                    }
                }, mainHandler)
            } catch (error: IllegalArgumentException) {
                image.recycle()
                onResult(null)
            }
        }

        /** Crop follows the buffer we are actually drawing, so a cover never keeps the inner zoom. */
        private fun specFor(width: Int, height: Int): SurfaceSpec? {
            if (width <= 0 || height <= 0) return spec
            val current = spec
            if (current != null && current.width == width && current.height == height) return current
            val rotation = 0
            val (nw, nh) = FoldOptics.naturalSize(width, height, rotation)
            val inner = FoldOptics.isInnerDisplay(nw, nh)
            val innerW = prefs.getInt(Repo.KEY_INNER_W, Repo.DEFAULT_INNER_W)
            val innerH = prefs.getInt(Repo.KEY_INNER_H, Repo.DEFAULT_INNER_H)
            val coverW = prefs.getInt(Repo.KEY_COVER_W, Repo.DEFAULT_COVER_W)
            val coverH = prefs.getInt(Repo.KEY_COVER_H, Repo.DEFAULT_COVER_H)
            val surfaceHeight = if (inner) nh / (nw * 0.5f) else nh.toFloat() / nw
            val next = SurfaceSpec(
                width, height, rotation, inner, surfaceHeight,
                FoldOptics.planeHeight(innerW, innerH, coverW, coverH)
            )
            spec = next
            return next
        }
    }

    companion object {
        // Wallpaper and Accessibility services share the main thread and the app process.
        private var backgroundEngine: FoldEngine? = null

        internal fun captureBackground(bounds: Rect, onResult: (Bitmap?) -> Unit) {
            val engine = backgroundEngine
            if (engine == null) onResult(null) else engine.copyBackground(bounds, onResult)
        }

        /** Scene time used when living motion is off. */
        const val STILL_TIME = 42f

        /** Hinge travel that counts as the user moving the hinge. */
        const val SETTLE_MOVE_DEG = 3f

        /** Stillness after which a half-open posture counts as settled. */
        const val SETTLE_DELAY_MS = 250L

        /** Ease from the effect midpoint to the hinge state after a display switch. */
        const val HANDOFF_MS = 160L

        private const val TAG = "Lumifold"
    }
}
