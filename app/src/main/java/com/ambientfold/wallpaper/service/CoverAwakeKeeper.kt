package com.ambientfold.wallpaper.service

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps the cover display awake while the user opens the hinge.
 *
 * Android 16 book-style foldables (BookStyleDeviceStatePolicy) stay in the CLOSED device state up
 * to about 90 degrees while any screen wake lock is held (screen timeout policy
 * KEEP_DISPLAY_ON). Without one, the system switches to the inner display as soon as the hinge
 * moves. The lock is armed for at most the user's own screen timeout and re-armed only by user
 * activity on the wallpaper, so it never keeps the screen on longer than normal.
 */
class CoverAwakeKeeper(private val context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    @Suppress("DEPRECATION") // The only screen wake lock level available to apps.
    private val wakeLock = powerManager.newWakeLock(
        PowerManager.SCREEN_DIM_WAKE_LOCK,
        "DuoBlur:CoverAwake"
    ).apply { setReferenceCounted(false) }

    private var wanted = false

    val isInteractive: Boolean get() = powerManager.isInteractive

    fun update(shouldHold: Boolean) {
        if (shouldHold == wanted) return
        wanted = shouldHold
        if (shouldHold) arm() else release()
    }

    fun onUserActivity() {
        if (wanted) arm()
    }

    fun release() {
        wanted = false
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun arm() {
        val timeout = Settings.System.getLong(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            DEFAULT_SCREEN_TIMEOUT_MS
        )
        wakeLock.acquire(holdTimeoutMs(timeout, keyguardManager?.isKeyguardLocked == true))
    }

    companion object {
        const val DEFAULT_SCREEN_TIMEOUT_MS = 30_000L
        const val LOCK_SCREEN_CAP_MS = 10_000L
        const val MIN_HOLD_MS = 3_000L
        const val MAX_HOLD_MS = 10 * 60_000L

        /** Hinge angle past which the cover faces away from the user. */
        const val MAX_HOLD_ANGLE = 120f

        /**
         * One second shorter than the user's screen timeout so the system dims and sleeps on its
         * usual schedule; capped on the lock screen, whose own timeout is short.
         */
        fun holdTimeoutMs(screenOffTimeoutMs: Long, keyguardLocked: Boolean): Long {
            val base = if (screenOffTimeoutMs <= 0) DEFAULT_SCREEN_TIMEOUT_MS else screenOffTimeoutMs
            val capped = if (keyguardLocked) min(base, LOCK_SCREEN_CAP_MS) else base
            return max(MIN_HOLD_MS, capped - 1_000L).coerceAtMost(MAX_HOLD_MS)
        }

        fun shouldHold(
            enabled: Boolean,
            visible: Boolean,
            onInnerDisplay: Boolean,
            isPreview: Boolean,
            interactive: Boolean,
            hingeAngle: Float
        ): Boolean = enabled && visible && !onInnerDisplay && !isPreview && interactive &&
            hingeAngle < MAX_HOLD_ANGLE
    }
}
