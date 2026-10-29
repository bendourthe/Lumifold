package com.lumifold.wallpaper.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Renders shaders once into ordinary bitmaps. Thumbnails drawn live would re-run their shaders on
 * every frame the window repaints (for example while the 3D preview animates).
 */
object ShaderSnapshots {

    /** Single background thread: compiles shaders and renders snapshots off the main thread. */
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(key: String): Bitmap? = cache.get(key)

    /** Returns the cached snapshot for [key] or renders it with [shader]. Call on [dispatcher]. */
    fun getOrRender(key: String, width: Int, height: Int, shader: () -> Shader): Bitmap {
        cache.get(key)?.let { return it }
        val s = shader()
        val bitmap = runCatching { renderOnGpu(width, height, s) }.getOrElse { renderInSoftware(width, height, s) }
        cache.put(key, bitmap)
        return bitmap
    }

    private fun renderOnGpu(width: Int, height: Int, shader: Shader): Bitmap {
        val reader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        val node = RenderNode("snapshot").apply { setPosition(0, 0, width, height) }
        val canvas = node.beginRecording()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
        node.endRecording()
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            reader.acquireNextImage().use { image ->
                val buffer = requireNotNull(image.hardwareBuffer)
                buffer.use { hb ->
                    val wrapped = requireNotNull(Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB)))
                    return wrapped.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        } finally {
            renderer.destroy()
            node.discardDisplayList()
            reader.close()
        }
    }

    private fun renderInSoftware(width: Int, height: Int, shader: Shader): Bitmap {
        val bitmap = createBitmap(width, height)
        Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
        return bitmap
    }
}
