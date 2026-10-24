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
        photo(
            "pillars_webb", "Pillars of Creation", WallpaperCategory.SPACE, R.drawable.wp_pillars_webb,
            "The Pillars of Creation in near-infrared, James Webb Space Telescope.", MotionStyle.NONE
        ),
        photo(
            "pillars_hubble", "Pillars, Hubble", WallpaperCategory.SPACE, R.drawable.wp_pillars_hubble,
            "The Eagle Nebula pillars in visible light, Hubble Space Telescope.", MotionStyle.NONE
        ),
        photo(
            "cosmic_cliffs", "Cosmic Cliffs", WallpaperCategory.SPACE, R.drawable.wp_cosmic_cliffs,
            "The star-forming edge of the Carina Nebula, James Webb Space Telescope.", MotionStyle.NONE
        ),
        photo(
            "tarantula", "Tarantula Nebula", WallpaperCategory.SPACE, R.drawable.wp_tarantula,
            "Glowing gas and young stars in 30 Doradus, Hubble Space Telescope.", MotionStyle.NONE
        ),
        photo(
            "orion_nebula", "Orion Nebula", WallpaperCategory.SPACE, R.drawable.wp_orion_nebula,
            "The heart of the Orion Nebula, Hubble Space Telescope.", MotionStyle.NONE
        ),
        photo(
            "helix", "Helix Nebula", WallpaperCategory.SPACE, R.drawable.wp_helix,
            "The Helix Nebula in infrared, Spitzer Space Telescope.", MotionStyle.NONE
        ),
        photo(
            "ring_nebula", "Ring Nebula", WallpaperCategory.SPACE, R.drawable.wp_ring_nebula,
            "The Ring Nebula, Messier 57, Hubble Space Telescope.", MotionStyle.NONE
        ),
        photo(
            "crab_nebula", "Crab Nebula", WallpaperCategory.SPACE, R.drawable.wp_crab_nebula,
            "The remains of a supernova seen in 1054, Hubble Space Telescope.", MotionStyle.NONE
        ),
        photo(
            "cygnus_loop", "Cygnus Loop", WallpaperCategory.SPACE, R.drawable.wp_cygnus_loop,
            "Blue filaments of a supernova remnant in ultraviolet light.", MotionStyle.NONE
        ),
        photo(
            "whirlpool", "Whirlpool Galaxy", WallpaperCategory.SPACE, R.drawable.wp_whirlpool,
            "The spiral arms of Messier 51, Hubble Space Telescope.", MotionStyle.NONE
        ),
        photo(
            "andromeda", "Andromeda", WallpaperCategory.SPACE, R.drawable.wp_andromeda,
            "The Andromeda Galaxy in a dense field of stars.", MotionStyle.NONE
        ),
        photo(
            "backlit_saturn", "Backlit Saturn", WallpaperCategory.SPACE, R.drawable.wp_backlit_saturn,
            "Saturn eclipsing the Sun, its rings glowing, from Cassini.", MotionStyle.NONE
        ),
        photo(
            "saturn_rings", "Saturn", WallpaperCategory.SPACE, R.drawable.wp_saturn_rings,
            "Saturn and its rings in natural color, from Cassini.", MotionStyle.NONE
        ),
        photo(
            "full_moon", "Full Moon", WallpaperCategory.SPACE, R.drawable.wp_full_moon,
            "The full Moon in sharp detail.", MotionStyle.NONE
        ),
        photo(
            "earthrise", "Earthrise", WallpaperCategory.SPACE, R.drawable.wp_earthrise,
            "Earth rising over the lunar horizon, Apollo 8.", MotionStyle.NONE, focusY = 0.3f
        ),
        photo(
            "nile_at_night", "Nile at Night", WallpaperCategory.SPACE, R.drawable.wp_nile_at_night,
            "Cairo and the Nile Delta at night, from the International Space Station.", MotionStyle.NONE
        ),
        photo(
            "aurora_from_orbit", "Aurora from Orbit", WallpaperCategory.SPACE, R.drawable.wp_aurora_from_orbit,
            "The aurora australis seen from the International Space Station.", MotionStyle.NONE
        ),
        photo(
            "joshua_tree_night", "Joshua Tree Night", WallpaperCategory.SPACE, R.drawable.wp_joshua_tree_night,
            "The Milky Way over Joshua Tree National Park.", MotionStyle.NONE
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
