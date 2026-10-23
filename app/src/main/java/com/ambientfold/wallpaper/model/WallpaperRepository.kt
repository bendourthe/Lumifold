package com.ambientfold.wallpaper.model

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.ambientfold.wallpaper.R

object WallpaperRepository {

    const val PREFS_NAME = "ambient_fold_wallpaper_prefs"
    const val KEY_ACTIVE_WALLPAPER_ID = "pref_active_wallpaper_id"
    const val KEY_MAX_BLUR_RADIUS = "pref_max_blur_radius"
    const val KEY_OPENING_EFFECT = "pref_opening_effect"
    const val KEY_DYNAMIC_MOTION_ENABLED = "pref_dynamic_motion_enabled"
    const val KEY_ANIMATION_DURATION_MS = "pref_animation_duration_ms"

    const val EFFECT_DUO_BLUR = "duo_blur"
    const val EFFECT_CREASE_BLOOM = "crease_bloom"
    const val EFFECT_APERTURE_ZOOM = "aperture_zoom"
    const val EFFECT_PRISMATIC_WAVE = "prismatic_wave"

    const val DEFAULT_MAX_BLUR = 65.0f
    const val DEFAULT_ANIMATION_DURATION = 600L
    const val DEFAULT_OPENING_EFFECT = EFFECT_DUO_BLUR

    val wallpapers: List<WallpaperItem> = listOf(
        WallpaperItem(
            id = "mountain_sunset",
            title = "Alpine Glacier Dusk",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.wp_mountain_sunset,
            description = "High-resolution 4K mountain lake reflecting dramatic sunset peaks with floating starlight.",
            isDynamic = true,
            particleColor = Color.parseColor("#FFE082")
        ),
        WallpaperItem(
            id = "starry_galaxy",
            title = "Cosmic Milky Way",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.wp_starry_galaxy,
            description = "High-resolution 4K night sky over snowy alpine peaks with shimmering celestial dust.",
            isDynamic = true,
            particleColor = Color.parseColor("#80D8FF")
        ),
        WallpaperItem(
            id = "misty_forest",
            title = "Misty Emerald Pines",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.wp_misty_forest,
            description = "High-resolution 4K Pacific Northwest forest with drifting morning fog and atmospheric motes.",
            isDynamic = true,
            particleColor = Color.parseColor("#E0F2F1")
        ),
        WallpaperItem(
            id = "ocean_golden",
            title = "Golden Horizon Ocean",
            category = WallpaperCategory.LANDSCAPES,
            isDark = false,
            drawableResId = R.drawable.wp_ocean_golden,
            description = "High-resolution 4K coastal wave at golden hour with shimmering ambient light rays.",
            isDynamic = true,
            particleColor = Color.parseColor("#FFD54F")
        ),
        WallpaperItem(
            id = "aurora_borealis",
            title = "Northern Lights",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.wp_aurora_borealis,
            description = "High-resolution 4K emerald and violet aurora borealis flowing over snowy peaks.",
            isDynamic = true,
            particleColor = Color.parseColor("#69F0AE")
        ),
        WallpaperItem(
            id = "dark_prism",
            title = "Obsidian Prism",
            category = WallpaperCategory.ABSTRACT_DYNAMIC,
            isDark = true,
            drawableResId = R.drawable.wp_dark_prism,
            description = "Ultra-sleek dark geometric glassmorphism with refractive chromatic light ribbons.",
            isDynamic = true,
            particleColor = Color.parseColor("#E040FB")
        )
    )

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getActiveWallpaper(context: Context): WallpaperItem {
        val savedId = getPrefs(context).getString(KEY_ACTIVE_WALLPAPER_ID, null)
        return resolveWallpaper(savedId)
    }

    fun setActiveWallpaper(context: Context, wallpaperId: String) {
        getPrefs(context).edit().putString(KEY_ACTIVE_WALLPAPER_ID, wallpaperId).apply()
    }

    fun getOpeningEffect(context: Context): String {
        return getPrefs(context).getString(KEY_OPENING_EFFECT, DEFAULT_OPENING_EFFECT) ?: DEFAULT_OPENING_EFFECT
    }

    fun setOpeningEffect(context: Context, effect: String) {
        getPrefs(context).edit().putString(KEY_OPENING_EFFECT, effect).apply()
    }

    fun isDynamicMotionEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DYNAMIC_MOTION_ENABLED, true)
    }

    fun setDynamicMotionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DYNAMIC_MOTION_ENABLED, enabled).apply()
    }

    fun getMaxBlurRadius(context: Context): Float {
        return getPrefs(context).getFloat(KEY_MAX_BLUR_RADIUS, DEFAULT_MAX_BLUR)
    }

    fun setMaxBlurRadius(context: Context, radius: Float) {
        getPrefs(context).edit().putFloat(KEY_MAX_BLUR_RADIUS, radius.coerceIn(20f, 120f)).apply()
    }

    fun getAnimationDuration(context: Context): Long {
        return getPrefs(context).getLong(KEY_ANIMATION_DURATION_MS, DEFAULT_ANIMATION_DURATION)
    }

    fun setAnimationDuration(context: Context, durationMs: Long) {
        getPrefs(context).edit().putLong(KEY_ANIMATION_DURATION_MS, durationMs).apply()
    }

    fun resolveWallpaper(id: String?): WallpaperItem {
        if (id.isNullOrBlank()) return wallpapers.first()
        return wallpapers.firstOrNull { it.id == id } ?: wallpapers.first()
    }

    fun getWallpaperById(id: String): WallpaperItem? {
        return wallpapers.firstOrNull { it.id == id }
    }

    fun getWallpapersByCategory(category: WallpaperCategory): List<WallpaperItem> {
        return wallpapers.filter { it.category == category }
    }
}
