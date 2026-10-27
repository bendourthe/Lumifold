package com.lumifold.wallpaper.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Android's "Continue using apps on fold" setting.
 * Always keeps the front screen awake as the phone folds. Swipe up is the system default.
 */
object FoldLockSetting {

    const val KEY = "fold_lock_behavior_setting"
    const val ALWAYS = "stay_awake_on_fold_key"
    const val SWIPE_UP = "selective_stay_awake_key"

    fun isAlways(context: Context): Boolean =
        Settings.System.getString(context.contentResolver, KEY) == ALWAYS

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    /**
     * Returns false when the change is not allowed. Recent Android versions keep this setting in
     * secure settings and reject the write even with the change-system-settings permission.
     */
    fun setAlways(context: Context, always: Boolean): Boolean {
        if (!canWrite(context)) return false
        return runCatching {
            Settings.System.putString(context.contentResolver, KEY, if (always) ALWAYS else SWIPE_UP)
        }.getOrDefault(false)
    }

    fun requestWritePermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
