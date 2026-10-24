package com.lumifold.wallpaper.model

import androidx.annotation.DrawableRes

enum class WallpaperCategory(val label: String) {
    ABSTRACT("Abstract"),
    GRADIENTS("Gradients"),
    LANDSCAPES("Landscapes"),
    CITY("City"),
    SCENES("Scenes"),
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
    IRIS,
    SUN
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
    COSMOS(3),

    /** The photo is an equirectangular surface map wrapped on a slowly rotating, lit sphere. */
    GLOBE(4),

    /** Nebula gas drifting on a slow flow while the stars stay fixed and twinkle. */
    NEBULA(5)
}

/**
 * A planet or moon for [MotionStyle.GLOBE]. Position and radius are in panel widths from the hinge
 * ([centerY] is a fraction of the plane height). With [night], the texture holds the day map on top
 * and the night lights map below.
 */
data class Globe(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radius: Float = 0.46f,
    val spinSeconds: Float = 180f,
    val tiltDeg: Float = 10f,
    val atmosphere: Int = 0x000000,
    val night: Boolean = false
)

sealed interface WallpaperSource {
    data class Photo(
        @DrawableRes val resId: Int,
        val motion: MotionStyle,
        val focusX: Float = 0.5f,
        val focusY: Float = 0.5f,
        val globe: Globe? = null,
        /** Grid thumbnail, when the photo itself is not what the wallpaper looks like. */
        @DrawableRes val thumbResId: Int = 0
    ) : WallpaperSource

    data class Generative(val scene: Scene, val palette: Palette) : WallpaperSource

    /** A looping clip, downloaded on demand from [url]; [posterResId] stands in until it plays. */
    data class Video(
        val url: String,
        @DrawableRes val posterResId: Int,
        val sizeMb: Float,
        val focusX: Float = 0.5f
    ) : WallpaperSource
}

data class WallpaperItem(
    val id: String,
    val title: String,
    val category: WallpaperCategory,
    val source: WallpaperSource,
    val description: String
) {
    val isLive: Boolean get() = source is WallpaperSource.Generative || source is WallpaperSource.Video
}
