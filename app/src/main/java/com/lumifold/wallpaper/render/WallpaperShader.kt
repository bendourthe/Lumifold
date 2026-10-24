package com.lumifold.wallpaper.render

import android.content.res.Resources
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.lumifold.wallpaper.model.OpeningEffect
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

    val isReady: Boolean get() = item.source is WallpaperSource.Generative || pyramid != null

    fun attachPyramid(p: BlurPyramid) {
        pyramid = p
        val base = p.levels[0]
        p.levels.forEachIndexed { k, bmp ->
            val bs = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
                setLocalMatrix(Matrix().apply {
                    setScale(base.width.toFloat() / bmp.width, base.height.toFloat() / bmp.height)
                })
            }
            shader.setInputShader("L$k", bs)
        }
        shader.setFloatUniform("uTexSize", base.width.toFloat(), base.height.toFloat())
        val motion = (item.source as WallpaperSource.Photo).motion
        shader.setFloatUniform("uStyle", motion.shaderIndex.toFloat())
    }

    /** A second instance with its own uniforms, sharing the compiled effect and pyramid. */
    fun duplicate(): WallpaperShader {
        val copy = WallpaperShader(item, RuntimeShader(sourceFor(item)))
        copy.applyPalette()
        pyramid?.let { copy.attachPyramid(it) }
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

        /** Decodes a photo into a pyramid of [width] x [height]. Call off the main thread. */
        fun buildPyramid(resources: Resources, photo: WallpaperSource.Photo, width: Int, height: Int): BlurPyramid =
            BlurPyramid.fromResource(resources, photo.resId, width, height, photo.focusX, photo.focusY)

        private fun sourceFor(item: WallpaperItem): String = when (val s = item.source) {
            is WallpaperSource.Photo -> FoldShaders.photoSource()
            is WallpaperSource.Generative -> FoldShaders.sceneSource(s.scene)
        }

        fun create(item: WallpaperItem): WallpaperShader =
            WallpaperShader(item, RuntimeShader(sourceFor(item))).also { it.applyPalette() }
    }
}
