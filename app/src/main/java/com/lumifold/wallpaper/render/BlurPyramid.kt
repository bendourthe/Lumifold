package com.lumifold.wallpaper.render

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Gaussian pyramid of the shared image plane. Level k is half the size of level k-1 and carries a
 * blur of roughly 2^k level-0 pixels, so the shader can pick any sigma by blending two levels.
 */
class BlurPyramid private constructor(
    val levels: List<Bitmap>,
    /** Motion map cropped exactly like level 0, or null. */
    val motionMap: Bitmap?
) {

    val width: Int get() = levels[0].width
    val height: Int get() = levels[0].height

    fun recycle() {
        levels.forEach { if (!it.isRecycled) it.recycle() }
        motionMap?.let { if (!it.isRecycled) it.recycle() }
    }

    companion object {

        /**
         * Decodes [resId] center-cropped around ([focusX], [focusY]) to [targetWidth] x
         * [targetHeight], then builds [FoldShaders.PYRAMID_LEVELS] levels. Call off the main thread.
         */
        fun fromResource(
            resources: Resources,
            resId: Int,
            targetWidth: Int,
            targetHeight: Int,
            focusX: Float = 0.5f,
            focusY: Float = 0.5f,
            motionMapResId: Int = 0
        ): BlurPyramid {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, resId, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth &&
                bounds.outHeight / (sample * 2) >= targetHeight
            ) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeResource(
                resources, resId,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: error("Cannot decode wallpaper resource $resId")

            val base = cropTo(decoded, targetWidth, targetHeight, focusX, focusY)
            if (base !== decoded) decoded.recycle()

            val map = if (motionMapResId == 0) null else {
                val raw = BitmapFactory.decodeResource(
                    resources, motionMapResId,
                    BitmapFactory.Options().apply { inScaled = false }
                )
                raw?.let {
                    val cropped = cropTo(it, max(1, targetWidth / 8), max(1, targetHeight / 8), focusX, focusY)
                    it.recycle()
                    cropped
                }
            }
            return fromBitmap(base, map)
        }

        fun fromBitmap(base: Bitmap, motionMap: Bitmap? = null): BlurPyramid {
            val levels = ArrayList<Bitmap>(FoldShaders.PYRAMID_LEVELS)
            levels += base
            var prev = base
            for (k in 1 until FoldShaders.PYRAMID_LEVELS) {
                val w = max(1, (prev.width / 2f).roundToInt())
                val h = max(1, (prev.height / 2f).roundToInt())
                val half = Bitmap.createScaledBitmap(prev, w, h, true)
                levels += binomialBlur(half, passes = 2)
                if (half !== levels.last()) half.recycle()
                prev = levels.last()
            }
            return BlurPyramid(levels, motionMap)
        }

        private fun cropTo(src: Bitmap, w: Int, h: Int, fx: Float, fy: Float): Bitmap {
            val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
            val cropW = w / scale
            val cropH = h / scale
            val left = ((src.width - cropW) * fx).coerceIn(0f, src.width - cropW)
            val top = ((src.height - cropH) * fy).coerceIn(0f, src.height - cropH)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(
                src,
                Rect(left.toInt(), top.toInt(), (left + cropW).toInt(), (top + cropH).toInt()),
                RectF(0f, 0f, w.toFloat(), h.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            )
            return out
        }

        /** Separable [1 4 6 4 1] / 16 kernel with clamped edges, applied [passes] times. */
        private fun binomialBlur(src: Bitmap, passes: Int): Bitmap {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            val r = FloatArray(w * h)
            val g = FloatArray(w * h)
            val b = FloatArray(w * h)
            for (i in px.indices) {
                val c = px[i]
                r[i] = ((c shr 16) and 0xFF).toFloat()
                g[i] = ((c shr 8) and 0xFF).toFloat()
                b[i] = (c and 0xFF).toFloat()
            }
            val tmp = FloatArray(w * h)
            repeat(passes) {
                for (ch in arrayOf(r, g, b)) {
                    blurAxis(ch, tmp, w, h, horizontal = true)
                    blurAxis(tmp, ch, w, h, horizontal = false)
                }
            }
            for (i in px.indices) {
                px[i] = (0xFF shl 24) or
                    (r[i].roundToInt().coerceIn(0, 255) shl 16) or
                    (g[i].roundToInt().coerceIn(0, 255) shl 8) or
                    b[i].roundToInt().coerceIn(0, 255)
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }

        private fun blurAxis(src: FloatArray, dst: FloatArray, w: Int, h: Int, horizontal: Boolean) {
            val n = if (horizontal) w else h
            val lines = if (horizontal) h else w
            val step = if (horizontal) 1 else w
            for (line in 0 until lines) {
                val start = if (horizontal) line * w else line
                for (i in 0 until n) {
                    val im2 = (i - 2).coerceIn(0, n - 1)
                    val im1 = (i - 1).coerceIn(0, n - 1)
                    val ip1 = (i + 1).coerceIn(0, n - 1)
                    val ip2 = (i + 2).coerceIn(0, n - 1)
                    dst[start + i * step] = (
                        src[start + im2 * step] + 4f * src[start + im1 * step] +
                            6f * src[start + i * step] +
                            4f * src[start + ip1 * step] + src[start + ip2 * step]
                        ) * 0.0625f
                }
            }
        }
    }
}
