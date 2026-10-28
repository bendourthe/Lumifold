package com.lumifold.wallpaper.service

import android.content.Context
import android.os.PowerManager
import com.lumifold.wallpaper.model.WallpaperRepository

/** Decides whether living motion runs, given the user's choice and Battery Saver. */
object MotionPolicy {

    enum class Pause { NONE, BATTERY_SAVER }

    fun pauseReason(pauseOnSaver: Boolean, powerSave: Boolean): Pause =
        if (pauseOnSaver && powerSave) Pause.BATTERY_SAVER else Pause.NONE

    fun currentPause(context: Context): Pause = pauseReason(
        WallpaperRepository.isPauseOnSaver(context),
        context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    )
}
