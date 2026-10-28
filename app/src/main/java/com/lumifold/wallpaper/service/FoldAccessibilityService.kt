package com.lumifold.wallpaper.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.lumifold.wallpaper.model.WallpaperRepository as Repo
import com.lumifold.wallpaper.render.FoldOptics
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Follows the hinge and applies the opening effect to the whole unlocked screen.
 * It takes one picture at the start of a fold and drops it when the hinge stops. It does not
 * record.
 */
class FoldAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val shots = Executors.newSingleThreadExecutor()
    private var glass: FoldGlass? = null
    private var hinge: HingeTracker? = null
    private val motion = FoldMotion()
    private var registered = false
    private val diagnostics by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    private var lastRoute: String? = null
    private var lastHingeLog = 0L
    private val captureThrottle = WindowCaptureThrottle()
    // The wallpaper engine holds the cover only while the wallpaper is visible. This hold covers
    // apps, which hide it, so opening over an app keeps the cover until 90 degrees like Home.
    private var awake: CoverAwakeKeeper? = null
    private var holding = false
    private var touchWatch: View? = null
    private var previousHinge = Float.NaN
    private var closingHinge = false
    private var wasActive = false
    private var overWallpaper = false
    private var lastInner: Boolean? = null
    private val prefs by lazy { Repo.getPrefs(this) }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Repo.KEY_FOLD_EFFECT_ENABLED) updateAwake()
    }
    val coversUnlockedScreen: Boolean
        get() = Settings.canDrawOverlays(this) && getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true

    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private val frame = Runnable { render() }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            updateAwake()
            render()
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (diagnostics) Log.d(TAG, "Screen event=${intent.action}")
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                main.removeCallbacks(frame)
                hinge?.stop()
                motion.reset()
                glass?.hide()
            } else hinge?.start()
            updateAwake()
        }
    }

    override fun onServiceConnected() {
        instance = this
        stopService(Intent(this, ScreenCaptureService::class.java))
        hinge?.stop()
        glass?.hide()
        glass = FoldGlass(this)
        awake?.release()
        awake = CoverAwakeKeeper(this)
        holding = false
        if (!registered) {
            displays?.registerDisplayListener(displayListener, main)
            ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            registered = true
        }
        addTouchWatch()
        hinge = HingeTracker(this) {
            val now = SystemClock.uptimeMillis()
            if (diagnostics && now - lastHingeLog >= 100L) {
                lastHingeLog = now
                Log.d(TAG, "Hinge angle=$it uptime=$now")
            }
            if (!it.isNaN() && !previousHinge.isNaN() && abs(it - previousHinge) > 1f) {
                closingHinge = it < previousHinge
            }
            if (!it.isNaN()) previousHinge = it
            motion.sample(it, now)
            updateAwake()
            render()
        }.also { it.start() }
        updateAwake()
        Log.i(TAG, "Accessibility connected; hinge=${hinge?.isAvailable}")
    }

    /** Applies the wallpaper engine's cover-hold rule to whatever unlocked screen is showing. */
    @Suppress("DEPRECATION")
    private fun updateAwake() {
        val keeper = awake ?: return
        val display = displays?.getDisplay(Display.DEFAULT_DISPLAY)
        val unlocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false
        val inner = display?.let {
            val metrics = DisplayMetrics().also(it::getRealMetrics)
            FoldOptics.isInnerDisplay(metrics.widthPixels, metrics.heightPixels)
        } == true
        // The lock screen shows the wallpaper, whose engine already holds the cover there.
        val hold = CoverAwakeKeeper.shouldHold(
            enabled = Repo.isCoverAwakeEnabled(this),
            visible = unlocked && display?.state == Display.STATE_ON,
            onInnerDisplay = inner,
            isPreview = false,
            interactive = keeper.isInteractive,
            hingeAngle = hinge?.angle?.takeUnless { it.isNaN() } ?: 0f,
            closing = closingHinge
        )
        keeper.update(hold)
        if (diagnostics && hold != holding) Log.d(TAG, "Cover hold=$hold inner=$inner unlocked=$unlocked")
        holding = hold
    }

    /**
     * A transparent 1x1 overlay that receives ACTION_OUTSIDE for every touch on the screen, with
     * no coordinates for other apps. It re-arms the cover hold the way the wallpaper's own touch
     * events do on Home, so the hold never outlasts the user's screen timeout.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun addTouchWatch() {
        if (touchWatch != null) return
        val view = View(this).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    updateAwake()
                    awake?.onUserActivity()
                }
                false
            }
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        ).apply {
            // The corner pixel sits inside the rounded display corner, where no app takes touches.
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        runCatching { getSystemService(WindowManager::class.java).addView(view, params) }
            .onSuccess { touchWatch = view }
            .onFailure { Log.w(TAG, "Touch watch unavailable", it) }
    }

    @Suppress("DEPRECATION")
    private fun render() {
        main.removeCallbacks(frame)
        val display = displays?.getDisplay(Display.DEFAULT_DISPLAY)
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked
        if (display == null || display.state != Display.STATE_ON ||
            interactive == false || locked == true) {
            traceRoute("blocked display=${display?.state} interactive=$interactive locked=$locked")
            glass?.hide()
            return
        }
        val metrics = DisplayMetrics().also { display.getRealMetrics(it) }
        val inner = FoldOptics.isInnerDisplay(metrics.widthPixels, metrics.heightPixels)
        val now = SystemClock.uptimeMillis()
        if (inner != lastInner) {
            lastInner = inner
            motion.panelShown(now)
        }
        val active = motion.isActive(now)
        // Over Home the wallpaper draws the effect live; a screenshot on top would double it and
        // freeze a video wallpaper. Decided once per fold, because the wallpaper hides for a
        // moment while the panels switch.
        if (active && !wasActive) overWallpaper = FoldWallpaperService.isWallpaperShowing
        wasActive = active
        // The overlay itself emits window events. It must not be mistaken for a foreground app.
        val show = Repo.isFoldEffectEnabled(this) && active && !overWallpaper
        traceRoute("panel=${metrics.widthPixels}x${metrics.heightPixels} inner=$inner enabled=${Repo.isFoldEffectEnabled(this)} active=$active wallpaper=$overWallpaper show=$show")
        glass?.update(show, motion.degrees(inner, now), inner, Repo.getOpeningEffect(this), Repo.getIntensity(this))
        if (motion.isActive(now)) main.postDelayed(frame, 16L)
    }

    override fun onDestroy() {
        hinge?.stop()
        glass?.hide()
        awake?.release()
        touchWatch?.let { view -> runCatching { getSystemService(WindowManager::class.java).removeView(view) } }
        touchWatch = null
        main.removeCallbacksAndMessages(null)
        if (registered) {
            displays?.unregisterDisplayListener(displayListener)
            unregisterReceiver(screenReceiver)
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            registered = false
        }
        if (instance === this) instance = null
        // Already queued composition jobs own bitmaps and must run their cleanup.
        shots.shutdown()
        super.onDestroy()
    }

    // Unlocking by face or fingerprint sends no USER_PRESENT here, but it changes the visible
    // window. Re-checking only takes a hold released while asleep or locked; it never extends one.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = updateAwake()

    override fun onInterrupt() { glass?.hide() }

    private fun traceRoute(route: String) {
        if (diagnostics && route != lastRoute) {
            lastRoute = route
            Log.d(TAG, "Route $route")
        }
    }

    @Suppress("DEPRECATION")
    private fun captureLayout(): List<CaptureWindow>? = runCatching {
        val display = displays?.getDisplay(Display.DEFAULT_DISPLAY) ?: return@runCatching null
        if (display.state != Display.STATE_ON) return@runCatching null
        val metrics = DisplayMetrics().also { display.getRealMetrics(it) }
        val applications = windows.filter {
            it.displayId == Display.DEFAULT_DISPLAY && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
        }.map {
            val bounds = Rect().also(it::getBoundsInScreen)
            CaptureWindow(it.id, bounds.left, bounds.top, bounds.right, bounds.bottom, it.isActive)
        }
        selectCaptureWindows(applications, metrics.widthPixels, metrics.heightPixels)
    }.getOrNull()

    /** Returns a main-thread cancellation action that releases incomplete captures. */
    fun capture(
        isCurrent: () -> Boolean,
        onFrame: (Bitmap, Rect) -> Unit,
        onMiss: (retryAfterMs: Long?) -> Unit
    ): () -> Unit {
        // Window capture excludes our overlay. Inspect metadata only, never app nodes or text.
        if (Build.VERSION.SDK_INT < 34 || !isCurrent() || !coversUnlockedScreen ||
            !motion.isActive(SystemClock.uptimeMillis())) {
            onMiss(null)
            return {}
        }
        val targets = captureLayout()
        if (targets == null) {
            if (diagnostics) Log.d(TAG, "Capture layout not ready; retaining fold shading")
            onMiss(16L)
            return {}
        }
        val delay = captureThrottle.delayMs(targets.map { it.id }, SystemClock.uptimeMillis())
        if (delay > 0L) {
            if (diagnostics) Log.d(TAG, "Window capture interval wait=${delay}ms")
            onMiss(delay)
            return {}
        }
        val bounds = Rect(0, targets.first().top, targets.last().right, targets.first().bottom)
        val placement = targets.map { it.copy(active = false) }
        val parts = mutableMapOf<Int, Bitmap>()
        var waitingForeground: Bitmap? = null
        var finished = false
        var cancelled = false

        fun current(): Boolean = instance === this && isCurrent() && coversUnlockedScreen &&
            motion.isActive(SystemClock.uptimeMillis()) &&
            captureLayout()?.map { it.copy(active = false) } == placement

        fun releaseParts() {
            parts.values.forEach { it.recycle() }
            parts.clear()
            waitingForeground?.recycle()
            waitingForeground = null
        }

        fun miss(retryAfter: Long?) {
            if (finished) return
            finished = true
            releaseParts()
            onMiss(retryAfter)
        }

        fun publish(image: Bitmap?, withWallpaper: Boolean = false) {
            if (cancelled) image?.recycle()
            else if (image == null || !current()) {
                image?.recycle()
                onMiss(16L)
            } else {
                if (diagnostics) Log.d(TAG, "Capture assembled panes=${targets.size} size=${image.width}x${image.height} wallpaper=$withWallpaper")
                onFrame(image, bounds)
            }
        }

        fun complete(image: Bitmap?, needsBackground: Boolean) {
            if (image == null || cancelled || !current() || !needsBackground) {
                publish(image)
                return
            }
            // A window screenshot contains Home icons but excludes the wallpaper underneath.
            // Complete the image before distortion; transparent output would duplicate the icons.
            waitingForeground = image
            FoldWallpaperService.captureBackground(bounds) { background ->
                val foreground = waitingForeground
                waitingForeground = null
                if (foreground == null) {
                    background?.recycle()
                } else if (background == null || !current()) {
                    foreground.recycle()
                    background?.recycle()
                    if (diagnostics) Log.d(TAG, "Wallpaper unavailable for transparent window; retaining fold shading")
                    if (!cancelled) onMiss(null)
                } else {
                    runCatching {
                        shots.execute {
                            val composed = try {
                                compositeWindowOnWallpaper(foreground, background)
                            } catch (error: Exception) {
                                background.recycle()
                                Log.w(TAG, "Wallpaper composition failed", error)
                                null
                            } finally { foreground.recycle() }
                            main.post { publish(composed, withWallpaper = true) }
                        }
                    }.onFailure {
                        foreground.recycle()
                        background.recycle()
                        onMiss(null)
                    }
                }
            }
        }

        fun receive(target: CaptureWindow, image: Bitmap?) {
            if (finished) {
                image?.recycle()
                return
            }
            if (image == null || image.width != target.width || image.height != target.height || !current()) {
                image?.recycle()
                miss(16L)
                return
            }
            parts[target.id] = image
            if (parts.size != targets.size) return
            finished = true
            val images = targets.map { parts.getValue(it.id) }
            parts.clear()
            // Assemble panes and inspect alpha off the UI thread, without scaling missing areas.
            runCatching {
                shots.execute {
                    var composed: Bitmap? = null
                    var needsBackground = false
                    try {
                        if (images.size == 1) composed = images.single()
                        else {
                            composed = createBitmap(bounds.width(), bounds.height())
                            val canvas = Canvas(composed)
                            targets.zip(images).forEach { (window, bitmap) ->
                                canvas.drawBitmap(bitmap, window.left.toFloat(), (window.top - bounds.top).toFloat(), null)
                            }
                        }
                        needsBackground = captureNeedsWallpaper(composed)
                    } catch (error: Exception) {
                        composed?.recycle()
                        composed = null
                        Log.w(TAG, "Window composition failed", error)
                    } finally {
                        if (images.size > 1) images.forEach { it.recycle() }
                    }
                    val ready = composed
                    main.post { complete(ready, needsBackground) }
                }
            }.onFailure {
                images.forEach { image -> image.recycle() }
                onMiss(null)
            }
        }

        for (target in targets) {
            if (finished) break
            val started = SystemClock.uptimeMillis()
            captureThrottle.record(target.id, started)
            if (diagnostics) Log.d(TAG, "Capture id=$started requested window=${target.id} bounds=$target panes=${targets.size}")
            try {
                takeScreenshotOfWindow(target.id, shots, object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val callbackMs = SystemClock.uptimeMillis() - started
                        val buffer = result.hardwareBuffer
                        val copy = try {
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB))
                            try { hardware?.copy(Bitmap.Config.ARGB_8888, false) } finally { hardware?.recycle() }
                        } catch (error: Exception) {
                            Log.w(TAG, "Screenshot conversion failed", error)
                            null
                        } finally { buffer.close() }
                        val usable = copy?.takeUnless {
                            isBlankScreenFrame(it.width, it.height, it::getPixel)
                        }
                        if (copy != null && usable == null) {
                            copy.recycle()
                            if (diagnostics) Log.d(TAG, "Blank window screenshot rejected window=${target.id}")
                        }
                        main.post {
                            if (diagnostics) Log.d(TAG, "Capture id=$started callbackMs=$callbackMs totalMs=${SystemClock.uptimeMillis() - started} size=${usable?.width}x${usable?.height}")
                            receive(target, usable)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Screenshot refused: code=$errorCode id=$started elapsedMs=${SystemClock.uptimeMillis() - started}")
                        main.post {
                            miss(if (errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ||
                                errorCode == ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) null else 16L)
                        }
                    }
                })
            } catch (error: Exception) {
                Log.w(TAG, "Screenshot unavailable", error)
                miss(null)
            }
        }
        return {
            cancelled = true
            finished = true
            releaseParts()
        }
    }

    companion object {
        private const val TAG = "LumifoldGlass"
        // Weak, so this static field never keeps a destroyed service and its context alive.
        @Volatile private var instanceRef: WeakReference<FoldAccessibilityService>? = null
        var instance: FoldAccessibilityService?
            get() = instanceRef?.get()
            private set(value) { instanceRef = value?.let { WeakReference(it) } }
    }
}
