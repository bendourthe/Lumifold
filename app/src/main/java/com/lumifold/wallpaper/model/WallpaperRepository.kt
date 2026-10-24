package com.lumifold.wallpaper.model

import android.content.Context
import android.content.SharedPreferences
import com.lumifold.wallpaper.R

object WallpaperRepository {

    const val PREFS_NAME = "lumifold_prefs"
    const val KEY_ACTIVE_WALLPAPER_ID = "pref_active_wallpaper_id"
    const val KEY_OPENING_EFFECT = "pref_opening_effect"
    const val KEY_EFFECT_INTENSITY = "pref_effect_intensity"
    const val KEY_DYNAMIC_MOTION_ENABLED = "pref_dynamic_motion_enabled"
    const val KEY_COVER_AWAKE = "pref_cover_awake"
    const val KEY_MEASURED_OPEN_SWITCH = "pref_measured_open_switch"
    const val KEY_MEASURED_CLOSE_SWITCH = "pref_measured_close_switch"
    const val KEY_INNER_W = "pref_inner_natural_w"
    const val KEY_INNER_H = "pref_inner_natural_h"
    const val KEY_COVER_W = "pref_cover_natural_w"
    const val KEY_COVER_H = "pref_cover_natural_h"

    const val DEFAULT_WALLPAPER_ID = "duo_ribbons"
    const val DEFAULT_INTENSITY = 1.0f
    const val MIN_INTENSITY = 0.25f
    const val MAX_INTENSITY = 1.5f

    // Pixel 9 Pro Fold natural sizes, used until the wallpaper has seen both displays.
    const val DEFAULT_INNER_W = 2076
    const val DEFAULT_INNER_H = 2152
    const val DEFAULT_COVER_W = 1080
    const val DEFAULT_COVER_H = 2424

    private fun gen(
        id: String,
        title: String,
        category: WallpaperCategory,
        scene: Scene,
        palette: Palette,
        description: String
    ) = WallpaperItem(id, title, category, WallpaperSource.Generative(scene, palette), description)

    private fun photo(
        id: String,
        title: String,
        category: WallpaperCategory,
        resId: Int,
        description: String,
        motion: PhotoMotion = LIQUID,
        focusX: Float = 0.5f,
        focusY: Float = 0.5f
    ) = WallpaperItem(id, title, category, WallpaperSource.Photo(resId, motion, focusX, focusY), description)

    private val LIQUID = PhotoMotion(MotionStyle.LIQUID)

    private fun nature(mapResId: Int, windX: Float = 0.004f, waterX: Float = 0f, waterY: Float = 0.012f) =
        PhotoMotion(MotionStyle.NATURE, mapResId, windX, waterX, waterY)

    val wallpapers: List<WallpaperItem> = listOf(
        // Abstract
        gen(
            "duo_ribbons", "Duo Ribbons", WallpaperCategory.ABSTRACT, Scene.RIBBONS,
            Palette(0x0B1230, 0xFF6A3D, 0xE83F5B, 0x8B5CF6, 0x3B82F6),
            "Silk ribbons of coral, crimson, violet and blue drifting across the crease."
        ),
        gen(
            "ember_silk", "Ember Silk", WallpaperCategory.ABSTRACT, Scene.RIBBONS,
            Palette(0x140A08, 0xFFB347, 0xFF5E3A, 0xC2185B, 0xFFD9A0),
            "Warm amber and rose ribbons on a near-black ground."
        ),
        gen(
            "glacier_silk", "Glacier Silk", WallpaperCategory.ABSTRACT, Scene.RIBBONS,
            Palette(0x06121C, 0x7DD3FC, 0x38BDF8, 0xA5B4FC, 0xE0F2FE),
            "Cold blue ribbons that move like light under ice."
        ),
        gen(
            "iris_flow", "Iris Flow", WallpaperCategory.ABSTRACT, Scene.IRIS,
            Palette(0x120B2E, 0x6D28D9, 0xEC4899, 0xF59E0B, 0x22D3EE),
            "Slowly folding bands of iridescent color."
        ),
        gen(
            "mercury", "Mercury", WallpaperCategory.ABSTRACT, Scene.IRIS,
            Palette(0x0A0C10, 0x3A4150, 0x9AA4B5, 0xE6EAF0, 0x5B6575),
            "Liquid metal in graphite and silver."
        ),
        gen(
            "city_bokeh", "City Bokeh", WallpaperCategory.ABSTRACT, Scene.BOKEH,
            Palette(0x07080C, 0xFFB347, 0xFF6B6B, 0x7DD3FC, 0xFDE68A),
            "Out-of-focus night lights floating in the dark."
        ),
        photo(
            "oil_bubbles", "Oil Bubbles", WallpaperCategory.ABSTRACT, R.drawable.wp_oil_bubbles,
            "Macro photograph of colored oil drops on water."
        ),
        photo(
            "liquid_glass", "Liquid Glass", WallpaperCategory.ABSTRACT, R.drawable.wp_liquid_glass,
            "Violet and blue waves of glossy color."
        ),
        photo(
            "ink_bloom", "Ink Bloom", WallpaperCategory.ABSTRACT, R.drawable.wp_ink_bloom,
            "Red and blue ink billowing through water."
        ),

        // Gradients
        gen(
            "sorbet", "Sorbet", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0xFFB4A2, 0xFF6F91, 0xFFC75F, 0xC34A86, 0xF9F871),
            "Peach, rose and lemon blending in slow motion."
        ),
        gen(
            "lagoon", "Lagoon", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0x03243B, 0x00B4D8, 0x0077B6, 0x90E0EF, 0x48CAE4),
            "Deep water blues with a bright shallow edge."
        ),
        gen(
            "dusk", "Dusk", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0x1B1035, 0xF97316, 0x7C3AED, 0xDB2777, 0x0EA5E9),
            "Violet night pushing into an orange horizon."
        ),
        gen(
            "moss", "Moss", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0x0F1F17, 0x3F6B4E, 0xA3B18A, 0x588157, 0xDAD7CD),
            "Muted greens and sage."
        ),
        photo(
            "pastel_haze", "Pastel Haze", WallpaperCategory.GRADIENTS, R.drawable.wp_pastel_haze,
            "Soft pink, teal and violet light."
        ),

        // Landscapes
        gen(
            "aurora_veil", "Aurora Veil", WallpaperCategory.LANDSCAPES, Scene.AURORA,
            Palette(0x030712, 0x0F2A3A, 0x34D399, 0xA855F7, 0x05080C),
            "Green and violet curtains over a mountain ridge."
        ),
        gen(
            "sahara", "Sahara", WallpaperCategory.LANDSCAPES, Scene.DUNES,
            Palette(0x2E3A6E, 0xF7A35C, 0xFFE8B0, 0xE59A5B, 0x7A3B2E),
            "Layered dunes under a low evening sun."
        ),
        gen(
            "moonlit_dunes", "Moonlit Dunes", WallpaperCategory.LANDSCAPES, Scene.DUNES,
            Palette(0x050816, 0x1E2A4A, 0xE8EEF8, 0x5B6B8C, 0x141B2E),
            "Cold dunes under a full moon."
        ),
        photo(
            "misty_pines", "Misty Pines", WallpaperCategory.LANDSCAPES, R.drawable.wp_misty_pines,
            "Fog drifting over a swaying pine forest.",
            nature(R.drawable.motion_misty_pines, windX = 0.006f), focusY = 0.6f
        ),
        photo(
            "fog_castle", "Fog Castle", WallpaperCategory.LANDSCAPES, R.drawable.wp_fog_castle,
            "Mist rolling past a stone castle above a rippling pool.",
            nature(R.drawable.motion_fog_castle, windX = 0.005f, waterX = 0.004f, waterY = 0f), focusY = 0.35f
        ),
        photo(
            "valley_river", "Valley River", WallpaperCategory.LANDSCAPES, R.drawable.wp_valley_river,
            "A river running through a granite valley at dawn.",
            nature(R.drawable.motion_valley_river, windX = 0.003f, waterY = 0.014f)
        ),
        photo(
            "golden_shore", "Golden Shore", WallpaperCategory.LANDSCAPES, R.drawable.wp_golden_shore,
            "Waves rolling onto a beach under drifting clouds.",
            nature(R.drawable.motion_golden_shore, windX = 0.004f, waterY = 0.02f)
        ),
        photo(
            "desert_road", "Desert Road", WallpaperCategory.LANDSCAPES, R.drawable.wp_desert_road,
            "Clouds crossing red rock country, grass bending in the wind.",
            nature(R.drawable.motion_desert_road, windX = 0.005f), focusX = 0.3f
        ),

        // Space
        gen(
            "carina", "Carina", WallpaperCategory.SPACE, Scene.NEBULA,
            Palette(0x02030A, 0x7C3AED, 0x0EA5E9, 0xF472B6, 0xE0E7FF),
            "Violet and cyan gas clouds in a deep star field."
        ),
        gen(
            "ember_nebula", "Ember Nebula", WallpaperCategory.SPACE, Scene.NEBULA,
            Palette(0x050203, 0xEA580C, 0xBE123C, 0xFDE68A, 0xFFEDD5),
            "Glowing orange and crimson nebula."
        ),
        gen(
            "polar_night", "Polar Night", WallpaperCategory.SPACE, Scene.AURORA,
            Palette(0x01040A, 0x0B1A2E, 0x22D3EE, 0x34D399, 0x020409),
            "Cyan aurora over a black horizon."
        ),
        photo(
            "milky_way", "Milky Way", WallpaperCategory.SPACE, R.drawable.wp_milky_way,
            "The Milky Way turning slowly over snowy peaks.",
            nature(R.drawable.motion_milky_way, windX = 0.0015f)
        ),

        // Textures
        gen(
            "contour_gold", "Contour Gold", WallpaperCategory.TEXTURES, Scene.CONTOURS,
            Palette(0x0B0B0D, 0x15161A, 0xC9A227, 0xF5D76E, 0x000000),
            "Gold elevation lines drifting over black stone."
        ),
        photo(
            "wave_lines", "Wave Lines", WallpaperCategory.TEXTURES, R.drawable.wp_wave_lines,
            "Layered blue line waves."
        ),
        photo(
            "circuit_glow", "Circuit Glow", WallpaperCategory.TEXTURES, R.drawable.wp_circuit_glow,
            "Pulses of light running through circuit traces.",
            PhotoMotion(MotionStyle.PULSE)
        ),

        // Minimal
        gen(
            "still_sea", "Still Sea", WallpaperCategory.MINIMAL, Scene.HORIZON,
            Palette(0x1C2541, 0xF4A261, 0xFFF1D6, 0x14213D, 0xFFD6A5),
            "A low sun over a calm sea."
        ),
        gen(
            "graphite", "Graphite", WallpaperCategory.MINIMAL, Scene.MESH,
            Palette(0x0E0F12, 0x2A2D34, 0x1A1C21, 0x3B3F48, 0x15171B),
            "Barely moving shades of charcoal."
        ),
        gen(
            "contour_slate", "Contour Slate", WallpaperCategory.MINIMAL, Scene.CONTOURS,
            Palette(0x1E293B, 0x0F172A, 0x94A3B8, 0xE2E8F0, 0x000000),
            "Fine silver contours on slate."
        )
    )

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun resolveWallpaper(id: String?): WallpaperItem {
        if (id.isNullOrBlank()) return wallpapers.first()
        return wallpapers.firstOrNull { it.id == id } ?: wallpapers.first()
    }

    fun getWallpaperById(id: String): WallpaperItem? = wallpapers.firstOrNull { it.id == id }

    fun getWallpapersByCategory(category: WallpaperCategory?): List<WallpaperItem> =
        if (category == null) wallpapers else wallpapers.filter { it.category == category }

    fun getActiveWallpaper(context: Context): WallpaperItem =
        resolveWallpaper(getPrefs(context).getString(KEY_ACTIVE_WALLPAPER_ID, DEFAULT_WALLPAPER_ID))

    fun setActiveWallpaper(context: Context, wallpaperId: String) {
        getPrefs(context).edit().putString(KEY_ACTIVE_WALLPAPER_ID, wallpaperId).apply()
    }

    fun getOpeningEffect(context: Context): OpeningEffect =
        OpeningEffect.fromId(getPrefs(context).getString(KEY_OPENING_EFFECT, null))

    fun setOpeningEffect(context: Context, effect: OpeningEffect) {
        getPrefs(context).edit().putString(KEY_OPENING_EFFECT, effect.id).apply()
    }

    fun getIntensity(context: Context): Float =
        getPrefs(context).getFloat(KEY_EFFECT_INTENSITY, DEFAULT_INTENSITY)
            .coerceIn(MIN_INTENSITY, MAX_INTENSITY)

    fun setIntensity(context: Context, value: Float) {
        getPrefs(context).edit()
            .putFloat(KEY_EFFECT_INTENSITY, value.coerceIn(MIN_INTENSITY, MAX_INTENSITY)).apply()
    }

    fun isDynamicMotionEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_DYNAMIC_MOTION_ENABLED, true)

    fun setDynamicMotionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DYNAMIC_MOTION_ENABLED, enabled).apply()
    }

    fun isCoverAwakeEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_COVER_AWAKE, true)

    fun setCoverAwakeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_COVER_AWAKE, enabled).apply()
    }

    /** Hinge angle at which the system last switched cover to inner, or NaN if never seen. */
    fun getMeasuredOpenSwitch(context: Context): Float =
        getPrefs(context).getFloat(KEY_MEASURED_OPEN_SWITCH, Float.NaN)

    /** Hinge angle at which the system last switched inner to cover, or NaN if never seen. */
    fun getMeasuredCloseSwitch(context: Context): Float =
        getPrefs(context).getFloat(KEY_MEASURED_CLOSE_SWITCH, Float.NaN)
}
