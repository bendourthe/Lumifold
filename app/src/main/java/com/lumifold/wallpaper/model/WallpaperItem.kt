package com.lumifold.wallpaper.model

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes

enum class WallpaperCategory(val label: String) {
    ABSTRACT("Abstract"),
    GRADIENTS("Gradients"),
    LANDSCAPES("Landscapes"),
    CITY("City"),
    LANDMARKS("Landmarks"),
    SCENES("Scenes"),
    ILLUSTRATED("Illustrated"),
    SPACE("Space"),
    WILDLIFE("Wildlife"),
    TEXTURES("Textures"),
    MINIMAL("Minimal")
}

/** Procedural scenes rendered by AGSL at the display's native resolution. */
enum class Scene {
    RIBBONS,
    MESH,
    BOKEH,
    CONTOURS,
    IRIS,
    BLACK_HOLE,
    GALAXY,
    HILLS,
    AURORA,
    SEA,
    SNOW_FOREST,
    SWIRL
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

    /** Nebula rebuilt in depth: gas and star layers move with a slow camera, stars twinkle. */
    NEBULA(5),

    /** A photo or painting rebuilt in depth from its depth map, seen by a slowly moving camera. */
    DEPTH(6)
}

/**
 * A planet or moon for [MotionStyle.GLOBE], in panel widths. Open, it is centered on the inner
 * display with [innerRadius]; folded, the cover shows its left limb, which starts at [coverEdgeX].
 * The spin axis rolls by [tiltDeg] in the screen and leans [inclinationDeg] toward the viewer.
 * With [night], the texture holds the day map on top and the night lights map below.
 */
data class Globe(
    val spinSeconds: Float = 180f,
    val tiltDeg: Float = 10f,
    val inclinationDeg: Float = 12f,
    val atmosphere: Int = 0x000000,
    val night: Boolean = false,
    val rings: Boolean = false,
    val lightX: Float = -0.75f,
    val lightY: Float = 0.3f,
    val lightZ: Float = 0.6f,
    val innerRadius: Float = 0.62f,
    val coverRadius: Float = 1.0f,
    val coverEdgeX: Float = 0.4f
)

/** Where a wide picture sits on each screen. The cover's right edge cuts through [coverEdgeU]. */
data class ScreenPlace(
    val coverEdgeU: Float,
    val coverSpanU: Float,
    val innerCenterU: Float,
    val innerSpanU: Float
)

sealed interface WallpaperSource {
    data class Photo(
        @DrawableRes val resId: Int,
        val motion: MotionStyle,
        val focusX: Float = 0.5f,
        val focusY: Float = 0.5f,
        val globe: Globe? = null,
        /** Grid thumbnail, when the photo itself is not what the wallpaper looks like. */
        @DrawableRes val thumbResId: Int = 0,
        /** For [MotionStyle.NEBULA]: the star layer split from the photo; [resId] is the starless gas. */
        @DrawableRes val starsResId: Int = 0,
        /** For [MotionStyle.NEBULA] and [MotionStyle.DEPTH]: grayscale depth, white is near. */
        @DrawableRes val depthResId: Int = 0,
        /** Show the whole picture, with bars, instead of filling and cropping to the screen. */
        val fit: Boolean = false,
        val place: ScreenPlace? = null
    ) : WallpaperSource

    data class Generative(val scene: Scene, val palette: Palette) : WallpaperSource

    /** A looping clip, downloaded on demand from [url]; [posterResId] stands in until it plays. */
    data class Video(
        val url: String,
        @DrawableRes val posterResId: Int,
        val sizeMb: Float,
        val focusX: Float = 0.5f,
        val focusY: Float = 0.5f,
        val framing: VideoFraming? = null,
        /** The small loop bundled for grid tiles and previews, cut from the same footage. */
        @RawRes val previewResId: Int = 0
    ) : WallpaperSource
}

/**
 * Places a clip per display instead of filling the shared image plane: open, the frame is centered
 * on the inner display, [innerHeight] panel widths tall; folded, it is [coverHeight] tall, centered
 * at [coverCenterX] panel widths from the hinge. Outside the frame is black space.
 */
data class VideoFraming(val innerHeight: Float, val coverHeight: Float, val coverCenterX: Float)

data class WallpaperItem(
    val id: String,
    val title: String,
    val category: WallpaperCategory,
    val source: WallpaperSource,
    val description: String
) {
    /**
     * A moving picture: a video, a drawn scene, or a planet turning on its axis. A photograph is
     * still, even with a slight depth drift.
     */
    val isLive: Boolean
        get() = source is WallpaperSource.Generative || source is WallpaperSource.Video ||
            (source as? WallpaperSource.Photo)?.motion == MotionStyle.GLOBE

    /** A photograph. Depth and nebula drift are not counted as a live wallpaper. */
    val isStill: Boolean get() = !isLive
}
