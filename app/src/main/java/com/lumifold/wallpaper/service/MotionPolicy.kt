package com.lumifold.wallpaper.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.lumifold.wallpaper.model.WallpaperRepository

/** Decides whether living motion runs, given the user's choice and the battery rules. */
object MotionPolicy {

    enum class Pause { NONE, BATTERY_SAVER, LOW_BATTERY }

    data class Battery(val level: Int, val charging: Boolean, val powerSave: Boolean)

    fun pauseReason(
        pauseOnSaver: Boolean,
        pauseBelowLevel: Boolean,
        threshold: Int,
        battery: Battery
    ): Pause = when {
        pauseOnSaver && battery.powerSave -> Pause.BATTERY_SAVER
        pauseBelowLevel && !battery.charging && battery.level in 0..threshold -> Pause.LOW_BATTERY
        else -> Pause.NONE
    }

    /** Reads the battery from the sticky ACTION_BATTERY_CHANGED broadcast. */
    fun readBattery(context: Context): Battery {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        return Battery(if (level < 0 || scale <= 0) -1 else level * 100 / scale, charging, powerSave)
    }

    fun currentPause(context: Context): Pause = pauseReason(
        WallpaperRepository.isPauseOnSaver(context),
        WallpaperRepository.isPauseBelowLevel(context),
        WallpaperRepository.getPauseLevel(context),
        readBattery(context)
    )
}
