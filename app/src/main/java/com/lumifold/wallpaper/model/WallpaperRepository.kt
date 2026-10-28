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
    const val KEY_FOLD_EFFECT_ENABLED = "pref_fold_effect_enabled"
    const val KEY_SYMMETRIC_INNER = "pref_symmetric_inner"
    const val KEY_PAUSE_ON_SAVER = "pref_pause_motion_on_saver"
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
        motion: MotionStyle = MotionStyle.LIQUID,
        focusX: Float = 0.5f,
        focusY: Float = 0.5f,
        depthResId: Int = 0,
        fit: Boolean = false,
        place: ScreenPlace? = null
    ) = WallpaperItem(id, title, category, WallpaperSource.Photo(resId, motion, focusX, focusY, depthResId = depthResId, fit = fit, place = place), description)

    private fun video(
        id: String,
        title: String,
        category: WallpaperCategory,
        url: String,
        posterResId: Int,
        sizeMb: Float,
        description: String,
        focusX: Float = 0.5f,
        focusY: Float = 0.5f,
        framing: VideoFraming? = null
    ) = WallpaperItem(
        id, title, category,
        WallpaperSource.Video(url, posterResId, sizeMb, focusX, focusY, framing),
        description
    )

    // Full-disk SDO frames: the disk spans about 84% of the frame. Open, the Sun is centered;
    // folded, its left limb sits on the right of the cover with eruptions and space to the left.
    private val SUN_FRAMING = VideoFraming(innerHeight = 1.55f, coverHeight = 2.4f, coverCenterX = 1.45f)

    /** Full-resolution Sun clips, re-encoded from the NASA originals. See docs/reference/photo-credits.md. */
    private const val SUN_MEDIA = "https://github.com/bendourthe/lumifold/releases/download/media-v1"

    private fun nebula(id: String, title: String, gasResId: Int, starsResId: Int, thumbResId: Int, depthResId: Int, description: String) =
        WallpaperItem(
            id, title, WallpaperCategory.SPACE,
            WallpaperSource.Photo(gasResId, MotionStyle.NEBULA, thumbResId = thumbResId, starsResId = starsResId, depthResId = depthResId),
            description
        )

    private fun globe(id: String, title: String, mapResId: Int, thumbResId: Int, globe: Globe, description: String) =
        WallpaperItem(
            id, title, WallpaperCategory.SPACE,
            WallpaperSource.Photo(mapResId, MotionStyle.GLOBE, globe = globe, thumbResId = thumbResId),
            description
        )

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
            "jade_silk", "Jade Silk", WallpaperCategory.ABSTRACT, Scene.RIBBONS,
            Palette(0x04130F, 0x34D399, 0x10B981, 0x2DD4BF, 0xD1FAE5),
            "Emerald and teal ribbons folding over deep green."
        ),
        gen(
            "iris_flow", "Iris Flow", WallpaperCategory.ABSTRACT, Scene.IRIS,
            Palette(0x120B2E, 0x6D28D9, 0xEC4899, 0xF59E0B, 0x22D3EE),
            "Slowly folding bands of iridescent color."
        ),
        gen(
            "opal", "Opal", WallpaperCategory.ABSTRACT, Scene.IRIS,
            Palette(0x1A1530, 0x93C5FD, 0xF9A8D4, 0xFDE68A, 0xA7F3D0),
            "Pastel opal light shifting through blue, pink and gold."
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
        gen(
            "rain_bokeh", "Rain Bokeh", WallpaperCategory.ABSTRACT, Scene.BOKEH,
            Palette(0x070B14, 0x93C5FD, 0x38BDF8, 0xC4B5FD, 0xE0F2FE),
            "Soft blue lights drifting like rain on glass."
        ),
        gen(
            "dusk_iris", "Dusk Iris", WallpaperCategory.ABSTRACT, Scene.IRIS,
            Palette(0x1A1028, 0xF472B6, 0xFB7185, 0xFBBF24, 0x818CF8),
            "Slow bands of dusk color folding over each other."
        ),
        photo(
            "ink_in_water", "Ink in Water", WallpaperCategory.ABSTRACT, R.drawable.wp_ink_in_water,
            "Deep blue ink unfurling through clear water."
        ),
        photo(
            "ember_smoke", "Ember Smoke", WallpaperCategory.ABSTRACT, R.drawable.wp_ember_smoke,
            "A ribbon of orange and gold smoke on black."
        ),
        photo(
            "spectrum_smoke", "Spectrum Smoke", WallpaperCategory.ABSTRACT, R.drawable.wp_spectrum_smoke,
            "Smoke curling through every color of the spectrum."
        ),
        photo(
            "violet_smoke", "Violet Smoke", WallpaperCategory.ABSTRACT, R.drawable.wp_violet_smoke,
            "Blue and violet smoke drifting in the dark."
        ),
        photo(
            "holiday_bokeh", "Holiday Bokeh", WallpaperCategory.ABSTRACT, R.drawable.wp_holiday_bokeh,
            "Soft discs of red, green and gold holiday lights."
        ),
        photo(
            "night_bokeh", "Night Bokeh", WallpaperCategory.ABSTRACT, R.drawable.wp_night_bokeh,
            "Blurred city lights glowing at the bottom of the frame."
        ),
        photo(
            "light_trails", "Light Trails", WallpaperCategory.ABSTRACT, R.drawable.wp_light_trails,
            "Long-exposure traffic light streaks at dusk."
        ),
        photo(
            "frozen_bubble", "Frozen Bubble", WallpaperCategory.ABSTRACT, R.drawable.wp_frozen_bubble,
            "A soap bubble freezing into ice crystals.", MotionStyle.NONE
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
        gen(
            "peach_fuzz", "Peach Fuzz", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0xFFE5D9, 0xFFBE98, 0xF4978E, 0xFFD7BA, 0xF8AD9D),
            "Warm peach and apricot, soft as felt."
        ),
        gen(
            "northern", "Northern", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0x061A2B, 0x10B981, 0x0EA5E9, 0x6366F1, 0x064E3B),
            "Aurora greens sliding into night blue."
        ),
        gen(
            "citrus", "Citrus", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0xFFF3B0, 0xFFB703, 0xFB8500, 0xFFD166, 0xA7C957),
            "Lemon, tangerine and lime."
        ),
        gen(
            "orchid", "Orchid", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0x2A0A3D, 0xC026D3, 0xF0ABFC, 0x7E22CE, 0xFB7185),
            "Magenta and lilac petals of color."
        ),
        gen(
            "midnight", "Midnight", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0x020617, 0x1E3A8A, 0x312E81, 0x0F172A, 0x3B82F6),
            "Deep navy with a faint electric glow."
        ),
        gen(
            "coral_reef", "Coral Reef", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0x00303D, 0xFF7F50, 0x2EC4B6, 0xFFBF69, 0xCBF3F0),
            "Coral and turquoise from a shallow reef."
        ),
        gen(
            "mint", "Mint", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0xE8FFF4, 0xA7F3D0, 0x6EE7B7, 0xD1FAE5, 0x99F6E4),
            "Cool pale mint, almost white."
        ),
        gen(
            "sunset_strip", "Sunset Strip", WallpaperCategory.GRADIENTS, Scene.MESH,
            Palette(0x2D0B3A, 0xFF4D6D, 0xFF9E00, 0x9D4EDD, 0xFFD60A),
            "Neon pink, orange and purple of a city sunset."
        ),
        // Landscapes. Live 4K clips first, then photographs. Each display holds the landmark.
        video(
            "halfdome_sunset", "Half Dome Sunset", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/4/4c/Sunset_on_Halfdome_timelapse_Yosemite_CA_2023-07-15_20-11-06_1.webm",
            R.drawable.wpv_halfdome_sunset, 4.9f,
            "Alpenglow moving across Half Dome. 4K time-lapse, Yosemite.",
            focusX = 0.46f, focusY = 0.42f
        ),
        video(
            "prismatic_spring", "Grand Prismatic", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/0/08/Grand_Prismatic_Spring_%28Midway_Geyser_Basin%2C_Yellowstone_National_Park%29_2021-08-10%2C_video2.webm",
            R.drawable.wpv_prismatic_spring, 27.3f,
            "Steam drifting off the Grand Prismatic Spring, Yellowstone. 4K.",
            focusX = 0.48f, focusY = 0.50f
        ),
        video(
            "canary_spring", "Canary Spring", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/5/52/Canary_Spring_%28Mammoth_Hot_Springs%2C_Yellowstone_National_Park%29_2021-08-09.webm",
            R.drawable.wpv_canary_spring, 48.9f,
            "The travertine terraces of Canary Spring at Mammoth Hot Springs, Yellowstone. 4K.",
            focusX = 0.55f, focusY = 0.55f
        ),
        video(
            "aurora_alaska", "Alaskan Aurora", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/c/c5/Alaskan_Aurora_Time_Lapse_%28SVS15077%29.webm",
            R.drawable.wpv_aurora_alaska, 12.6f,
            "Green aurora curtains rippling over the Alaskan night. NASA.", focusX = 0.35f
        ),
        video(
            "excelsior_geyser", "Excelsior Geyser", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/4/4e/Excelsior_Geyser_%28Midway_Geyser_Basin%2C_Yellowstone_National_Park%29_2021-08-10%2C_02.webm",
            R.drawable.wpv_excelsior_geyser, 49.4f,
            "Steam rolling off the turquoise Excelsior Geyser crater, Yellowstone."
        ),
        video(
            "shenandoah_sunset", "Shenandoah Sunset", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/a/a3/Sunset_at_Naked_Creek_Overlook_Shenandoah_National_Park_VA_2024-10-11.60fps.uhd4k.webm",
            R.drawable.wpv_shenandoah_sunset, 85.1f,
            "The sun setting over the Blue Ridge from Naked Creek Overlook, Shenandoah."
        ),
        video(
            "rhine_falls", "Rhine Falls", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/0/08/Rheinfall_%28Rhine_Falls%29.webm",
            R.drawable.wpv_rhine_falls, 85.5f,
            "The Rhine pouring over Europe's largest waterfall, Switzerland."
        ),
        video(
            "grand_canyon_clouds", "Grand Canyon Clouds", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/e/ed/B-roll_Video_-_Time-lapse_of_Clouds_over_the_Canyon_-_November_20%2C_2025_%2855087231001%29.webm",
            R.drawable.wpv_grand_canyon_clouds, 14.1f,
            "Clouds drifting over the Grand Canyon. National Park Service."
        ),
        video(
            "longs_peak_cloud", "Longs Peak Cloud", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/a/aa/Lenticular_cloud_over_Longs_Peak%2C_Colorado_%28time_lapse%29.ogv",
            R.drawable.wpv_longs_peak_cloud, 27.5f,
            "A lenticular cloud hovering over Longs Peak, Rocky Mountain National Park.", focusX = 0.32f
        ),
        video(
            "dettifoss", "Dettifoss", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/1/1e/Dettifoss_waterfall_in_Northern_Iceland.webm",
            R.drawable.wpv_dettifoss, 6.7f,
            "Dettifoss thundering through its canyon in northern Iceland."
        ),
        video(
            "godafoss", "Godafoss", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/f/f8/Go%C3%B0afoss_waterfall_in_Northern_Iceland.webm",
            R.drawable.wpv_godafoss, 3.0f,
            "The horseshoe curtain of Godafoss in northern Iceland."
        ),
        video(
            "niagara_rainbow", "Niagara Falls", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/0/07/Rainbow_over_Niagara_Falls_in_Canada.webm",
            R.drawable.wpv_niagara_rainbow, 24.2f,
            "A rainbow over the Horseshoe Falls at Niagara."
        ),
        video(
            "iguazu_falls", "Iguazu Falls", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/7/76/Cascadas_en_Garganta_del_Diablo_en_Iguaz%C3%BA_con_gran_caudal_de_agua.webm",
            R.drawable.wpv_iguazu_falls, 13.6f,
            "The Devil's Throat at Iguazu Falls in full flood."
        ),
        video(
            "victoria_falls", "Victoria Falls", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/0/0a/Victoria_Falls_2019_8.webm",
            R.drawable.wpv_victoria_falls, 4.2f,
            "Victoria Falls pouring into its gorge."
        ),
        video(
            "seljalandsfoss", "Seljalandsfoss", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/7/7e/203_Seljalandsfoss_long_exposure_timelapse_Video_by_Giles_Laurent.webm",
            R.drawable.wpv_seljalandsfoss, 15.1f,
            "Seljalandsfoss in a soft long-exposure timelapse, Iceland."
        ),
        video(
            "gullfoss", "Gullfoss", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/8/85/Gullfoss_waterfall_in_Iceland.webm",
            R.drawable.wpv_gullfoss, 15.7f,
            "The two-step drop of Gullfoss, Iceland."
        ),
        photo(
            "yosemite_falls", "Yosemite Falls", WallpaperCategory.LANDSCAPES, R.drawable.wp_yosemite_falls,
            "Yosemite Falls pouring down the valley wall, Yosemite National Park.",
            MotionStyle.DEPTH, focusX = 0.58f, focusY = 0.42f, depthResId = R.drawable.wpd_yosemite_falls
        ),
        photo(
            "grand_canyon", "Grand Canyon", WallpaperCategory.LANDSCAPES, R.drawable.wp_grand_canyon,
            "The South Rim of the Grand Canyon at sunset, the lit cliffs held in frame.",
            MotionStyle.DEPTH, focusX = 0.70f, focusY = 0.48f, depthResId = R.drawable.wpd_grand_canyon
        ),
        photo(
            "grand_teton", "Grand Teton", WallpaperCategory.LANDSCAPES, R.drawable.wp_grand_teton,
            "The Teton peaks across a lake in autumn, Grand Teton National Park.",
            MotionStyle.DEPTH, focusX = 0.78f, focusY = 0.48f, depthResId = R.drawable.wpd_grand_teton
        ),
        photo(
            "bryce_canyon", "Bryce Canyon", WallpaperCategory.LANDSCAPES, R.drawable.wp_bryce,
            "Hoodoos of Bryce Canyon in snow, the towers held in the center of both screens.",
            MotionStyle.DEPTH, focusX = 0.45f, focusY = 0.40f, depthResId = R.drawable.wpd_bryce
        ),
        photo(
            "half_dome", "Half Dome", WallpaperCategory.LANDSCAPES, R.drawable.wp_half_dome,
            "Half Dome in late light, seen from Glacier Point, Yosemite.",
            MotionStyle.DEPTH, focusX = 0.55f, focusY = 0.45f, depthResId = R.drawable.wpd_half_dome
        ),
        photo(
            "tunnel_view", "Tunnel View", WallpaperCategory.LANDSCAPES, R.drawable.wp_tunnel_view,
            "El Capitan, Half Dome and Bridalveil Fall from Tunnel View, Yosemite.",
            MotionStyle.DEPTH, focusX = 0.48f, focusY = 0.42f, depthResId = R.drawable.wpd_tunnel_view
        ),
        photo(
            "hopi_point", "Hopi Point", WallpaperCategory.LANDSCAPES, R.drawable.wp_hopi_point,
            "The Grand Canyon from Hopi Point, with a rainbow over the inner gorge.",
            MotionStyle.DEPTH, focusX = 0.62f, focusY = 0.42f, depthResId = R.drawable.wpd_hopi_point
        ),
        // City: skylines only, seen from the water or across open ground. Landmarks are their own category.
        video(
            "colosseum", "Colosseum", WallpaperCategory.LANDMARKS,
            "https://upload.wikimedia.org/wikipedia/commons/b/b0/Colosseum%2C_Rome_113323.webm",
            R.drawable.wpv_colosseum, 11.5f,
            "Inside the Colosseum in Rome, the arches filling the frame. 4K.",
            focusX = 0.50f, focusY = 0.48f
        ),
        video(
            "chicago_day_night", "Chicago Day and Night", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/9/99/Chicago_Day_and_Night.webm",
            R.drawable.wpv_chicago_day_night, 53.6f,
            "The Chicago skyline from the lakefront, passing from day into night. 4K.",
            focusX = 0.42f, focusY = 0.38f
        ),
        video(
            "eiffel_sunset", "Eiffel Sunset", WallpaperCategory.LANDMARKS,
            "https://upload.wikimedia.org/wikipedia/commons/a/a9/Sunset_on_Eiffel_Tower_in_Paris_-_Vimeo.webm",
            R.drawable.wpv_eiffel_sunset, 5.6f,
            "The sun setting behind the Eiffel Tower, Paris.", focusX = 0.72f
        ),
        video(
            "la_night", "Los Angeles Night", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/9/92/DTLA_by_night%2C_Los_Angeles_July_2022.webm",
            R.drawable.wpv_la_night, 30.5f,
            "Downtown Los Angeles at night, the towers filling the frame.", focusX = 0.50f, focusY = 0.55f
        ),
        video(
            "london_waterloo", "London from Waterloo Bridge", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/f/f5/Timelapse_from_Waterloo_Bridge%2C_London.webm",
            R.drawable.wpv_london_waterloo, 55.4f,
            "The Thames, St Paul's and the City from Waterloo Bridge."
        ),
        video(
            "taj_mahal_live", "Taj Mahal", WallpaperCategory.LANDMARKS,
            "https://upload.wikimedia.org/wikipedia/commons/d/dc/Taj_Mahal.webm",
            R.drawable.wpv_taj_mahal_live, 4.1f,
            "The Taj Mahal and its reflecting pool, Agra."
        ),
        video(
            "shanghai_lujiazui", "Shanghai Lujiazui", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/9/91/Shanghai_Bund%2C_Lujiazui_Skyline_Timelapse.webm",
            R.drawable.wpv_shanghai_lujiazui, 18.9f,
            "The Lujiazui towers across the Huangpu from the Bund."
        ),
        video(
            "hong_kong_sunset", "Hong Kong Skyline", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/d/d0/%E4%BB%8A%E6%97%A5%E4%BF%822020%E5%B9%B4%E5%98%85%E6%9C%80%E5%BE%8C%E4%B8%80%E6%97%A5%EF%BC%8C%E6%88%91%E7%89%B9%E5%88%A5%E5%8E%BB%E5%92%97%E6%B7%B1%E6%B0%B4%E5%9F%97%E5%98%89%E9%A0%93%E5%B1%B1%28%E5%96%83%E5%98%B8%E5%B1%B1%29%E7%9D%87%E4%BB%8A%E5%B9%B4%E6%9C%80%E5%BE%8C%E4%B8%80%E6%AC%A1%E6%97%A5%E8%90%BD.webm",
            R.drawable.wpv_hong_kong_sunset, 25.4f,
            "The Kowloon skyline at sunset, seen across the harbour."
        ),
        video(
            "machu_picchu_live", "Machu Picchu", WallpaperCategory.LANDMARKS,
            "https://upload.wikimedia.org/wikipedia/commons/b/ba/Machu-Pichu_%28video_2011%29.webm",
            R.drawable.wpv_machu_picchu_live, 7.2f,
            "Clouds rolling over the peaks at Machu Picchu, Peru."
        ),
        video(
            "petra_monastery", "Petra Monastery", WallpaperCategory.LANDMARKS,
            "https://upload.wikimedia.org/wikipedia/commons/1/18/The_Monastery_-_Petra%2C_Jordan_%28video_of_September_2009%29.webm",
            R.drawable.wpv_petra_monastery, 71.1f,
            "The rock-cut Monastery of Petra, Jordan."
        ),
        video(
            "toronto_skyline_live", "Toronto Skyline", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/6/62/Toronto_Skyline2.webm",
            R.drawable.wpv_toronto_skyline_live, 11.3f,
            "The CN Tower and Toronto skyline at dusk."
        ),
        video(
            "statue_of_liberty", "Statue of Liberty", WallpaperCategory.LANDMARKS,
            "https://upload.wikimedia.org/wikipedia/commons/5/50/View_of_Statue_of_Liberty_on_a_Boat.webm",
            R.drawable.wpv_statue_of_liberty, 10.6f,
            "Passing the Statue of Liberty in New York Harbor."
        ),
        photo(
            "golden_gate", "Golden Gate", WallpaperCategory.LANDMARKS, R.drawable.wp_golden_gate,
            "The Golden Gate Bridge from the bay. Folded or open, the near tower stays in frame.",
            MotionStyle.DEPTH, focusX = 0.28f, focusY = 0.42f, depthResId = R.drawable.wpd_golden_gate
        ),
        photo(
            "toronto_night", "Toronto Night", WallpaperCategory.CITY, R.drawable.wp_toronto_night,
            "The Toronto skyline and CN Tower from the water at night.",
            MotionStyle.DEPTH, focusX = 0.50f, focusY = 0.55f, depthResId = R.drawable.wpd_toronto_night
        ),
        photo(
            "san_francisco", "San Francisco Night", WallpaperCategory.CITY, R.drawable.wp_san_francisco,
            "San Francisco at night from across the water: the Bay Bridge and the downtown skyline.",
            MotionStyle.DEPTH, focusX = 0.74f, focusY = 0.55f, depthResId = R.drawable.wpd_san_francisco
        ),
        photo(
            "london_skyline", "London at Dusk", WallpaperCategory.CITY, R.drawable.wp_london_skyline,
            "The City of London from Waterloo Bridge at dusk: St Paul's, the Gherkin and the Shard.",
            MotionStyle.DEPTH, focusX = 0.52f, focusY = 0.40f, depthResId = R.drawable.wpd_london_skyline
        ),
        // Scenes
        video(
            "deep_sea_jellyfish", "Deep-Sea Jellyfish", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/c/c0/Jellyfish_-_Deepwater_Exploration_of_the_Marianas_expedition_-_April_24%2C_2016.webm/Jellyfish_-_Deepwater_Exploration_of_the_Marianas_expedition_-_April_24%2C_2016.webm.1080p.vp9.webm",
            R.drawable.wpv_deep_sea_jellyfish, 8.5f,
            "A glowing jellyfish drifting in the deep sea. NOAA footage."
        ),
        video(
            "blue_jellyfish", "Blue Jellyfish", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/8/8e/Aquarium_Boka_-_species_02.webm/Aquarium_Boka_-_species_02.webm.1080p.vp9.webm",
            R.drawable.wpv_blue_jellyfish, 8.1f,
            "Jellyfish pulsing through blue water."
        ),
        video(
            "spotted_jellyfish", "Spotted Jellyfish", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/9/9b/M%C3%A9duses_constell%C3%A9es_%C3%A0_l%27aquarium_de_Monaco_en_novembre_2021_%28vid%C3%A9o%29.webm/M%C3%A9duses_constell%C3%A9es_%C3%A0_l%27aquarium_de_Monaco_en_novembre_2021_%28vid%C3%A9o%29.webm.1080p.vp9.webm",
            R.drawable.wpv_spotted_jellyfish, 4.1f,
            "Spotted jellyfish floating in the dark."
        ),
        video(
            "sea_nettle", "Sea Nettle", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/9/94/Chrysaora_fuscescens_%28Pacific_sea_nettle%29_swimming.webm",
            R.drawable.wpv_sea_nettle, 10.3f,
            "A Pacific sea nettle trailing its long tentacles."
        ),
        video(
            "reef", "Reef", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/4/46/Sealife-Munich-3.ogv/Sealife-Munich-3.ogv.1080p.vp9.webm",
            R.drawable.wpv_reef, 10.4f,
            "Tropical fish around a colorful reef."
        ),
        video(
            "shark", "Shark", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/1/1f/Sealife-Munich-Shark.ogv/Sealife-Munich-Shark.ogv.1080p.vp9.webm",
            R.drawable.wpv_shark, 10.0f,
            "A shark gliding through blue water."
        ),
        video(
            "candle", "Candle", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/7/72/Corona.Candle.webm/Corona.Candle.webm.1080p.vp9.webm",
            R.drawable.wpv_candle, 1.5f,
            "A single candle flickering in the dark."
        ),
        video(
            "fireplace", "Fireplace", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/b/bb/Fantastic-fireplace-fire-chimney-hearth-_background_-_texture_-_motion_graphics_-_free_video_library.webm",
            R.drawable.wpv_fireplace, 19.1f,
            "Flames curling over logs in a hearth."
        ),
        video(
            "snow_streetlight", "Snow Under Lamplight", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/f/f1/Snow_under_LED_street_light_in_Tuntorp%2C_Brastad.webm",
            R.drawable.wpv_snow_streetlight, 7.2f,
            "Snow falling through the glow of a street light."
        ),
        video(
            "embers", "Embers", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/f/f4/Fire_%D8%A2%D8%AA%D8%B4_04.ogv/Fire_%D8%A2%D8%AA%D8%B4_04.ogv.1080p.vp9.webm",
            R.drawable.wpv_embers, 9.4f,
            "Glowing embers and soft bokeh."
        ),
        video(
            "campfire", "Campfire", WallpaperCategory.SCENES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/7/71/Fire_%D8%A2%D8%AA%D8%B4_10.ogv/Fire_%D8%A2%D8%AA%D8%B4_10.ogv.1080p.vp9.webm",
            R.drawable.wpv_campfire, 4.7f,
            "Campfire flames up close."
        ),
        // Illustrated. Drawn scenes that move first, then paintings in depth.
        gen(
            "dusk_hills", "Dusk Hills", WallpaperCategory.ILLUSTRATED, Scene.HILLS,
            Palette(0x2B1E4A, 0xF4A67A, 0x8A5A88, 0x221833, 0xFFE2A8),
            "Violet ridges drifting past each other under a peach sunset."
        ),
        gen(
            "blue_ridge", "Blue Ridge", WallpaperCategory.ILLUSTRATED, Scene.HILLS,
            Palette(0x6FA8DC, 0xDDEBF5, 0x7F9DB8, 0x1F3448, 0xFFF6DA),
            "Misty blue mountain layers with clouds sliding over them."
        ),
        gen(
            "northern_lights", "Northern Lights", WallpaperCategory.ILLUSTRATED, Scene.AURORA,
            Palette(0x050B1E, 0x3CF2A0, 0x8A5CF6, 0x070A12, 0x0D2440),
            "Green and violet aurora curtains rippling over a mirror lake."
        ),
        gen(
            "aurora_rose", "Aurora Rose", WallpaperCategory.ILLUSTRATED, Scene.AURORA,
            Palette(0x0A0A1F, 0xFF7AB6, 0x56E3C8, 0x0B0B14, 0x241433),
            "Pink and teal aurora swaying above dark peaks."
        ),
        gen(
            "golden_sea", "Golden Sea", WallpaperCategory.ILLUSTRATED, Scene.SEA,
            Palette(0x2E3B6E, 0xF7B267, 0xFFE8B0, 0x5B4E7A, 0x1C2A4F),
            "Slow waves rolling under a low golden sun."
        ),
        gen(
            "coral_bay", "Coral Bay", WallpaperCategory.ILLUSTRATED, Scene.SEA,
            Palette(0x7EC8E3, 0xFFD6C2, 0xFFF4DE, 0x4FA3C4, 0x15607E),
            "A bright bay with a glittering path of light across the water."
        ),
        gen(
            "winter_night", "Winter Night", WallpaperCategory.ILLUSTRATED, Scene.SNOW_FOREST,
            Palette(0x0B1430, 0x2D3E6B, 0x24345C, 0x0E1528, 0xEAF2FF),
            "Snow falling through a moonlit pine forest."
        ),
        gen(
            "snowy_morning", "Snowy Morning", WallpaperCategory.ILLUSTRATED, Scene.SNOW_FOREST,
            Palette(0xB9D3EA, 0xF3F6F9, 0x8DA3B8, 0x3E5468, 0xFFFFFF),
            "Soft snow drifting past pines on a pale winter morning."
        ),
        gen(
            "starry_swirl", "Starry Swirl", WallpaperCategory.ILLUSTRATED, Scene.SWIRL,
            Palette(0x0E1E4F, 0x2D5DA8, 0x8EC5E8, 0xF7D44C, 0x0A1128),
            "A swirling painted night sky with a glowing moon and haloed stars."
        ),
        gen(
            "ember_swirl", "Ember Swirl", WallpaperCategory.ILLUSTRATED, Scene.SWIRL,
            Palette(0x2A0E12, 0x8A2F2A, 0xE89F6B, 0xFFE08A, 0x14080A),
            "The swirling painted sky in ember reds and gold."
        ),
        photo(
            "great_wave", "The Great Wave", WallpaperCategory.ILLUSTRATED, R.drawable.wp_great_wave,
            "Hokusai's great wave towers over three boats while Mount Fuji sits small in the distance, c. 1831.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_great_wave
        ),
        photo(
            "red_fuji", "Red Fuji", WallpaperCategory.ILLUSTRATED, R.drawable.wp_red_fuji,
            "Mount Fuji glows red in the early light beneath rows of mackerel clouds, Hokusai, c. 1831.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_red_fuji
        ),
        photo(
            "sudden_shower", "Sudden Shower", WallpaperCategory.ILLUSTRATED, R.drawable.wp_sudden_shower,
            "Travelers hurry across the Ohashi bridge through a sudden summer downpour, Hiroshige, 1857.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_sudden_shower
        ),
        photo(
            "tennoji_snow", "Snow at Tennoji", WallpaperCategory.ILLUSTRATED, R.drawable.wp_tennoji_snow,
            "A lone figure under an umbrella stands in falling snow before the pagoda of Shitenno-ji, Hasui, 1927.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_tennoji_snow
        ),
        photo(
            "starry_night", "The Starry Night", WallpaperCategory.ILLUSTRATED, R.drawable.wp_starry_night,
            "Van Gogh's swirling night sky rolls over a sleeping village at Saint-Remy, 1889.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_starry_night
        ),
        photo(
            "cafe_terrace", "Café Terrace at Night", WallpaperCategory.ILLUSTRATED, R.drawable.wp_cafe_terrace,
            "A gaslit cafe terrace glows yellow under a starry sky in Arles, Van Gogh, 1888.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_cafe_terrace
        ),
        photo(
            "impression_sunrise", "Impression, Sunrise", WallpaperCategory.ILLUSTRATED, R.drawable.wp_impression_sunrise,
            "An orange sun rises through the morning haze over the port of Le Havre, Monet, 1872.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_impression_sunrise
        ),
        photo(
            "water_lilies", "Water Lilies", WallpaperCategory.ILLUSTRATED, R.drawable.wp_water_lilies,
            "Water lilies drift on a violet pond beneath trailing willow fronds, Monet, 1922.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_water_lilies
        ),
        photo(
            "the_kiss", "The Kiss", WallpaperCategory.ILLUSTRATED, R.drawable.wp_the_kiss,
            "Klimt's lovers are wrapped in a golden embrace at the edge of a flowered meadow, 1907-1908.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_the_kiss
        ),
        photo(
            "wanderer_fog", "Wanderer above the Fog", WallpaperCategory.ILLUSTRATED, R.drawable.wp_wanderer_fog,
            "A lone wanderer stands on a rocky crag above a sea of fog, Friedrich, c. 1818.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_wanderer_fog
        ),
        photo(
            "ninth_wave", "The Ninth Wave", WallpaperCategory.ILLUSTRATED, R.drawable.wp_ninth_wave,
            "Shipwreck survivors cling to a mast as a towering wave glows in the dawn light, Aivazovsky, 1850.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_ninth_wave
        ),
        photo(
            "sierra_nevada", "Among the Sierra Nevada", WallpaperCategory.ILLUSTRATED, R.drawable.wp_sierra_nevada,
            "Golden light breaks through storm clouds over a mountain lake in the Sierra Nevada, Bierstadt, 1868.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_sierra_nevada
        ),
        photo(
            "grand_canyon_poster", "Grand Canyon", WallpaperCategory.ILLUSTRATED, R.drawable.wp_grand_canyon_poster,
            "Layered canyon walls in pink and violet, from a 1938 WPA poster for Grand Canyon National Park.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_grand_canyon_poster
        ),
        photo(
            "zion_poster", "Zion", WallpaperCategory.ILLUSTRATED, R.drawable.wp_zion_poster,
            "Zion's great white cliff rises above a pine forest, from a 1938 WPA National Park poster.", MotionStyle.DEPTH, depthResId = R.drawable.wpd_zion_poster
        ),
        // Space
        video(
            "sun_flare_304", "Solar Flare", WallpaperCategory.SPACE,
            "$SUN_MEDIA/sun_flare_304.webm",
            R.drawable.wpv_sun_flare_304, 36.5f,
            "The Sun in extreme ultraviolet as a strong flare erupts. NASA Solar Dynamics Observatory.", framing = SUN_FRAMING
        ),
        video(
            "sun_corona_171", "Golden Corona", WallpaperCategory.SPACE,
            "$SUN_MEDIA/sun_corona_171.webm",
            R.drawable.wpv_sun_corona_171, 35.8f,
            "Magnetic loops shimmering in the Sun's corona. NASA Solar Dynamics Observatory.", framing = SUN_FRAMING
        ),
        video(
            "sun_teal_131", "Flaring Sun", WallpaperCategory.SPACE,
            "$SUN_MEDIA/sun_teal_131.webm",
            R.drawable.wpv_sun_teal_131, 37.7f,
            "The hottest flaring plasma glowing teal. NASA Solar Dynamics Observatory.", framing = SUN_FRAMING
        ),
        video(
            "ring_prominence", "Ring Prominence", WallpaperCategory.SPACE,
            "$SUN_MEDIA/ring_prominence.webm",
            R.drawable.wpv_ring_prominence, 27.8f,
            "A ring of plasma lifting off the Sun and breaking apart. NASA Solar Dynamics Observatory.", framing = SUN_FRAMING
        ),
        video(
            "solar_eruption", "Solar Eruption", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/d/d3/SDO_Video_Toolkit_%28SVS14126_-_Oct2_Eruption_2%29.webm?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original",
            R.drawable.wpv_solar_eruption, 2.3f,
            "Plasma arcing off the limb of the Sun. NASA Solar Dynamics Observatory."
        ),
        globe(
            "earth_globe", "Earth", R.drawable.wpg_earth, R.drawable.wpt_earth,
            Globe(spinSeconds = 160f, tiltDeg = 23f, atmosphere = 0x5A9BFF, night = true, coverRadius = 0.70f, coverEdgeX = 0.30f),
            "Earth turning slowly, city lights glowing on the night side. NASA Blue Marble and city lights maps."
        ),
        globe(
            "earth_night_globe", "Earth at Night", R.drawable.wpg_earth, R.drawable.wpt_earth_night,
            Globe(spinSeconds = 160f, tiltDeg = 23f, atmosphere = 0x5A9BFF, night = true, lightX = 0.35f, lightY = 0.1f, lightZ = -0.93f, coverRadius = 0.70f, coverEdgeX = 0.30f),
            "The night side of Earth turning slowly, its cities glowing. NASA city lights map."
        ),
        globe(
            "moon_globe", "The Moon", R.drawable.wpg_moon, R.drawable.wpt_moon,
            Globe(spinSeconds = 240f, tiltDeg = 6f, coverRadius = 0.70f, coverEdgeX = 0.30f),
            "The Moon rotating in sunlight. NASA Lunar Reconnaissance Orbiter color map."
        ),
        globe(
            "mars_globe", "Mars", R.drawable.wpg_mars, R.drawable.wpt_mars,
            Globe(spinSeconds = 170f, tiltDeg = 25f, atmosphere = 0xE08A5A, coverRadius = 0.70f, coverEdgeX = 0.30f),
            "Mars turning under a thin dusty atmosphere. NASA Viking color mosaic."
        ),
        globe(
            "jupiter_globe", "Jupiter", R.drawable.wpg_jupiter, R.drawable.wpt_jupiter,
            Globe(spinSeconds = 110f, tiltDeg = 3f, atmosphere = 0xCCB38C, coverRadius = 0.70f, coverEdgeX = 0.30f),
            "Jupiter's cloud bands and Great Red Spot rotating. NASA Cassini map."
        ),
        photo(
            "saturn_cassini", "Saturn", WallpaperCategory.SPACE, R.drawable.wp_saturn_cassini,
            "Saturn and its rings, photographed by Cassini. Folded, the cover shows the left half of the planet. Open, the whole planet is in frame.",
            MotionStyle.NONE, focusX = 0.55f, focusY = 0.52f,
            place = ScreenPlace(coverEdgeU = 0.555f, coverSpanU = 0.42f, innerCenterU = 0.50f, innerSpanU = 0.88f)
        ),
        video(
            "black_hole", "Black Hole", WallpaperCategory.SPACE,
            "https://svs.gsfc.nasa.gov/vis/a010000/a013300/a013326/BH_Accretion_Disk_Sim_360_4k.mp4",
            R.drawable.wpv_black_hole, 119.2f,
            "One full revolution around a black hole, slowed and looping without a cut. NASA."
        ),
        video(
            "spinning_saturn", "Spinning Saturn", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/c/cc/Video_-_Spinning_Saturn_-_Cassini_%2851784196854%29.webm",
            R.drawable.wpv_spinning_saturn, 2.8f,
            "Saturn turning with its rings, from Cassini images."
        ),
        video(
            "orion_flight", "Orion Flight", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/c/c8/Flight_through_Orion_Nebula_in_visible_light_%28Hubblecast_106b_excerpt%29.webm",
            R.drawable.wpv_orion_flight, 1.8f,
            "A slow flight into the Orion Nebula, built from Hubble images."
        ),
        video(
            "crab_nebula_live", "Crab Nebula", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/a/a1/Hubble%E2%80%99s_Inside_The_Image-_Crab_Nebula_%28SVS14287%29.webm",
            R.drawable.wpv_crab_nebula_live, 22.1f,
            "Drifting through the filaments of the Crab Nebula, Hubble."
        ),
        video(
            "jupiter_juno", "Jupiter Flyby", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/7/78/Juno%27s_Perijove-07_Jupiter_Flyby%2C_Reconstructed_in_25-Fold_Time-Lapse.webm",
            R.drawable.wpv_jupiter_juno, 6.9f,
            "Juno sweeping past Jupiter's clouds and the Great Red Spot."
        ),
        video(
            "earth_from_iss", "Earth from the ISS", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/4/42/Crew_Earth_Observations_%284K%29.webm",
            R.drawable.wpv_earth_from_iss, 83.7f,
            "Earth turning below the International Space Station, in 4K."
        ),
        video(
            "spiral_galaxy_live", "Spiral Galaxy", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/0/0d/One-sided_spiral_%28potm2607a%29.webm",
            R.drawable.wpv_spiral_galaxy_live, 22.9f,
            "A slow pan across a lopsided spiral galaxy. ESO."
        ),
        video(
            "m83_galaxy", "Southern Pinwheel", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/f/f1/Explore_spiral_galaxy_M83_through_Webb%E2%80%99s_infrared_eyes_ESA508223.webm",
            R.drawable.wpv_m83_galaxy, 31.4f,
            "Spiral galaxy M83 in Webb's infrared view."
        ),
        photo(
            "spiral_galaxy", "Whirlpool Galaxy", WallpaperCategory.SPACE, R.drawable.wp_whirlpool,
            "The Whirlpool Galaxy, M51, photographed by Hubble: blue arms, dark dust lanes and pink star-forming knots. NASA.",
            MotionStyle.DEPTH, focusX = 0.38f, focusY = 0.50f, depthResId = R.drawable.wpd_whirlpool
        ),
        nebula(
            "pillars_webb", "Pillars of Creation", R.drawable.wp_pillars_webb, R.drawable.wpn_pillars_webb, R.drawable.wpt_pillars_webb, R.drawable.wpd_pillars_webb,
            "The Pillars of Creation in near-infrared, James Webb Space Telescope."
        ),
        nebula(
            "cosmic_cliffs", "Cosmic Cliffs", R.drawable.wp_cosmic_cliffs, R.drawable.wpn_cosmic_cliffs, R.drawable.wpt_cosmic_cliffs, R.drawable.wpd_cosmic_cliffs,
            "The star-forming edge of the Carina Nebula, James Webb Space Telescope."
        ),
        nebula(
            "orion_nebula", "Orion Nebula", R.drawable.wp_orion_nebula, R.drawable.wpn_orion_nebula, R.drawable.wpt_orion_nebula, R.drawable.wpd_orion_nebula,
            "The heart of the Orion Nebula, Hubble Space Telescope."
        ),
        nebula(
            "tarantula", "Tarantula Nebula", R.drawable.wp_tarantula, R.drawable.wpn_tarantula, R.drawable.wpt_tarantula, R.drawable.wpd_tarantula,
            "Glowing gas and young stars in 30 Doradus, Hubble Space Telescope."
        ),
        nebula(
            "helix", "Helix Nebula", R.drawable.wp_helix, R.drawable.wpn_helix, R.drawable.wpt_helix, R.drawable.wpd_helix,
            "The Helix Nebula in infrared, Spitzer Space Telescope."
        ),
        // Wildlife. Live footage first, then photographs.
        video(
            "flamingos", "Flamingos", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/7/7e/046_Greater_flamingos_courtship_display_in_the_Camargue_during_mating_season_%28normal_speed%29_Video_by_Giles_Laurent.webm",
            R.drawable.wpv_flamingos, 14.8f,
            "Greater flamingos in their courtship display, Camargue."
        ),
        video(
            "red_deer", "Red Deer", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/7/78/004_Red_deer_checking_if_a_female_is_ready_to_mate_Video_by_Giles_Laurent.webm",
            R.drawable.wpv_red_deer, 34.8f,
            "A red deer stag in the woods."
        ),
        video(
            "humpback_whale", "Humpback Whale", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/f/fe/032_Humpback_whale_lobtailing_in_slow_motion_Video_by_Giles_Laurent.webm",
            R.drawable.wpv_humpback_whale, 84.1f,
            "A humpback whale slapping its tail in slow motion."
        ),
        video(
            "siberian_tiger", "Siberian Tiger", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/4/47/2024-06-01_LJUBLJANA_ZOO_LJUBLJANA_-_Panthera_tigris_altaica.webm",
            R.drawable.wpv_siberian_tiger, 91.5f,
            "A Siberian tiger moving through the trees."
        ),
        video(
            "adelie_penguin", "Adelie Penguin", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/c/cc/Ad%C3%A9lie_penguin_%28Pygoscelis_adeliae%29_in_Antarctica.webm",
            R.drawable.wpv_adelie_penguin, 3.5f,
            "An Adelie penguin on the Antarctic ice."
        ),
        video(
            "plains_bison", "Plains Bison", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/a/a0/Plains_bison_in_North_Dakota.webm",
            R.drawable.wpv_plains_bison, 6.2f,
            "Bison grazing the North Dakota prairie."
        ),
        video(
            "forest_elephants", "Forest Elephants", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/7/7d/Forest_elephants_gathering_at_Dzanga_Bai.webm",
            R.drawable.wpv_forest_elephants, 12.6f,
            "Forest elephants gathering at Dzanga Bai."
        ),
        video(
            "penguin_colony", "Penguin Colony", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/8/8f/Life_in_the_Colony.ogv",
            R.drawable.wpv_penguin_colony, 16.9f,
            "Life in an emperor penguin colony."
        ),
        video(
            "savanna_elephant", "Savanna Elephant", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/d/df/Savanna_Elephant_%28Loxodonta_africana%29_bull_drinking_from_a_rain_puddle.webm",
            R.drawable.wpv_savanna_elephant, 24.3f,
            "An elephant bull drinking from a rain puddle on the savanna."
        ),
        video(
            "mountain_gorilla", "Mountain Gorilla", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/9/9b/Mountain_gorilla_%28Gorilla_beringei_beringei%29_climbing_a_tree.webm",
            R.drawable.wpv_mountain_gorilla, 50.3f,
            "A mountain gorilla climbing in the forest."
        ),
        video(
            "dolphins", "Dolphins", WallpaperCategory.WILDLIFE,
            "https://upload.wikimedia.org/wikipedia/commons/c/ce/Common_Dolphins_in_the_Bay_of_Gibraltar_06.ogv",
            R.drawable.wpv_dolphins, 9.4f,
            "Common dolphins in the Bay of Gibraltar."
        ),
        // Textures. Drawn live motion first, then photographs.
        gen(
            "contour_copper", "Contour Copper", WallpaperCategory.TEXTURES, Scene.CONTOURS,
            Palette(0x140C08, 0x2A1810, 0xC4784A, 0xE8B48A, 0x000000),
            "Copper elevation lines drifting over dark wood."
        ),
        gen(
            "contour_frost", "Contour Frost", WallpaperCategory.TEXTURES, Scene.CONTOURS,
            Palette(0x0E1418, 0x1A242C, 0xB9D6E0, 0xE8F4F8, 0x000000),
            "Pale frost lines moving slowly across stone."
        ),
        gen(
            "contour_ember", "Contour Ember", WallpaperCategory.TEXTURES, Scene.CONTOURS,
            Palette(0x140806, 0x2A100C, 0xE07A3D, 0xF6C28B, 0x000000),
            "Warm ember lines drifting over charcoal."
        ),
        gen(
            "mesh_silk", "Silk Mesh", WallpaperCategory.TEXTURES, Scene.MESH,
            Palette(0x16121A, 0x3A2A38, 0xC9A0B0, 0xE8D0D4, 0x241820),
            "A silk surface shifting in slow folds."
        ),
        gen(
            "mesh_tide", "Tide Mesh", WallpaperCategory.TEXTURES, Scene.MESH,
            Palette(0x071418, 0x123038, 0x3D8A8A, 0xB7E0D8, 0x0C2024),
            "Tide-colored cloth moving like shallow water."
        ),
        gen(
            "mesh_dusk", "Dusk Mesh", WallpaperCategory.TEXTURES, Scene.MESH,
            Palette(0x1A1020, 0x3A2040, 0xC47898, 0xF0C8B0, 0x241428),
            "Dusk cloth with a slow, even drift."
        ),
        gen(
            "contour_pine", "Contour Pine", WallpaperCategory.TEXTURES, Scene.CONTOURS,
            Palette(0x0C1410, 0x142018, 0x6A8A5A, 0xC8D8B0, 0x000000),
            "Green contour lines moving over dark pine."
        ),
        gen(
            "iris_stone", "Stone Iris", WallpaperCategory.TEXTURES, Scene.IRIS,
            Palette(0x12100E, 0x3A342C, 0xA89880, 0xE4D8C8, 0x6A6054),
            "Stone-colored bands folding very slowly."
        ),
        gen(
            "bokeh_dust", "Dust Light", WallpaperCategory.TEXTURES, Scene.BOKEH,
            Palette(0x100E0C, 0xE8D8C0, 0xC4A878, 0x8A7860, 0xF4E8D4),
            "Soft dust motes floating through warm light."
        ),
        gen(
            "contour_gold", "Contour Gold", WallpaperCategory.TEXTURES, Scene.CONTOURS,
            Palette(0x0B0B0D, 0x15161A, 0xC9A227, 0xF5D76E, 0x000000),
            "Gold elevation lines drifting over black stone."
        ),
        photo(
            "marble", "Marble", WallpaperCategory.TEXTURES, R.drawable.wp_marble,
            "Pale veined marble from Euboea, Greece.", MotionStyle.NONE
        ),
        photo(
            "pine_grain", "Pine Grain", WallpaperCategory.TEXTURES, R.drawable.wp_pine_grain,
            "Close-up of pressed pine grain.", MotionStyle.NONE
        ),
        photo(
            "curly_grain", "Curly Grain", WallpaperCategory.TEXTURES, R.drawable.wp_curly_grain,
            "Figured wood with rippling curly grain.", MotionStyle.NONE
        ),
        photo(
            "leaf_veins", "Leaf Veins", WallpaperCategory.TEXTURES, R.drawable.wp_leaf_veins,
            "The vein network of a green leaf in macro.", MotionStyle.NONE
        ),
        photo(
            "backlit_leaf", "Backlit Leaf", WallpaperCategory.TEXTURES, R.drawable.wp_backlit_leaf,
            "A translucent leaf glowing in sunlight.", MotionStyle.NONE
        ),
        photo(
            "fjord_water", "Fjord Water", WallpaperCategory.TEXTURES, R.drawable.wp_fjord_water,
            "Green water rippling on a Swedish fjord."
        ),
        photo(
            "blue_ice", "Blue Ice", WallpaperCategory.TEXTURES, R.drawable.wp_blue_ice,
            "Translucent blue glacier ice.", MotionStyle.NONE
        ),
        photo(
            "ice_on_moss", "Ice on Moss", WallpaperCategory.TEXTURES, R.drawable.wp_ice_on_moss,
            "Lace of ice over dark moss, Glacier National Park.", MotionStyle.NONE
        ),
        photo(
            "window_frost", "Window Frost", WallpaperCategory.TEXTURES, R.drawable.wp_window_frost,
            "Frost flowers growing across a window pane.", MotionStyle.NONE
        ),
        photo(
            "tree_bark", "Tree Bark", WallpaperCategory.TEXTURES, R.drawable.wp_tree_bark,
            "Deeply cracked bark of a sapodilla tree.", MotionStyle.NONE
        ),
        photo(
            "pebbles", "Pebbles", WallpaperCategory.TEXTURES, R.drawable.wp_pebbles,
            "Colorful pebbles on a beach in Rethymno, Crete.", MotionStyle.NONE
        ),
        photo(
            "granite", "Granite", WallpaperCategory.TEXTURES, R.drawable.wp_granite,
            "Black and white granite blocks in a quarry.", MotionStyle.NONE
        ),
        // Minimal
        gen(
            "pale_fog", "Pale Fog", WallpaperCategory.MINIMAL, Scene.MESH,
            Palette(0xE4E7EB, 0xF5F6F8, 0xCDD3DA, 0xDCE3EA, 0xBFC7D0),
            "Soft drifting greys, light and quiet."
        ),
        gen(
            "graphite", "Graphite", WallpaperCategory.MINIMAL, Scene.MESH,
            Palette(0x0E0F12, 0x2A2D34, 0x1A1C21, 0x3B3F48, 0x15171B),
            "Barely moving shades of charcoal."
        ),
        gen(
            "sand", "Sand", WallpaperCategory.MINIMAL, Scene.MESH,
            Palette(0xEDE0D4, 0xE6CCB2, 0xDDB892, 0xF5EBE0, 0xD6CCC2),
            "Warm beige and cream."
        ),
        gen(
            "contour_slate", "Contour Slate", WallpaperCategory.MINIMAL, Scene.CONTOURS,
            Palette(0x1E293B, 0x0F172A, 0x94A3B8, 0xE2E8F0, 0x000000),
            "Fine silver contours on slate."
        ),
        gen(
            "contour_ink", "Contour Ink", WallpaperCategory.MINIMAL, Scene.CONTOURS,
            Palette(0xF8F7F4, 0xEEECE7, 0x2B2B2B, 0x6B6B6B, 0xFFFFFF),
            "Fine ink contours on paper white."
        ),
        gen(
            "mesh_mist", "Mist", WallpaperCategory.MINIMAL, Scene.MESH,
            Palette(0xE8EEF2, 0xF7F9FB, 0xD5DEE6, 0xC5D0DA, 0xEEF3F6),
            "A pale mist that barely moves."
        ),
        gen(
            "mesh_linen", "Linen", WallpaperCategory.MINIMAL, Scene.MESH,
            Palette(0xF3EDE4, 0xE7DDD0, 0xD9CBBA, 0xF7F3EC, 0xCDBFAE),
            "Warm linen with a very slow drift."
        ),
        gen(
            "contour_pearl", "Contour Pearl", WallpaperCategory.MINIMAL, Scene.CONTOURS,
            Palette(0xF4F1EC, 0xE7E2DA, 0xB7A99A, 0x8C7E72, 0xFFFFFF),
            "Fine pearl-grey lines on warm paper."
        ),
        gen(
            "contour_ash", "Contour Ash", WallpaperCategory.MINIMAL, Scene.CONTOURS,
            Palette(0x1A1C1E, 0x121416, 0x8E949A, 0xC5CCD1, 0x000000),
            "Ash-colored contours drifting on charcoal."
        ),
        gen(
            "mesh_snow", "Snow Drift", WallpaperCategory.MINIMAL, Scene.MESH,
            Palette(0xF5F7F8, 0xE6EBEE, 0xD5DDE2, 0xFBFCFD, 0xC9D3DA),
            "Almost white, with a slow snow-like drift."
        ),
        photo(
            "lake_matheson", "Lake Matheson", WallpaperCategory.MINIMAL, R.drawable.wp_lake_matheson,
            "Morning fog and a violet reflection on Lake Matheson.", MotionStyle.NONE
        ),
        photo(
            "lake_mcdonald_fog", "Lake McDonald", WallpaperCategory.MINIMAL, R.drawable.wp_lake_mcdonald_fog,
            "Still water and distant peaks at dawn, Glacier National Park.", MotionStyle.NONE
        ),
        photo(
            "quartz_lake_fog", "Quartz Lake", WallpaperCategory.MINIMAL, R.drawable.wp_quartz_lake_fog,
            "Fog lifting off forest and water, Glacier National Park.", MotionStyle.NONE
        ),
        photo(
            "calm_sea", "Calm Sea", WallpaperCategory.MINIMAL, R.drawable.wp_calm_sea,
            "A long exposure of a smooth grey sea.", MotionStyle.NONE
        ),
        photo(
            "dune_walk", "Dune Walk", WallpaperCategory.MINIMAL, R.drawable.wp_dune_walk,
            "A lone hiker on pale dunes under a pastel sky.", MotionStyle.NONE
        ),
        photo(
            "foggy_forest", "Foggy Forest", WallpaperCategory.MINIMAL, R.drawable.wp_foggy_forest,
            "Green trees fading into fog on a hillside.", MotionStyle.NONE
        ),
        photo(
            "snow_field", "Snow Field", WallpaperCategory.MINIMAL, R.drawable.wp_snow_field,
            "A white winter field under a wide sky.", MotionStyle.NONE
        ),
        photo(
            "salt_flats", "Salt Flats", WallpaperCategory.MINIMAL, R.drawable.wp_salt_flats,
            "Pink salt flats beside the sea at Cabo Rojo, Puerto Rico.", MotionStyle.NONE
        ),
        photo(
            "lone_tree_fog", "Lone Tree", WallpaperCategory.MINIMAL, R.drawable.wp_lone_tree_fog,
            "A single tree fading into morning fog.", MotionStyle.NONE, focusX = 0.48f
        ),
        photo(
            "white_dunes", "White Dunes", WallpaperCategory.MINIMAL, R.drawable.wp_white_dunes,
            "Gypsum dunes and ripples at White Sands.", MotionStyle.NONE
        ),
        // City stills: skylines from the water or across open ground.
        photo(
            "manhattan_skyline", "Lower Manhattan", WallpaperCategory.CITY, R.drawable.wp_manhattan_skyline,
            "Lower Manhattan at night from Jersey City, the Tribute in Light rising behind it.",
            MotionStyle.NONE, focusX = 0.42f
        ),
        photo(
            "toronto_day", "Toronto Skyline", WallpaperCategory.CITY, R.drawable.wp_toronto_day,
            "The Toronto skyline and CN Tower across the water from Snake Island.",
            MotionStyle.NONE, focusX = 0.30f
        ),
        photo(
            "taj_mahal", "Taj Mahal", WallpaperCategory.LANDMARKS, R.drawable.wp_taj_mahal,
            "The Taj Mahal at the end of its gardens, Agra.", MotionStyle.NONE
        ),
        photo(
            "colosseum_rome", "Colosseum", WallpaperCategory.LANDMARKS, R.drawable.wp_colosseum_rome,
            "The Colosseum lit at blue hour, Rome.", MotionStyle.NONE
        ),
        photo(
            "christ_redeemer", "Christ the Redeemer", WallpaperCategory.LANDMARKS, R.drawable.wp_christ_redeemer,
            "Christ the Redeemer above a sea of clouds at sunrise, Rio de Janeiro.",
            MotionStyle.NONE, focusX = 0.62f
        ),
        photo(
            "petra_treasury", "Petra Treasury", WallpaperCategory.LANDMARKS, R.drawable.wp_petra_treasury,
            "Al-Khazneh, the Treasury, carved into the rose cliffs of Petra.", MotionStyle.NONE, focusX = 0.45f
        ),
        photo(
            "chichen_itza", "Chichen Itza", WallpaperCategory.LANDMARKS, R.drawable.wp_chichen_itza,
            "El Castillo, the pyramid of Kukulcan at Chichen Itza.", MotionStyle.NONE, focusX = 0.45f
        ),
        photo(
            "great_wall", "Great Wall", WallpaperCategory.LANDMARKS, R.drawable.wp_great_wall,
            "The Great Wall climbing the ridges at Mutianyu.", MotionStyle.NONE, focusX = 0.60f
        ),
        photo(
            "machu_picchu", "Machu Picchu", WallpaperCategory.LANDMARKS, R.drawable.wp_machu_picchu,
            "Machu Picchu on its ridge above the Urubamba valley.", MotionStyle.NONE
        ),
        // Landscape stills
        photo(
            "zion_canyon", "Zion Canyon", WallpaperCategory.LANDSCAPES, R.drawable.wp_zion_canyon,
            "Navajo sandstone cliffs in Zion National Park.", MotionStyle.NONE
        ),
        photo(
            "old_faithful", "Old Faithful", WallpaperCategory.LANDSCAPES, R.drawable.wp_old_faithful,
            "Old Faithful erupting against a blue sky, Yellowstone.", MotionStyle.NONE, focusX = 0.43f
        ),
        photo(
            "moraine_lake", "Moraine Lake", WallpaperCategory.LANDSCAPES, R.drawable.wp_moraine_lake,
            "Moraine Lake below the Valley of the Ten Peaks, Banff.", MotionStyle.NONE, focusX = 0.38f
        ),
        // Space stills
        photo(
            "andromeda", "Andromeda Galaxy", WallpaperCategory.SPACE, R.drawable.wp_andromeda,
            "The Andromeda Galaxy and its two companions.", MotionStyle.NONE
        ),
        photo(
            "earthrise", "Earthrise", WallpaperCategory.SPACE, R.drawable.wp_earthrise,
            "Earth rising over the Moon's Compton crater. NASA Lunar Reconnaissance Orbiter.",
            MotionStyle.NONE, focusY = 0.30f
        ),
        photo(
            "sombrero_galaxy", "Sombrero Galaxy", WallpaperCategory.SPACE, R.drawable.wp_sombrero_galaxy,
            "The Sombrero Galaxy with its dark dust lane, Hubble Space Telescope.", MotionStyle.NONE
        ),
        // Scene stills
        photo(
            "jellyfish_photo", "Spotted Jelly", WallpaperCategory.SCENES, R.drawable.wp_jellyfish_photo,
            "A spotted jellyfish glowing blue in the dark.", MotionStyle.NONE, focusY = 0.40f
        ),
        photo(
            "coral_reef_photo", "Coral Close-up", WallpaperCategory.SCENES, R.drawable.wp_coral_reef_photo,
            "An elephant-ear coral open on the reef, Madagascar.", MotionStyle.NONE
        ),
        photo(
            "campfire_photo", "Campsite at Night", WallpaperCategory.SCENES, R.drawable.wp_campfire_photo,
            "A campfire glowing under string lights in the forest.", MotionStyle.NONE
        ),
        photo(
            "candlelight_photo", "Candlelight", WallpaperCategory.SCENES, R.drawable.wp_candlelight_photo,
            "A candle and a lantern glowing on a table in the dark.", MotionStyle.NONE, focusX = 0.42f
        ),
        photo(
            "lighthouse_photo", "Storm Lighthouse", WallpaperCategory.SCENES, R.drawable.wp_lighthouse_photo,
            "Lightning over the harbour and lighthouse at Port-la-Nouvelle.", MotionStyle.NONE
        ),
        photo(
            "snowy_cabin_photo", "Snowy Cabin", WallpaperCategory.SCENES, R.drawable.wp_snowy_cabin_photo,
            "A cabin glowing warm in the snow at night.", MotionStyle.NONE, focusX = 0.65f
        ),
        photo(
            "lanterns_photo", "Harbour Lights", WallpaperCategory.SCENES, R.drawable.wp_lanterns_photo,
            "Kaohsiung Music Center lit up over the water at night.", MotionStyle.NONE, focusX = 0.28f
        ),
        photo(
            "waves_photo", "Breaking Waves", WallpaperCategory.SCENES, R.drawable.wp_waves_photo,
            "Grey waves breaking along the shore at Sete.", MotionStyle.NONE
        ),
        photo(
            "sea_turtle_photo", "Sea Turtle", WallpaperCategory.SCENES, R.drawable.wp_sea_turtle_photo,
            "A sea turtle gliding over the reef.", MotionStyle.NONE
        ),
        photo(
            "aurora_cabins", "Aurora Cabins", WallpaperCategory.SCENES, R.drawable.wp_aurora_cabins,
            "The aurora over log cabins and mountains at night.", MotionStyle.NONE, focusX = 0.55f
        ),
        // Gradient stills: skies that shade from one color to another.
        photo(
            "belt_of_venus", "Pigeon Point Dusk", WallpaperCategory.GRADIENTS, R.drawable.wp_belt_of_venus,
            "Dusk shading from gold to blue over Pigeon Point Lighthouse.", MotionStyle.NONE, focusX = 0.40f
        ),
        photo(
            "twilight_sky", "Canyon Twilight", WallpaperCategory.GRADIENTS, R.drawable.wp_twilight_sky,
            "Twilight fading from orange to deep blue over the Grand Canyon.", MotionStyle.NONE
        ),
        photo(
            "sunset_clouds", "Pink Clouds", WallpaperCategory.GRADIENTS, R.drawable.wp_sunset_clouds,
            "Pink sunset clouds over the Mekong.", MotionStyle.NONE
        ),
        photo(
            "pastel_dawn", "Pastel Dawn", WallpaperCategory.GRADIENTS, R.drawable.wp_pastel_dawn,
            "A pastel dawn behind Split Rock Lighthouse.", MotionStyle.NONE, focusX = 0.35f
        ),
        photo(
            "ocean_dusk", "Ocean Dusk", WallpaperCategory.GRADIENTS, R.drawable.wp_ocean_dusk,
            "Sunset glow spilling over the ocean.", MotionStyle.NONE
        ),
        photo(
            "desert_dusk", "Night Gradient", WallpaperCategory.GRADIENTS, R.drawable.wp_desert_dusk,
            "The Milky Way rising out of the last blue of dusk.", MotionStyle.NONE
        ),
        photo(
            "afterglow", "Afterglow", WallpaperCategory.GRADIENTS, R.drawable.wp_afterglow,
            "Afterglow over a boardwalk and mountains.", MotionStyle.NONE
        ),
        photo(
            "visayan_sunset", "Visayan Sunset", WallpaperCategory.GRADIENTS, R.drawable.wp_visayan_sunset,
            "Sunset fading from fire to violet over the Visayan Sea.", MotionStyle.NONE
        ),
        photo(
            "golden_hour_sky", "Golden Hour", WallpaperCategory.GRADIENTS, R.drawable.wp_golden_hour_sky,
            "Golden light and soft clouds over the river.", MotionStyle.NONE
        ),
        photo(
            "noctilucent", "Noctilucent Clouds", WallpaperCategory.GRADIENTS, R.drawable.wp_noctilucent,
            "Electric-blue night clouds over a dark horizon.", MotionStyle.NONE
        ),
        // Abstract stills
        photo(
            "oil_water", "Thin Film", WallpaperCategory.ABSTRACT, R.drawable.wp_oil_water,
            "Swirling interference colors in a soap film.", MotionStyle.NONE
        ),
        photo(
            "neon_glass", "Neon Glass", WallpaperCategory.ABSTRACT, R.drawable.wp_neon_glass,
            "Neon light bent through a glass sphere.", MotionStyle.NONE
        ),
        // Wildlife stills
        photo(
            "lion_photo", "Lion", WallpaperCategory.WILDLIFE, R.drawable.wp_lion_photo,
            "A male lion resting on a track, Makalali Game Reserve.", MotionStyle.NONE
        ),
        photo(
            "cheetah_photo", "Cheetah", WallpaperCategory.WILDLIFE, R.drawable.wp_cheetah_photo,
            "A cheetah lying in tall grass.", MotionStyle.NONE, focusX = 0.55f
        ),
        photo(
            "zebra_photo", "Zebras", WallpaperCategory.WILDLIFE, R.drawable.wp_zebra_photo,
            "Plains zebras grazing in the Ngorongoro Conservation Area.", MotionStyle.NONE
        ),
        photo(
            "giraffe_photo", "Giraffes", WallpaperCategory.WILDLIFE, R.drawable.wp_giraffe_photo,
            "Three giraffes on the savanna under a blue sky.", MotionStyle.NONE, focusX = 0.40f
        ),
        photo(
            "leopard_photo", "Leopard", WallpaperCategory.WILDLIFE, R.drawable.wp_leopard_photo,
            "A young leopard in a tree, Kruger National Park.", MotionStyle.NONE, focusX = 0.60f
        ),
        photo(
            "toucan_photo", "Toco Toucan", WallpaperCategory.WILDLIFE, R.drawable.wp_toucan_photo,
            "A toco toucan on a branch, Encontro das Aguas State Park.", MotionStyle.NONE, focusX = 0.35f
        ),
        photo(
            "elephant_herd_photo", "Amboseli Elephants", WallpaperCategory.WILDLIFE, R.drawable.wp_elephant_herd_photo,
            "Elephants grazing below Kilimanjaro, Amboseli.", MotionStyle.NONE, focusX = 0.40f
        ),
        photo(
            "bald_eagle_photo", "Bald Eagle", WallpaperCategory.WILDLIFE, R.drawable.wp_bald_eagle_photo,
            "A bald eagle swooping over the water in Alaska.", MotionStyle.NONE
        ),
        photo(
            "red_fox_photo", "Red Fox", WallpaperCategory.WILDLIFE, R.drawable.wp_red_fox_photo,
            "A red fox in fresh snow, Rautas old-growth forest.", MotionStyle.NONE, focusX = 0.55f
        ),
        photo(
            "orangutan_photo", "Orangutan", WallpaperCategory.WILDLIFE, R.drawable.wp_orangutan_photo,
            "An orangutan by the water's edge.", MotionStyle.NONE, focusX = 0.60f
        )
    )

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun resolveWallpaper(id: String?): WallpaperItem {
        if (id.isNullOrBlank()) return wallpapers.first()
        return wallpapers.firstOrNull { it.id == id } ?: wallpapers.first()
    }

    fun getWallpaperById(id: String): WallpaperItem? = wallpapers.firstOrNull { it.id == id }

    fun getWallpapersByCategory(category: WallpaperCategory?, live: Boolean? = null): List<WallpaperItem> {
        val base = wallpapers
            .filter { category == null || it.category == category }
            .filter { live == null || it.isLive == live }
        return base.sortedBy { if (it.isLive) 0 else 1 }
    }

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

    fun isFoldEffectEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_FOLD_EFFECT_ENABLED, true)

    fun setFoldEffectEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FOLD_EFFECT_ENABLED, enabled).apply()
    }

    // Off matches the iPhone Duo: on the inner screen only the left half moves.
    fun isSymmetricInner(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_SYMMETRIC_INNER, false)

    fun setSymmetricInner(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SYMMETRIC_INNER, enabled).apply()
    }

    fun isPauseOnSaver(context: Context): Boolean = getPrefs(context).getBoolean(KEY_PAUSE_ON_SAVER, true)

    fun setPauseOnSaver(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PAUSE_ON_SAVER, enabled).apply()
    }

    // Holding the cover until Android's 90-degree switch is what makes the cover effect visible,
    // so it is built into the fold effect rather than offered as a separate choice.
    fun isCoverAwakeEnabled(context: Context): Boolean = isFoldEffectEnabled(context)

    /** Hinge angle at which the system last switched cover to inner, or NaN if never seen. */
    fun getMeasuredOpenSwitch(context: Context): Float =
        getPrefs(context).getFloat(KEY_MEASURED_OPEN_SWITCH, Float.NaN)

    /** Hinge angle at which the system last switched inner to cover, or NaN if never seen. */
    fun getMeasuredCloseSwitch(context: Context): Float =
        getPrefs(context).getFloat(KEY_MEASURED_CLOSE_SWITCH, Float.NaN)
}
