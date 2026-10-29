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
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.lumifold.wallpaper.model.WallpaperRepository as Repo
import com.lumifold.wallpaper.render.FoldOptics
import kotlin.math.abs

/**
 * Follows the hinge, holds the cover until Android's panel switch over apps, and dims the
 * unlocked app on screen while folding. It takes no screenshots and reads no app content.
 */
class FoldAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var glass: FoldGlass? = null
    private var hinge: HingeTracker? = null
    private val motion = FoldMotion()
    private var registered = false
    private val diagnostics by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    private var lastRoute: String? = null
    private var lastHingeLog = 0L
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
        // Over Home the wallpaper draws the effect live, so the overlay stays off. Decided once per fold, because the wallpaper hides for a
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

    private companion object {
        const val TAG = "LumifoldGlass"
    }
}
