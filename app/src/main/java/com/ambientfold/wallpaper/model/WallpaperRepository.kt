package com.ambientfold.wallpaper.model

import android.content.Context
import android.content.SharedPreferences
import com.ambientfold.wallpaper.R

object WallpaperRepository {

    const val PREFS_NAME = "ambient_fold_prefs"
    const val KEY_ACTIVE_WALLPAPER = "active_wallpaper_id"
    const val KEY_MAX_BLUR_RADIUS = "max_blur_radius"
    const val DEFAULT_MAX_BLUR = 42.0f

    val wallpapers: List<WallpaperItem> = listOf(
        // Landscapes
        WallpaperItem(
            id = "aurora_landscape",
            title = "Cyber Aurora",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.bg_aurora_landscape,
            description = "Ethereal northern lights over nocturnal mountain ridges."
        ),
        WallpaperItem(
            id = "dunes_sunset",
            title = "Dunes Horizon",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.bg_dunes_sunset,
            description = "Warm desert ridges under a sunset atmosphere."
        ),

        // Geometric
        WallpaperItem(
            id = "geometric_prism",
            title = "Prismatic Crystal",
            category = WallpaperCategory.GEOMETRIC,
            isDark = true,
            drawableResId = R.drawable.bg_geometric_prism,
            description = "Faceted crystalline geometry with neon refractions."
        ),
        WallpaperItem(
            id = "geometric_matrix",
            title = "Material Matrix",
            category = WallpaperCategory.GEOMETRIC,
            isDark = false,
            drawableResId = R.drawable.bg_geometric_matrix,
            description = "Crisp polygonal planes in a bright modern palette."
        ),

        // Minimal
        WallpaperItem(
            id = "minimal_dark",
            title = "OLED Ambient Rings",
            category = WallpaperCategory.MINIMAL,
            isDark = true,
            drawableResId = R.drawable.bg_minimalist_dark,
            description = "Deep blacks with subtle concentric ring accents."
        ),
        WallpaperItem(
            id = "minimal_light",
            title = "Clean Polar",
            category = WallpaperCategory.MINIMAL,
            isDark = false,
            drawableResId = R.drawable.bg_minimalist_light,
            description = "Minimalist light canvas with faint orbital geometry."
        )
    )

    fun resolveWallpaper(candidateId: String?): WallpaperItem {
        if (candidateId.isNullOrBlank()) return wallpapers.first()
        return wallpapers.firstOrNull { it.id == candidateId } ?: wallpapers.first()
    }

    fun getWallpaperById(id: String): WallpaperItem? {
        return wallpapers.firstOrNull { it.id == id }
    }

    fun getWallpapersByCategory(category: WallpaperCategory): List<WallpaperItem> {
        return wallpapers.filter { it.category == category }
    }

    fun getActiveWallpaper(context: Context): WallpaperItem {
        val prefs = getPrefs(context)
        val activeId = prefs.getString(KEY_ACTIVE_WALLPAPER, wallpapers.first().id)
        return resolveWallpaper(activeId)
    }

    fun setActiveWallpaper(context: Context, wallpaperId: String) {
        getPrefs(context).edit().putString(KEY_ACTIVE_WALLPAPER, wallpaperId).apply()
    }

    fun getMaxBlurRadius(context: Context): Float {
        return getPrefs(context).getFloat(KEY_MAX_BLUR_RADIUS, DEFAULT_MAX_BLUR)
    }

    fun setMaxBlurRadius(context: Context, radius: Float) {
        getPrefs(context).edit().putFloat(KEY_MAX_BLUR_RADIUS, radius).apply()
    }

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
