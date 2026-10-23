package com.ambientfold.wallpaper.model

import android.content.Context
import android.content.SharedPreferences
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
    const val EFFECT_LIQUID_GLASS = "prismatic_wave"
    const val EFFECT_APERTURE_HORIZON = "aperture_zoom"

    const val DEFAULT_MAX_BLUR = 65.0f
    const val DEFAULT_ANIMATION_DURATION = 600L
    const val DEFAULT_OPENING_EFFECT = EFFECT_DUO_BLUR

    val wallpapers: List<WallpaperItem> = listOf(
        // --- LANDSCAPES ---
        WallpaperItem(
            id = "mountain_sunset",
            title = "Alpine Glacier Dusk",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.wp_mountain_sunset,
            description = "High-resolution 4K mountain lake reflecting dramatic sunset peaks with subtle organic daylight breathing.",
            isDynamic = true,
            motionType = MotionType.LANDSCAPE_BREATHING,
            particleColor = 0xFFFFE082.toInt()
        ),
        WallpaperItem(
            id = "starry_galaxy",
            title = "Cosmic Milky Way",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.wp_starry_galaxy,
            description = "High-resolution 4K night sky over snowy alpine peaks with slow celestial starlight breathing.",
            isDynamic = true,
            motionType = MotionType.LANDSCAPE_BREATHING,
            particleColor = 0xFF80D8FF.toInt()
        ),
        WallpaperItem(
            id = "misty_forest",
            title = "Misty Emerald Pines",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.wp_misty_forest,
            description = "High-resolution 4K Pacific Northwest forest with drifting morning fog and atmospheric canopy glow.",
            isDynamic = true,
            motionType = MotionType.LANDSCAPE_BREATHING,
            particleColor = 0xFFE0F2F1.toInt()
        ),
        WallpaperItem(
            id = "ocean_golden",
            title = "Golden Horizon Ocean",
            category = WallpaperCategory.LANDSCAPES,
            isDark = false,
            drawableResId = R.drawable.wp_ocean_golden,
            description = "High-resolution 4K coastal wave at golden hour with shimmering ambient light rays and sea mist.",
            isDynamic = true,
            motionType = MotionType.LANDSCAPE_BREATHING,
            particleColor = 0xFFFFD54F.toInt()
        ),
        WallpaperItem(
            id = "aurora_borealis",
            title = "Northern Lights",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.wp_aurora_borealis,
            description = "High-resolution 4K emerald and violet aurora borealis flowing over Arctic snowy peaks.",
            isDynamic = true,
            motionType = MotionType.LANDSCAPE_BREATHING,
            particleColor = 0xFF69F0AE.toInt()
        ),
        WallpaperItem(
            id = "desert_dunes",
            title = "Sahara Sunset Dunes",
            category = WallpaperCategory.LANDSCAPES,
            isDark = false,
            drawableResId = R.drawable.wp_desert_dunes,
            description = "High-resolution 4K sweeping sand dunes glowing under warm golden hour sunlight with gentle heat shimmer.",
            isDynamic = true,
            motionType = MotionType.LANDSCAPE_BREATHING,
            particleColor = 0xFFFFAB40.toInt()
        ),
        WallpaperItem(
            id = "canyon_river",
            title = "Grand Canyon Horizon",
            category = WallpaperCategory.LANDSCAPES,
            isDark = false,
            drawableResId = R.drawable.wp_canyon_river,
            description = "High-resolution 4K rugged red rock canyon vista with luminous twilight atmospheric haze.",
            isDynamic = true,
            motionType = MotionType.LANDSCAPE_BREATHING,
            particleColor = 0xFFFF7043.toInt()
        ),
        WallpaperItem(
            id = "lake_reflections",
            title = "Emerald Alpine Lake",
            category = WallpaperCategory.LANDSCAPES,
            isDark = true,
            drawableResId = R.drawable.wp_lake_reflections,
            description = "High-resolution 4K mirror-calm mountain lake reflecting evergreen forest and towering granite peaks.",
            isDynamic = true,
            motionType = MotionType.LANDSCAPE_BREATHING,
            particleColor = 0xFF4DD0E1.toInt()
        ),

        // --- ABSTRACT FLOW ---
        WallpaperItem(
            id = "liquid_glass",
            title = "Liquid Glass Iridescence",
            category = WallpaperCategory.ABSTRACT_DYNAMIC,
            isDark = true,
            drawableResId = R.drawable.wp_liquid_glass,
            description = "Dynamic continuous liquid glass flowing wave with chromatic light refraction and smooth fluid motion.",
            isDynamic = true,
            motionType = MotionType.LIQUID_GLASS,
            particleColor = 0xFF818CF8.toInt()
        ),
        WallpaperItem(
            id = "dark_prism",
            title = "Obsidian Prism",
            category = WallpaperCategory.ABSTRACT_DYNAMIC,
            isDark = true,
            drawableResId = R.drawable.wp_dark_prism,
            description = "Ultra-sleek dark geometric glassmorphism with refractive chromatic light ribbons flowing smoothly.",
            isDynamic = true,
            motionType = MotionType.LIQUID_GLASS,
            particleColor = 0xFFE040FB.toInt()
        ),
        WallpaperItem(
            id = "fluid_chrome",
            title = "Fluid Mercury Chrome",
            category = WallpaperCategory.ABSTRACT_DYNAMIC,
            isDark = true,
            drawableResId = R.drawable.wp_fluid_chrome,
            description = "Continuous metallic fluid motion with liquid chrome reflections and specular glass curvature.",
            isDynamic = true,
            motionType = MotionType.LIQUID_GLASS,
            particleColor = 0xFFE2E8F0.toInt()
        ),
        WallpaperItem(
            id = "prismatic_silk",
            title = "Prismatic Silk Wave",
            category = WallpaperCategory.ABSTRACT_DYNAMIC,
            isDark = true,
            drawableResId = R.drawable.wp_prismatic_silk,
            description = "Fluid iridescent silk waves undulating like molten crystal in constant smooth harmonic motion.",
            isDynamic = true,
            motionType = MotionType.LIQUID_GLASS,
            particleColor = 0xFFF472B6.toInt()
        ),
        WallpaperItem(
            id = "neon_nebula",
            title = "Cyberpunk Aurora Nebula",
            category = WallpaperCategory.ABSTRACT_DYNAMIC,
            isDark = true,
            drawableResId = R.drawable.wp_neon_nebula,
            description = "High-energy electric cyan and magenta cosmic plasma flowing in slow undulating streams.",
            isDynamic = true,
            motionType = MotionType.LIQUID_GLASS,
            particleColor = 0xFF22D3EE.toInt()
        ),

        // --- TEXTURES ---
        WallpaperItem(
            id = "carbon_fiber",
            title = "Dark Carbon Fiber",
            category = WallpaperCategory.TEXTURES,
            isDark = true,
            drawableResId = R.drawable.wp_carbon_fiber,
            description = "Aerospace-grade woven carbon fiber with realistic depth and subtle directional light reflections.",
            isDynamic = true,
            motionType = MotionType.TEXTURE_SHIMMER,
            particleColor = 0xFF94A3B8.toInt()
        ),
        WallpaperItem(
            id = "brushed_titanium",
            title = "Brushed Titanium Minimal",
            category = WallpaperCategory.TEXTURES,
            isDark = true,
            drawableResId = R.drawable.wp_brushed_titanium,
            description = "Industrial precision machined metal surface with soft anisotropic specular light sweep.",
            isDynamic = true,
            motionType = MotionType.TEXTURE_SHIMMER,
            particleColor = 0xFFCBD5E1.toInt()
        ),
        WallpaperItem(
            id = "frosted_glass",
            title = "Architectural Fluted Glass",
            category = WallpaperCategory.TEXTURES,
            isDark = true,
            drawableResId = R.drawable.wp_frosted_glass,
            description = "Ribbed frosted glass panels with subtle ambient backlight shifts through vertical fluting.",
            isDynamic = true,
            motionType = MotionType.TEXTURE_SHIMMER,
            particleColor = 0xFFA5F3FC.toInt()
        ),
        WallpaperItem(
            id = "topographic_gold",
            title = "Topographic Obsidian Gold",
            category = WallpaperCategory.TEXTURES,
            isDark = true,
            drawableResId = R.drawable.wp_topographic_gold,
            description = "Matte black volcanic stone etched with glowing golden contour elevation lines and subtle shimmer.",
            isDynamic = true,
            motionType = MotionType.TEXTURE_SHIMMER,
            particleColor = 0xFFFBBF24.toInt()
        ),
        WallpaperItem(
            id = "silk_drape",
            title = "Minimal Dark Silk",
            category = WallpaperCategory.TEXTURES,
            isDark = true,
            drawableResId = R.drawable.wp_silk_drape,
            description = "Soft folds of deep midnight fabric with subtle velvet highlights flowing over surface contours.",
            isDynamic = true,
            motionType = MotionType.TEXTURE_SHIMMER,
            particleColor = 0xFFE2E8F0.toInt()
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
