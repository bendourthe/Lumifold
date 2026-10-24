package com.lumifold.wallpaper.model

import androidx.annotation.DrawableRes

enum class WallpaperCategory(val label: String) {
    ABSTRACT("Abstract"),
    GRADIENTS("Gradients"),
    LANDSCAPES("Landscapes"),
    ILLUSTRATED("Illustrated"),
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
    /** A still photograph. */
    NONE(0),

    /** Slow flowing liquid glass over the whole image. */
    LIQUID(1),

    /** Light travelling along bright features. */
    PULSE(2),

    /** A slow camera drift with twinkling stars. */
    COSMOS(3)
}

sealed interface WallpaperSource {
    data class Photo(
        @DrawableRes val resId: Int,
        val motion: MotionStyle,
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
