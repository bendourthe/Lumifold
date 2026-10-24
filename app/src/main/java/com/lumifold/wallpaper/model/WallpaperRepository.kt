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
    const val KEY_FOLD_EFFECT_ENABLED = "pref_fold_effect_enabled"
    const val KEY_PAUSE_ON_SAVER = "pref_pause_motion_on_saver"
    const val KEY_PAUSE_BELOW_LEVEL = "pref_pause_motion_below_level"
    const val KEY_PAUSE_LEVEL = "pref_pause_motion_level"
    const val DEFAULT_PAUSE_LEVEL = 20
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
        focusY: Float = 0.5f
    ) = WallpaperItem(id, title, category, WallpaperSource.Photo(resId, motion, focusX, focusY), description)

    private fun video(
        id: String,
        title: String,
        category: WallpaperCategory,
        url: String,
        posterResId: Int,
        sizeMb: Float,
        description: String,
        framing: VideoFraming? = null
    ) = WallpaperItem(id, title, category, WallpaperSource.Video(url, posterResId, sizeMb, framing = framing), description)

    // Full-disk SDO frames: the disk spans about 84% of the frame. Open, the Sun is centered;
    // folded, its left limb sits on the right of the cover with eruptions and space to the left.
    private val SUN_FRAMING = VideoFraming(innerHeight = 1.55f, coverHeight = 2.4f, coverCenterX = 1.45f)

    private fun nebula(id: String, title: String, gasResId: Int, starsResId: Int, thumbResId: Int, description: String) =
        WallpaperItem(
            id, title, WallpaperCategory.SPACE,
            WallpaperSource.Photo(gasResId, MotionStyle.NEBULA, thumbResId = thumbResId, starsResId = starsResId),
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

        // Video loops: public-domain and CC0 footage, credited in docs/reference/photo-credits.md
        video(
            "canyon_clouds", "Canyon Clouds", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/e/ed/B-roll_Video_-_Time-lapse_of_Clouds_over_the_Canyon_-_November_20%2C_2025_%2855087231001%29.webm/B-roll_Video_-_Time-lapse_of_Clouds_over_the_Canyon_-_November_20%2C_2025_%2855087231001%29.webm.1080p.vp9.webm",
            R.drawable.wpv_canyon_clouds, 15.6f,
            "Clouds pouring over the rim of the Grand Canyon. National Park Service time-lapse."
        ),
        video(
            "swiftcurrent_sunrise", "Swiftcurrent Sunrise", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/5/50/Sunrise_at_Swiftcurrent_Lake_%2826145798446%29.webm/Sunrise_at_Swiftcurrent_Lake_%2826145798446%29.webm.1080p.vp9.webm",
            R.drawable.wpv_swiftcurrent_sunrise, 17.7f,
            "Red peaks reflected in Swiftcurrent Lake at sunrise, Glacier National Park."
        ),
        video(
            "alaska_aurora", "Alaska Aurora", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/c/c5/Alaskan_Aurora_Time_Lapse_%28SVS15077%29.webm/Alaskan_Aurora_Time_Lapse_%28SVS15077%29.webm.1080p.vp9.webm",
            R.drawable.wpv_alaska_aurora, 7.1f,
            "Green aurora rippling over Alaska. NASA time-lapse."
        ),
        video(
            "lake_mcdonald_clouds", "Lake McDonald", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/3/3d/Lake_McDonald_8_6_15_%2823809254046%29.webm/Lake_McDonald_8_6_15_%2823809254046%29.webm.1080p.vp9.webm",
            R.drawable.wpv_lake_mcdonald_clouds, 16.0f,
            "Clouds sailing over Lake McDonald, Glacier National Park."
        ),
        video(
            "golden_sunset", "Golden Sunset", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/2/27/Golden_sunset_filmed_in_January.webm/Golden_sunset_filmed_in_January.webm.1080p.vp9.webm",
            R.drawable.wpv_golden_sunset, 11.8f,
            "A fiery gold sunset sky."
        ),
        video(
            "virginia_falls", "Virginia Falls", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/5/51/Virginia_Falls_%2826105159061%29.webm/Virginia_Falls_%2826105159061%29.webm.1080p.vp9.webm",
            R.drawable.wpv_virginia_falls, 10.3f,
            "Virginia Falls tumbling through the forest, Glacier National Park."
        ),
        video(
            "turquoise_waves", "Turquoise Waves", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/1/14/Plage_d%27Isola_Rossa_-_2017_-_vagues.webm/Plage_d%27Isola_Rossa_-_2017_-_vagues.webm.1080p.vp9.webm",
            R.drawable.wpv_turquoise_waves, 10.4f,
            "Turquoise waves rolling onto the beach at Isola Rossa, Corsica."
        ),
        video(
            "misty_river", "Misty River", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/9/9e/Misty_river_47_seconds.webm/Misty_river_47_seconds.webm.1080p.vp9.webm",
            R.drawable.wpv_misty_river, 26.9f,
            "Fog drifting over a calm river."
        ),
        video(
            "snowfall", "Snowfall", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/5/56/Snow_falling_in_Charlton_MA_2025-12-14.webm/Snow_falling_in_Charlton_MA_2025-12-14.webm.1080p.vp9.webm",
            R.drawable.wpv_snowfall, 11.3f,
            "Snow falling quietly in a winter forest."
        ),
        video(
            "meadow_breeze", "Meadow Breeze", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/1/15/Calmly_waving_grass_and_flowers.ogv/Calmly_waving_grass_and_flowers.ogv.1080p.vp9.webm",
            R.drawable.wpv_meadow_breeze, 6.1f,
            "Daisies and grass swaying in the wind."
        ),
        video(
            "desert_night", "Desert Night", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/1/12/Stars_and_Clouds_Over_Headstone_Rock_%2830230087163%29.webm/Stars_and_Clouds_Over_Headstone_Rock_%2830230087163%29.webm.1080p.vp9.webm",
            R.drawable.wpv_desert_night, 4.6f,
            "Stars and clouds wheeling over Headstone Rock, Joshua Tree. National Park Service time-lapse."
        ),
        video(
            "lightning_storm", "Lightning Storm", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/6/66/Lightning_storm_over_Colorado%2C_USA.ogg/Lightning_storm_over_Colorado%2C_USA.ogg.1080p.vp9.webm",
            R.drawable.wpv_lightning_storm, 7.4f,
            "Lightning flashing through a storm over Colorado."
        ),
        video(
            "lenticular_cloud", "Lenticular Cloud", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/a/aa/Lenticular_cloud_over_Longs_Peak%2C_Colorado_%28time_lapse%29.ogv/Lenticular_cloud_over_Longs_Peak%2C_Colorado_%28time_lapse%29.ogv.1080p.vp9.webm",
            R.drawable.wpv_lenticular_cloud, 6.9f,
            "A lenticular cloud swirling over Longs Peak, Colorado."
        ),
        video(
            "yellowstone_falls", "Yellowstone Falls", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/7/79/Upper_Falls_of_the_Yellowstone_River_%2824503121190%29.webm/Upper_Falls_of_the_Yellowstone_River_%2824503121190%29.webm.1080p.vp9.webm",
            R.drawable.wpv_yellowstone_falls, 6.0f,
            "The Upper Falls of the Yellowstone River in snow."
        ),
        video(
            "misty_lake", "Misty Lake", WallpaperCategory.LANDSCAPES,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/c/c5/Misty_Morning_at_Lake_McDonald_%2825569241623%29.webm/Misty_Morning_at_Lake_McDonald_%2825569241623%29.webm.1080p.vp9.webm",
            R.drawable.wpv_misty_lake, 10.9f,
            "Morning mist moving over Lake McDonald."
        ),
        video(
            "shanghai_bund", "Shanghai Bund", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/9/91/Shanghai_Bund%2C_Lujiazui_Skyline_Timelapse.webm/Shanghai_Bund%2C_Lujiazui_Skyline_Timelapse.webm.1080p.vp9.webm",
            R.drawable.wpv_shanghai_bund, 17.6f,
            "The Shanghai skyline turning from dusk to night."
        ),
        video(
            "shibuya_crossing", "Shibuya Crossing", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/4/47/Shibuya_crossing_time-lapse_2019-01-05.webm/Shibuya_crossing_time-lapse_2019-01-05.webm.1080p.vp9.webm",
            R.drawable.wpv_shibuya_crossing, 15.8f,
            "Crowds flowing across Shibuya crossing, Tokyo."
        ),
        video(
            "lujiazui_night", "Lujiazui Night", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/a/aa/Timelapse_de_nuit_de_la_vue_du_quartier_de_Lujiazui.webm/Timelapse_de_nuit_de_la_vue_du_quartier_de_Lujiazui.webm.1080p.vp9.webm",
            R.drawable.wpv_lujiazui_night, 4.0f,
            "The towers of Lujiazui glittering at night."
        ),
        video(
            "eiffel_sunset", "Eiffel Sunset", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/a/a9/Sunset_on_Eiffel_Tower_in_Paris_-_Vimeo.webm",
            R.drawable.wpv_eiffel_sunset, 5.6f,
            "The sun setting behind the Eiffel Tower, Paris."
        ),
        video(
            "berlin_dusk", "Berlin Dusk", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/b/b7/Berlin_Bundesautobahn_100_2026-03-25_mov03.webm/Berlin_Bundesautobahn_100_2026-03-25_mov03.webm.1080p.vp9.webm",
            R.drawable.wpv_berlin_dusk, 4.4f,
            "Headlights streaming along a Berlin motorway at dusk."
        ),
        video(
            "berlin_motorway", "Berlin Motorway", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/0/01/Berlin_Bundesautobahn_100_2026-03-25_mov04.webm/Berlin_Bundesautobahn_100_2026-03-25_mov04.webm.1080p.vp9.webm",
            R.drawable.wpv_berlin_motorway, 5.9f,
            "Evening traffic on the Berlin city motorway."
        ),
        video(
            "helsinki_harbour", "Helsinki Harbour", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/9/9e/Ajax_South_Harbour_Helsinki_20260214_02.webm/Ajax_South_Harbour_Helsinki_20260214_02.webm.1080p.vp9.webm",
            R.drawable.wpv_helsinki_harbour, 12.6f,
            "A ship breaking through harbour ice in Helsinki."
        ),
        video(
            "la_night", "Los Angeles Night", WallpaperCategory.CITY,
            "https://upload.wikimedia.org/wikipedia/commons/9/92/DTLA_by_night%2C_Los_Angeles_July_2022.webm",
            R.drawable.wpv_la_night, 30.5f,
            "Downtown Los Angeles glowing at night."
        ),
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

        // Landscapes: public-domain and CC0 photographs, credited in docs/reference/photo-credits.md
        photo(
            "teton_mirror", "Teton Mirror", WallpaperCategory.LANDSCAPES, R.drawable.wp_teton_mirror,
            "The Teton Range reflected in a still lake, Grand Teton National Park.", MotionStyle.NONE
        ),
        photo(
            "zion_afterglow", "Zion Afterglow", WallpaperCategory.LANDSCAPES, R.drawable.wp_zion_afterglow,
            "Sandstone cliffs under a burning evening sky, Zion National Park.", MotionStyle.NONE
        ),
        photo(
            "rainier_alpenglow", "Mount Rainier", WallpaperCategory.LANDSCAPES, R.drawable.wp_rainier_alpenglow,
            "Mount Rainier rising over dark evergreen forest.", MotionStyle.NONE
        ),
        photo(
            "rainier_falls", "Rainier Falls", WallpaperCategory.LANDSCAPES, R.drawable.wp_rainier_falls,
            "A waterfall below the snowfields of Mount Rainier.", MotionStyle.NONE
        ),
        photo(
            "rainier_meadow", "Autumn Meadow", WallpaperCategory.LANDSCAPES, R.drawable.wp_rainier_meadow,
            "Golden autumn meadows on the Mazama Ridge Trail, Mount Rainier.", MotionStyle.NONE
        ),
        photo(
            "arches_window", "Arches Window", WallpaperCategory.LANDSCAPES, R.drawable.wp_arches_window,
            "A red rock arch framing blue sky, Arches National Park.", MotionStyle.NONE
        ),
        photo(
            "death_valley_dunes", "Death Valley Dunes", WallpaperCategory.LANDSCAPES, R.drawable.wp_death_valley_dunes,
            "Sand dunes at sunset in Death Valley.", MotionStyle.NONE
        ),
        photo(
            "olympic_sunset", "Olympic Sunset", WallpaperCategory.LANDSCAPES, R.drawable.wp_olympic_sunset,
            "Layered ridges fading into an orange sunset, Olympic National Park.", MotionStyle.NONE
        ),
        photo(
            "olympic_blue_hour", "Blue Ridges", WallpaperCategory.LANDSCAPES, R.drawable.wp_olympic_blue_hour,
            "Blue mountain ridges over a quiet lake, Olympic National Park.", MotionStyle.NONE
        ),
        photo(
            "sea_of_clouds", "Sea of Clouds", WallpaperCategory.LANDSCAPES, R.drawable.wp_sea_of_clouds,
            "Snowy peaks above a valley filled with cloud, Olympic National Park.", MotionStyle.NONE
        ),
        photo(
            "forest_light", "Forest Light", WallpaperCategory.LANDSCAPES, R.drawable.wp_forest_light,
            "Sunbeams through mist in an old-growth forest.", MotionStyle.NONE
        ),
        photo(
            "lake_crescent", "Lake Crescent", WallpaperCategory.LANDSCAPES, R.drawable.wp_lake_crescent,
            "Wild blossoms over the blue water of Lake Crescent.", MotionStyle.NONE
        ),
        photo(
            "glacier_river", "Glacier River", WallpaperCategory.LANDSCAPES, R.drawable.wp_glacier_river,
            "A turquoise river winding through autumn forest.", MotionStyle.NONE
        ),
        photo(
            "crater_lake", "Crater Lake", WallpaperCategory.LANDSCAPES, R.drawable.wp_crater_lake,
            "Deep blue Crater Lake and Wizard Island.", MotionStyle.NONE
        ),
        photo(
            "crater_lake_dawn", "Crater Lake Dawn", WallpaperCategory.LANDSCAPES, R.drawable.wp_crater_lake_dawn,
            "First light over the rim of Crater Lake.", MotionStyle.NONE
        ),
        photo(
            "grand_canyon_sunset", "Grand Canyon", WallpaperCategory.LANDSCAPES, R.drawable.wp_grand_canyon_sunset,
            "The South Rim of the Grand Canyon at sunset.", MotionStyle.NONE
        ),
        photo(
            "emerald_bay", "Emerald Bay", WallpaperCategory.LANDSCAPES, R.drawable.wp_emerald_bay,
            "Sunset over Emerald Bay, Lake Tahoe.", MotionStyle.NONE
        ),
        photo(
            "mountain_lake", "Mountain Lake", WallpaperCategory.LANDSCAPES, R.drawable.wp_mountain_lake,
            "Peaks mirrored in a rippled mountain lake.", MotionStyle.NONE
        ),
        photo(
            "lofoten_aurora", "Lofoten Aurora", WallpaperCategory.LANDSCAPES, R.drawable.wp_lofoten_aurora,
            "Green northern lights over the mountains of Lofoten, Norway.", MotionStyle.NONE
        ),

        // Illustrated
        gen(
            "aurora_veil", "Aurora Veil", WallpaperCategory.ILLUSTRATED, Scene.AURORA,
            Palette(0x030712, 0x0F2A3A, 0x34D399, 0xA855F7, 0x05080C),
            "Green and violet curtains over a mountain ridge."
        ),
        gen(
            "polar_night", "Polar Night", WallpaperCategory.ILLUSTRATED, Scene.AURORA,
            Palette(0x01040A, 0x0B1A2E, 0x22D3EE, 0x34D399, 0x020409),
            "Cyan aurora over a black horizon."
        ),
        gen(
            "crimson_aurora", "Crimson Aurora", WallpaperCategory.ILLUSTRATED, Scene.AURORA,
            Palette(0x0A0308, 0x2A0F1E, 0xF43F5E, 0xFBBF24, 0x07030A),
            "Rare red and gold aurora over dark hills."
        ),
        gen(
            "sahara", "Sahara", WallpaperCategory.ILLUSTRATED, Scene.DUNES,
            Palette(0x2E3A6E, 0xF7A35C, 0xFFE8B0, 0xE59A5B, 0x7A3B2E),
            "Layered dunes under a low evening sun."
        ),
        gen(
            "moonlit_dunes", "Moonlit Dunes", WallpaperCategory.ILLUSTRATED, Scene.DUNES,
            Palette(0x050816, 0x1E2A4A, 0xE8EEF8, 0x5B6B8C, 0x141B2E),
            "Cold dunes under a full moon."
        ),
        gen(
            "rose_dunes", "Rose Dunes", WallpaperCategory.ILLUSTRATED, Scene.DUNES,
            Palette(0x4C2A5E, 0xFFB4A2, 0xFFE5D9, 0xE5989B, 0x6D3B47),
            "Pink dunes in a lilac dusk."
        ),
        gen(
            "still_sea", "Still Sea", WallpaperCategory.ILLUSTRATED, Scene.HORIZON,
            Palette(0x1C2541, 0xF4A261, 0xFFF1D6, 0x14213D, 0xFFD6A5),
            "A low sun over a calm sea."
        ),
        gen(
            "arctic_sea", "Arctic Sea", WallpaperCategory.ILLUSTRATED, Scene.HORIZON,
            Palette(0x0B2545, 0xE0FBFC, 0xFFFFFF, 0x13315C, 0xC2DFE3),
            "A pale sun over icy blue water."
        ),
        gen(
            "violet_hour", "Violet Hour", WallpaperCategory.ILLUSTRATED, Scene.HORIZON,
            Palette(0x240046, 0xFF7B00, 0xFFD000, 0x10002B, 0xC77DFF),
            "Orange sun sinking into a violet sea."
        ),
        gen(
            "carina", "Carina", WallpaperCategory.ILLUSTRATED, Scene.NEBULA,
            Palette(0x02030A, 0x7C3AED, 0x0EA5E9, 0xF472B6, 0xE0E7FF),
            "Violet and cyan gas clouds in a deep star field."
        ),
        gen(
            "ember_nebula", "Ember Nebula", WallpaperCategory.ILLUSTRATED, Scene.NEBULA,
            Palette(0x050203, 0xEA580C, 0xBE123C, 0xFDE68A, 0xFFEDD5),
            "Glowing orange and crimson nebula."
        ),
        gen(
            "teal_nebula", "Teal Nebula", WallpaperCategory.ILLUSTRATED, Scene.NEBULA,
            Palette(0x01080A, 0x14B8A6, 0x2563EB, 0x86EFAC, 0xECFEFF),
            "Teal and blue clouds lit by young stars."
        ),

        // Space: NASA, ESA and CC0 images, credited in docs/reference/photo-credits.md
        video(
            "sun_flare_304", "Solar Flare", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/d/dd/An_X1_0_Class_Solar_Flare_-_January_10%2C_2023_%28SVS5066_-_20230110_AR13186X10_AIA304_PSF_2048p30%29.webm/An_X1_0_Class_Solar_Flare_-_January_10%2C_2023_%28SVS5066_-_20230110_AR13186X10_AIA304_PSF_2048p30%29.webm.1080p.vp9.webm",
            R.drawable.wpv_sun_flare_304, 19.7f,
            "The Sun in extreme ultraviolet as a strong flare erupts. NASA Solar Dynamics Observatory.", framing = SUN_FRAMING
        ),
        video(
            "sun_corona_171", "Golden Corona", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/c/c1/An_X1_0_Class_Solar_Flare_-_January_10%2C_2023_%28SVS5066_-_20230110_AR13186X10_AIA171_PSF_2048p30%29.webm/An_X1_0_Class_Solar_Flare_-_January_10%2C_2023_%28SVS5066_-_20230110_AR13186X10_AIA171_PSF_2048p30%29.webm.1080p.vp9.webm",
            R.drawable.wpv_sun_corona_171, 18.0f,
            "Magnetic loops shimmering in the Sun's corona. NASA Solar Dynamics Observatory.", framing = SUN_FRAMING
        ),
        video(
            "sun_teal_131", "Flaring Sun", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/0/0a/Solar_X-Flare_-_April_20%2C_2022_%28X2_2_class%29_%28SVS4999_-_20220420_AR12992X22_AIA131_PSF_2048p30%29.webm/Solar_X-Flare_-_April_20%2C_2022_%28X2_2_class%29_%28SVS4999_-_20220420_AR12992X22_AIA131_PSF_2048p30%29.webm.1080p.vp9.webm",
            R.drawable.wpv_sun_teal_131, 18.9f,
            "The hottest flaring plasma glowing teal. NASA Solar Dynamics Observatory.", framing = SUN_FRAMING
        ),
        video(
            "prominence_eruption", "Prominence Eruption", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/7/7b/SDO_Video_Toolkit_%28SVS14126_-_171-304-June_21_PE%29.webm?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original",
            R.drawable.wpv_prominence_eruption, 2.2f,
            "A prominence lifting off the Sun's edge. NASA Solar Dynamics Observatory."
        ),
        video(
            "solar_eruption", "Solar Eruption", WallpaperCategory.SPACE,
            "https://upload.wikimedia.org/wikipedia/commons/d/d3/SDO_Video_Toolkit_%28SVS14126_-_Oct2_Eruption_2%29.webm?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original",
            R.drawable.wpv_solar_eruption, 2.3f,
            "Plasma arcing off the limb of the Sun. NASA Solar Dynamics Observatory."
        ),
        globe(
            "saturn_globe", "Saturn", R.drawable.wpg_saturn, R.drawable.wpt_saturn,
            Globe(spinSeconds = 120f, tiltDeg = -8f, inclinationDeg = 22f, atmosphere = 0x2A251C, rings = true,
                innerRadius = 0.4f, coverRadius = 0.85f, coverEdgeX = 0.45f),
            "Saturn turning inside its rings, colored from Cassini's natural-color view."
        ),
        globe(
            "earth_night_globe", "Earth at Night", R.drawable.wpg_earth, R.drawable.wpt_earth_night,
            Globe(spinSeconds = 160f, tiltDeg = 23f, atmosphere = 0x5A9BFF, night = true, lightX = 0.35f, lightY = 0.1f, lightZ = -0.93f),
            "The night side of Earth turning slowly, its cities glowing. NASA city lights map."
        ),
        globe(
            "earth_globe", "Earth", R.drawable.wpg_earth, R.drawable.wpt_earth,
            Globe(spinSeconds = 160f, tiltDeg = 23f, atmosphere = 0x5A9BFF, night = true),
            "Earth turning slowly, city lights glowing on the night side. NASA Blue Marble and city lights maps."
        ),
        globe(
            "moon_globe", "The Moon", R.drawable.wpg_moon, R.drawable.wpt_moon,
            Globe(spinSeconds = 240f, tiltDeg = 6f),
            "The Moon rotating in sunlight. NASA Lunar Reconnaissance Orbiter color map."
        ),
        globe(
            "mars_globe", "Mars", R.drawable.wpg_mars, R.drawable.wpt_mars,
            Globe(spinSeconds = 170f, tiltDeg = 25f, atmosphere = 0xE08A5A),
            "Mars turning under a thin dusty atmosphere. NASA Viking color mosaic."
        ),
        globe(
            "jupiter_globe", "Jupiter", R.drawable.wpg_jupiter, R.drawable.wpt_jupiter,
            Globe(spinSeconds = 110f, tiltDeg = 3f, atmosphere = 0xCCB38C),
            "Jupiter's cloud bands and Great Red Spot rotating. NASA Cassini map."
        ),
        globe(
            "mercury_globe", "Mercury", R.drawable.wpg_mercury, R.drawable.wpt_mercury,
            Globe(spinSeconds = 260f, tiltDeg = 2f),
            "Cratered Mercury turning close to the Sun. NASA MESSENGER mosaic."
        ),
        nebula(
            "pillars_webb", "Pillars of Creation", R.drawable.wp_pillars_webb, R.drawable.wpn_pillars_webb, R.drawable.wpt_pillars_webb,
            "The Pillars of Creation in near-infrared, James Webb Space Telescope."
        ),
        nebula(
            "pillars_hubble", "Pillars, Hubble", R.drawable.wp_pillars_hubble, R.drawable.wpn_pillars_hubble, R.drawable.wpt_pillars_hubble,
            "The Eagle Nebula pillars in visible light, Hubble Space Telescope."
        ),
        nebula(
            "cosmic_cliffs", "Cosmic Cliffs", R.drawable.wp_cosmic_cliffs, R.drawable.wpn_cosmic_cliffs, R.drawable.wpt_cosmic_cliffs,
            "The star-forming edge of the Carina Nebula, James Webb Space Telescope."
        ),
        nebula(
            "tarantula", "Tarantula Nebula", R.drawable.wp_tarantula, R.drawable.wpn_tarantula, R.drawable.wpt_tarantula,
            "Glowing gas and young stars in 30 Doradus, Hubble Space Telescope."
        ),
        nebula(
            "orion_nebula", "Orion Nebula", R.drawable.wp_orion_nebula, R.drawable.wpn_orion_nebula, R.drawable.wpt_orion_nebula,
            "The heart of the Orion Nebula, Hubble Space Telescope."
        ),
        nebula(
            "helix", "Helix Nebula", R.drawable.wp_helix, R.drawable.wpn_helix, R.drawable.wpt_helix,
            "The Helix Nebula in infrared, Spitzer Space Telescope."
        ),
        nebula(
            "ring_nebula", "Ring Nebula", R.drawable.wp_ring_nebula, R.drawable.wpn_ring_nebula, R.drawable.wpt_ring_nebula,
            "The Ring Nebula, Messier 57, Hubble Space Telescope."
        ),
        nebula(
            "crab_nebula", "Crab Nebula", R.drawable.wp_crab_nebula, R.drawable.wpn_crab_nebula, R.drawable.wpt_crab_nebula,
            "The remains of a supernova seen in 1054, Hubble Space Telescope."
        ),
        nebula(
            "cygnus_loop", "Cygnus Loop", R.drawable.wp_cygnus_loop, R.drawable.wpn_cygnus_loop, R.drawable.wpt_cygnus_loop,
            "Blue filaments of a supernova remnant in ultraviolet light."
        ),
        photo(
            "whirlpool", "Whirlpool Galaxy", WallpaperCategory.SPACE, R.drawable.wp_whirlpool,
            "The spiral arms of Messier 51, Hubble Space Telescope.", MotionStyle.COSMOS
        ),
        photo(
            "andromeda", "Andromeda", WallpaperCategory.SPACE, R.drawable.wp_andromeda,
            "The Andromeda Galaxy in a dense field of stars.", MotionStyle.COSMOS
        ),
        photo(
            "backlit_saturn", "Backlit Saturn", WallpaperCategory.SPACE, R.drawable.wp_backlit_saturn,
            "Saturn eclipsing the Sun, its rings glowing, from Cassini.", MotionStyle.COSMOS
        ),
        photo(
            "saturn_rings", "Saturn", WallpaperCategory.SPACE, R.drawable.wp_saturn_rings,
            "Saturn and its rings in natural color, from Cassini.", MotionStyle.COSMOS
        ),
        photo(
            "full_moon", "Full Moon", WallpaperCategory.SPACE, R.drawable.wp_full_moon,
            "The full Moon in sharp detail.", MotionStyle.COSMOS
        ),
        photo(
            "earthrise", "Earthrise", WallpaperCategory.SPACE, R.drawable.wp_earthrise,
            "Earth rising over the lunar horizon, Apollo 8.", MotionStyle.COSMOS, focusY = 0.3f
        ),
        photo(
            "nile_at_night", "Nile at Night", WallpaperCategory.SPACE, R.drawable.wp_nile_at_night,
            "Cairo and the Nile Delta at night, from the International Space Station.", MotionStyle.COSMOS
        ),
        photo(
            "aurora_from_orbit", "Aurora from Orbit", WallpaperCategory.SPACE, R.drawable.wp_aurora_from_orbit,
            "The aurora australis seen from the International Space Station.", MotionStyle.COSMOS
        ),
        photo(
            "joshua_tree_night", "Joshua Tree Night", WallpaperCategory.SPACE, R.drawable.wp_joshua_tree_night,
            "The Milky Way over Joshua Tree National Park.", MotionStyle.COSMOS
        ),

        // Textures
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

    fun isFoldEffectEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_FOLD_EFFECT_ENABLED, true)

    fun setFoldEffectEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FOLD_EFFECT_ENABLED, enabled).apply()
    }

    fun isPauseOnSaver(context: Context): Boolean = getPrefs(context).getBoolean(KEY_PAUSE_ON_SAVER, true)

    fun setPauseOnSaver(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PAUSE_ON_SAVER, enabled).apply()
    }

    fun isPauseBelowLevel(context: Context): Boolean = getPrefs(context).getBoolean(KEY_PAUSE_BELOW_LEVEL, false)

    fun setPauseBelowLevel(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PAUSE_BELOW_LEVEL, enabled).apply()
    }

    fun getPauseLevel(context: Context): Int =
        getPrefs(context).getInt(KEY_PAUSE_LEVEL, DEFAULT_PAUSE_LEVEL).coerceIn(5, 80)

    fun setPauseLevel(context: Context, level: Int) {
        getPrefs(context).edit().putInt(KEY_PAUSE_LEVEL, level.coerceIn(5, 80)).apply()
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
