package com.lumifold.wallpaper.model

import androidx.annotation.DrawableRes

enum class WallpaperCategory(val label: String) {
    ABSTRACT("Abstract"),
    GRADIENTS("Gradients"),
    LANDSCAPES("Landscapes"),
    SPACE("Space"),
    TEXTURES("Textures"),
    MINIMAL("Minimal")
}

/** Procedural scenes rendered by AGSL at the display's native resolution. */
enum class Scene {
    RIBBONS,
    AURORA,
    MESH,
    DUNES,
    BOKEH,
    CONTOURS,
    NEBULA,
    HORIZON,
    IRIS
}

/** Five sRGB colors (0xRRGGBB) whose meaning is defined by each [Scene]. */
data class Palette(val c0: Int, val c1: Int, val c2: Int, val c3: Int, val c4: Int) {
    val colors: List<Int> get() = listOf(c0, c1, c2, c3, c4)
}

/** How a photo moves when living motion is on. */
enum class MotionStyle(val shaderIndex: Int) {
    /** Slow flowing liquid glass over the whole image. */
    LIQUID(1),

    /** Region-based nature motion driven by a motion map. */
    NATURE(2),

    /** Light travelling along bright features. */
    PULSE(3)
}

/**
 * Motion for a photo. For [MotionStyle.NATURE], [mapResId] is an RGB motion map aligned with the
 * photo: red marks drifting sky, fog and clouds, green marks water, blue marks swaying foliage.
 * Directions are in image fractions per second.
 */
data class PhotoMotion(
    val style: MotionStyle,
    @DrawableRes val mapResId: Int = 0,
    val windX: Float = 0.004f,
    val waterX: Float = 0f,
    val waterY: Float = 0.012f
)

sealed interface WallpaperSource {
    data class Photo(
        @DrawableRes val resId: Int,
        val motion: PhotoMotion,
        val focusX: Float = 0.5f,
        val focusY: Float = 0.5f
    ) : WallpaperSource

    data class Generative(val scene: Scene, val palette: Palette) : WallpaperSource
}

data class WallpaperItem(
    val id: String,
    val title: String,
    val category: WallpaperCategory,
    val source: WallpaperSource,
    val description: String
) {
    val isLive: Boolean get() = source is WallpaperSource.Generative
}
