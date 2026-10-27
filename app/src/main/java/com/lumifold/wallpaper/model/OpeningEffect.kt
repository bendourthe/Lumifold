package com.lumifold.wallpaper.model

/**
 * Hinge-driven transitions, in display order. [shaderIndex] selects the branch in the AGSL effect
 * stage and must match the `uEffect` cases in FoldShaders.
 */
enum class OpeningEffect(
    val id: String,
    val title: String,
    val summary: String,
    val shaderIndex: Int
) {
    DUO_FROST(
        "duo_frost",
        "Duo Frost",
        "The iPhone Duo look. The moving panel turns to frosted glass, sharp at the hinge and soft at the free edge.",
        0
    ),
    SHATTER(
        "shatter",
        "Shattered Glass",
        "The glass cracks from an impact as the panel lifts. Shards shift and catch the light, then mend as it settles.",
        12
    ),
    FLUTED_GLASS(
        "fluted_glass",
        "Fluted Glass",
        "Reeded glass ribs bend the picture as the panel lifts, then straighten flat.",
        1
    ),
    CRYSTAL(
        "crystal",
        "Crystal",
        "Faceted glass splits the view into shifted shards with softly lit edges.",
        8
    ),
    PRISM(
        "prism",
        "Prism Edge",
        "The free edge splits light into color fringes that settle as the hinge opens.",
        5
    ),
    APERTURE(
        "aperture",
        "Aperture",
        "A camera iris closes as you open the cover and reopens from the crease.",
        2
    ),
    DAWN(
        "dawn",
        "Dawn",
        "Light leaves the cover from the free edge and returns from the crease like sunrise.",
        3
    ),
    WINDOW(
        "window",
        "Window",
        "No blur. The picture stays fixed in space behind the glass while the panel moves.",
        4
    ),
    RIPPLE(
        "ripple",
        "Ripple",
        "Rings spread from the hinge like water and travel with every degree you move it.",
        6
    ),
    MOSAIC(
        "mosaic",
        "Mosaic",
        "The picture breaks into tiles that shrink back to pixels as the panel settles.",
        7
    ),
    COLOR_BLOOM(
        "color_bloom",
        "Color Bloom",
        "Color drains from the moving edge and floods back from the crease.",
        9
    ),
    ZOOM_BURST(
        "zoom_burst",
        "Zoom Burst",
        "A radial motion blur rushes toward the center and resolves as you open.",
        10
    ),
    SILK_WAVE(
        "silk_wave",
        "Silk Wave",
        "A soft fabric fold rolls across the panel in step with the hinge.",
        11
    );

    companion object {
        val DEFAULT = DUO_FROST

        /** Effects shown on the home page before "Show all". */
        const val FEATURED_COUNT = 3

        /** Unknown and pre-0.5 ids (duo_blur, crease_bloom, ...) fall back to [DEFAULT]. */
        fun fromId(id: String?): OpeningEffect = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
