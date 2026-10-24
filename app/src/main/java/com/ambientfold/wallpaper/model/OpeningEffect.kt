package com.ambientfold.wallpaper.model

/**
 * Hinge-driven transitions. [shaderIndex] selects the branch in the AGSL effect stage and must
 * match the order of the `uEffect` cases in FoldShaders.
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
    FLUTED_GLASS(
        "fluted_glass",
        "Fluted Glass",
        "Reeded glass ribs bend the picture as the panel lifts, then straighten flat.",
        1
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
    PRISM(
        "prism",
        "Prism Edge",
        "The free edge splits light into color fringes that settle as the hinge opens.",
        5
    );

    companion object {
        val DEFAULT = DUO_FROST

        /** Unknown and pre-0.5 ids (duo_blur, crease_bloom, ...) fall back to [DEFAULT]. */
        fun fromId(id: String?): OpeningEffect = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
