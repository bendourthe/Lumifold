package com.lumifold.wallpaper.render

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.lumifold.wallpaper.model.Globe
import com.lumifold.wallpaper.model.OpeningEffect
import kotlin.math.PI
import com.lumifold.wallpaper.model.WallpaperItem
import com.lumifold.wallpaper.model.WallpaperSource

/** One display surface as the fold shader sees it. */
data class SurfaceSpec(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val isInner: Boolean,
    /** Natural height of this surface in panel widths. */
    val surfaceHeight: Float,
    /** Height of the shared image plane in panel widths. */
    val planeHeight: Float
)

/**
 * A compiled fold shader for one wallpaper. Photo wallpapers need a [BlurPyramid] attached before
 * they can draw; generative wallpapers are ready immediately.
 */
class WallpaperShader private constructor(
    val item: WallpaperItem,
    val shader: RuntimeShader
) {
    var pyramid: BlurPyramid? = null
        private set

    /** Latest frame of a video wallpaper (its poster until the clip plays). */
    var videoFrame: Bitmap? = null
        private set

    val isReady: Boolean
        get() = when (item.source) {
            is WallpaperSource.Generative -> true
            is WallpaperSource.Photo -> pyramid != null
            is WallpaperSource.Video -> videoFrame != null
        }

    fun setVideoFrame(frame: Bitmap) {
        videoFrame = frame
        shader.setInputShader("uVideo", BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
        })
        shader.setFloatUniform("uVidSize", frame.width.toFloat(), frame.height.toFloat())
        val video = item.source as WallpaperSource.Video
        shader.setFloatUniform("uFocusX", video.focusX)
        val f = video.framing
        shader.setFloatUniform(
            "uFrame", f?.innerHeight ?: 0f, f?.coverHeight ?: 0f, f?.coverCenterX ?: 0f, if (f != null) 1f else 0f
        )
    }

    fun attachPyramid(p: BlurPyramid) {
        pyramid = p
        val base = p.levels[0]
        val photo = item.source as WallpaperSource.Photo
        // A globe map wraps around in longitude.
        val tileX = if (photo.globe != null) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
        p.levels.forEachIndexed { k, bmp ->
            val bs = BitmapShader(bmp, tileX, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
                setLocalMatrix(Matrix().apply {
                    setScale(base.width.toFloat() / bmp.width, base.height.toFloat() / bmp.height)
                })
            }
            shader.setInputShader("L$k", bs)
        }
        shader.setFloatUniform("uTexSize", base.width.toFloat(), base.height.toFloat())
        shader.setInputShader("uStars", BitmapShader(p.aux ?: NO_STARS, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
            if (p.aux == null) setLocalMatrix(Matrix().apply { setScale(base.width.toFloat(), base.height.toFloat()) })
        })
        shader.setFloatUniform("uStyle", photo.motion.shaderIndex.toFloat())
        val g = photo.globe ?: Globe()
        shader.setFloatUniform(
            "uGlobe", g.coverEdgeX + g.coverRadius, g.coverRadius, g.innerRadius, (2.0 * PI / g.spinSeconds).toFloat()
        )
        shader.setFloatUniform(
            "uGlobe2",
            (g.tiltDeg * PI / 180.0).toFloat(),
            ((g.atmosphere shr 16) and 0xFF) / 255f,
            ((g.atmosphere shr 8) and 0xFF) / 255f,
            (g.atmosphere and 0xFF) / 255f
        )
        shader.setFloatUniform(
            "uGlobe3", (g.inclinationDeg * PI / 180.0).toFloat(), if (g.rings) 1f else 0f, if (g.night) 1f else 0f, 0f
        )
        shader.setFloatUniform("uLight", g.lightX, g.lightY, g.lightZ)
    }

    /** A second instance with its own uniforms, sharing the compiled effect and pyramid. */
    fun duplicate(): WallpaperShader {
        val copy = WallpaperShader(item, RuntimeShader(sourceFor(item)))
        copy.applyPalette()
        pyramid?.let { copy.attachPyramid(it) }
        videoFrame?.let { copy.setVideoFrame(it) }
        return copy
    }

    fun bind(
        spec: SurfaceSpec,
        angleRad: Float,
        timeSec: Float,
        effect: OpeningEffect,
        intensity: Float,
        motion: Boolean,
        foldEffect: Boolean = true,
        eyeX: Float = if (spec.isInner) 0f else 0.5f,
        eyeY: Float = spec.planeHeight * 0.5f,
        eyeZ: Float = FoldOptics.VIEW_DISTANCE.toFloat()
    ) {
        shader.setFloatUniform("uEye", eyeX, eyeY, eyeZ)
        shader.setFloatUniform("uSize", spec.width.toFloat(), spec.height.toFloat())
        shader.setFloatUniform("uRot", spec.rotation.toFloat())
        shader.setFloatUniform("uInner", if (spec.isInner) 1f else 0f)
        shader.setFloatUniform("uAngle", angleRad)
        shader.setFloatUniform("uPlaneH", spec.planeHeight)
        shader.setFloatUniform("uSurfH", spec.surfaceHeight)
        shader.setFloatUniform("uIntensity", intensity)
        shader.setFloatUniform("uTime", timeSec)
        shader.setFloatUniform("uEffect", if (foldEffect) effect.shaderIndex.toFloat() else NO_FOLD_INDEX)
        if (item.source is WallpaperSource.Photo) {
            shader.setFloatUniform("uMotion", if (motion) 1f else 0f)
        }
    }

    private fun applyPalette() {
        val source = item.source as? WallpaperSource.Generative ?: return
        source.palette.colors.forEachIndexed { i, rgb ->
            shader.setFloatUniform(
                "uC$i",
                ((rgb shr 16) and 0xFF) / 255f,
                ((rgb shr 8) and 0xFF) / 255f,
                (rgb and 0xFF) / 255f
            )
        }
    }

    companion object {
        /** Effect index the shader treats as "fold effect off". */
        private const val NO_FOLD_INDEX = 99f

        private val NO_STARS: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF000000.toInt()) }

        /** Decodes a photo into a pyramid of [width] x [height]. Call off the main thread. */
        fun buildPyramid(resources: Resources, photo: WallpaperSource.Photo, width: Int, height: Int): BlurPyramid {
            if (photo.globe == null) {
                return BlurPyramid.fromResource(resources, photo.resId, width, height, photo.focusX, photo.focusY).also {
                    if (photo.starsResId != 0) it.attachAux(resources, photo.starsResId, photo.focusX, photo.focusY)
                }
            }
            // Globe maps keep their own size and aspect: the shader samples them by longitude and latitude.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, photo.resId, bounds)
            return BlurPyramid.fromResource(resources, photo.resId, bounds.outWidth, bounds.outHeight)
        }

        private fun sourceFor(item: WallpaperItem): String = when (val s = item.source) {
            is WallpaperSource.Photo -> FoldShaders.photoSource()
            is WallpaperSource.Generative -> FoldShaders.sceneSource(s.scene)
            is WallpaperSource.Video -> FoldShaders.videoSource()
        }

        /** Decodes a video's poster frame. Call off the main thread. */
        fun decodePoster(resources: Resources, video: WallpaperSource.Video): Bitmap? =
            BitmapFactory.decodeResource(resources, video.posterResId)

        fun create(item: WallpaperItem): WallpaperShader =
            WallpaperShader(item, RuntimeShader(sourceFor(item))).also { it.applyPalette() }
    }
}
