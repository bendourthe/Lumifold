package com.ambientfold.wallpaper.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import com.ambientfold.wallpaper.model.MotionType
import com.ambientfold.wallpaper.model.WallpaperItem
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Natural, slow, embedded dynamic wallpaper motion engine.
 * Eliminates artificial lens flares and overlay glares.
 * Directly animates the image pixels with organic physical dynamics:
 * 1. Landscapes: Slow embedded cloud drift in the sky, gentle wind swaying in foliage,
 *    and subtle water refraction ripples.
 * 2. Abstract Flow: Smooth, slow-motion harmonic liquid glass flow with chromatic dispersion.
 * 3. Textures: Gentle rolling anisotropic light sheen across natural materials.
 */
class DynamicMotionEngine {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val motionPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()

    private var startTimeMs = SystemClock.uptimeMillis()
    private var touchX = -1f
    private var touchY = -1f
    private var touchTimeMs = 0L

    // AGSL RuntimeShaders for API 33+
    private var agslLandscapeShader: Any? = null
    private var agslLiquidShader: Any? = null
    private var lastLandscapeBitmapShader: BitmapShader? = null
    private var lastLandscapeCachedBmp: Bitmap? = null
    private var lastLiquidBitmapShader: BitmapShader? = null
    private var lastLiquidCachedBmp: Bitmap? = null

    companion object {
        // Natural landscape motion embedded into image pixels (no glares or overlays)
        private const val AGSL_LANDSCAPE_SRC = """
            uniform float2 iResolution;
            uniform float iTime;
            uniform shader iImage;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution;
                
                // Slow, serene time scale (25 to 40-second cycles)
                float tSky = iTime * 0.12;
                float tWind = iTime * 0.28;
                
                // 1. Natural Cloud Drift (embedded in upper sky, fading towards horizon)
                float skyMask = smoothstep(0.62, 0.15, uv.y);
                float cloudDriftX = sin(uv.x * 2.2 + tSky * 0.8) * 0.006 + cos(uv.y * 3.0 + tSky * 0.6) * 0.004;
                float cloudDriftY = cos(uv.x * 2.8 - tSky * 0.5) * 0.0025;
                float2 skyOffset = float2(cloudDriftX, cloudDriftY) * skyMask;
                
                // 2. Natural Wind & Foliage / Water Ripple (embedded in lower ground/lake)
                float groundMask = smoothstep(0.42, 0.95, uv.y);
                float windSwayX = sin(uv.y * 16.0 + tWind * 1.0) * 0.0035 * groundMask;
                float waterRippleY = cos(uv.x * 26.0 + tWind * 1.1) * 0.0025 * groundMask;
                float2 groundOffset = float2(windSwayX, waterRippleY);
                
                // Directly sample the high-resolution image with natural physical displacement
                float2 warpedCoord = (uv + skyOffset + groundOffset) * iResolution;
                return iImage.eval(warpedCoord);
            }
        """

        // Smooth harmonic liquid glass flow
        private const val AGSL_LIQUID_GLASS_SRC = """
            uniform float2 iResolution;
            uniform float iTime;
            uniform shader iImage;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution;
                float t = iTime * 0.24; // Slow, calm fluid velocity
                
                // Harmonic domain warping for smooth fluid motion
                float2 p = uv * 2.8;
                float wave1 = sin(p.y * 1.8 + t * 0.9) * 0.035 + cos(p.x * 2.1 - t * 0.6) * 0.025;
                float wave2 = cos(p.x * 1.6 + t * 0.8) * 0.035 + sin(p.y * 2.4 + t * 0.7) * 0.025;
                float2 offset = float2(wave1, wave2);
                
                // Subtle chromatic dispersion
                float2 coordR = (uv + offset * 1.03) * iResolution;
                float2 coordG = (uv + offset) * iResolution;
                float2 coordB = (uv + offset * 0.97) * iResolution;
                
                half r = iImage.eval(coordR).r;
                half g = iImage.eval(coordG).g;
                half b = iImage.eval(coordB).b;
                half a = iImage.eval(coordG).a;
                
                return half4(r, g, b, a);
            }
        """
    }

    init {
        initShadersIfSupported()
    }

    private fun initShadersIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                agslLandscapeShader = RuntimeShader(AGSL_LANDSCAPE_SRC)
                agslLiquidShader = RuntimeShader(AGSL_LIQUID_GLASS_SRC)
            } catch (e: Throwable) {
                agslLandscapeShader = null
                agslLiquidShader = null
            }
        }
    }

    fun addTouchRipple(x: Float, y: Float) {
        touchX = x
        touchY = y
        touchTimeMs = SystemClock.uptimeMillis()
    }

    fun render(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        wallpaper: WallpaperItem,
        isDynamic: Boolean,
        blurProgress: Float = 0.0f
    ) {
        if (width <= 0 || height <= 0 || bitmap.isRecycled) return

        val nowMs = SystemClock.uptimeMillis()
        val elapsedSec = (nowMs - startTimeMs) / 1000.0f

        // Center-Crop scaling
        val scaleBase = max(width.toFloat() / bitmap.width.toFloat(), height.toFloat() / bitmap.height.toFloat())

        if (!isDynamic) {
            // Static mode: Clean Center-Crop
            val scaledW = bitmap.width * scaleBase
            val scaledH = bitmap.height * scaleBase
            val dx = (width - scaledW) * 0.5f
            val dy = (height - scaledH) * 0.5f

            matrix.reset()
            matrix.postScale(scaleBase, scaleBase)
            matrix.postTranslate(dx, dy)
            canvas.drawBitmap(bitmap, matrix, basePaint)
            return
        }

        // Dynamic Motion Mode
        when (wallpaper.motionType) {
            MotionType.LIQUID_GLASS -> {
                renderLiquidGlass(canvas, bitmap, width, height, scaleBase, elapsedSec, blurProgress)
            }
            MotionType.TEXTURE_SHIMMER -> {
                renderTextureShimmer(canvas, bitmap, width, height, scaleBase, elapsedSec, blurProgress)
            }
            MotionType.LANDSCAPE_BREATHING -> {
                renderLandscapeNatural(canvas, bitmap, width, height, scaleBase, elapsedSec, blurProgress)
            }
        }

        // Interactive touch ripple
        val touchElapsed = (nowMs - touchTimeMs) / 1000.0f
        if (touchElapsed in 0.0f..1.0f && touchX >= 0f && touchY >= 0f) {
            val rippleRadius = touchElapsed * (width.coerceAtLeast(height) * 0.5f)
            val alpha = ((1.0f - touchElapsed) * 60f).toInt().coerceIn(0, 255)
            val rippleGradient = RadialGradient(
                touchX, touchY, rippleRadius.coerceAtLeast(10f),
                intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0.7f, 0.90f, 1.0f),
                Shader.TileMode.CLAMP
            )
            lightPaint.shader = rippleGradient
            canvas.drawCircle(touchX, touchY, rippleRadius, lightPaint)
        }
    }

    /**
     * Renders natural landscape motion:
     * - Embedded cloud drift across the sky.
     * - Subtle wind swaying in foliage and gentle water ripple.
     * - Zero artificial lens flares or white glares.
     */
    private fun renderLandscapeNatural(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        scaleBase: Float,
        timeSec: Float,
        blurProgress: Float
    ) {
        val scaledW = bitmap.width * scaleBase
        val scaledH = bitmap.height * scaleBase
        val dx = (width - scaledW) * 0.5f
        val dy = (height - scaledH) * 0.5f

        matrix.reset()
        matrix.postScale(scaleBase, scaleBase)
        matrix.postTranslate(dx, dy)

        // Hardware AGSL: Natural pixel-embedded cloud drift and wind sway
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslLandscapeShader != null) {
            try {
                val shader = agslLandscapeShader as RuntimeShader
                if (lastLandscapeCachedBmp != bitmap || lastLandscapeBitmapShader == null) {
                    lastLandscapeCachedBmp = bitmap
                    lastLandscapeBitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(matrix)
                    }
                } else {
                    lastLandscapeBitmapShader?.setLocalMatrix(matrix)
                }

                shader.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
                shader.setFloatUniform("iTime", timeSec)
                shader.setInputShader("iImage", lastLandscapeBitmapShader!!)

                motionPaint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), motionPaint)
                return
            } catch (e: Throwable) {
                // Fallback
            }
        }

        // Clean CPU Fallback: Gentle, slow cinematic drift with zero artificial glares
        val breathCycle = sin(timeSec * 0.20f) // 31-second gentle breath
        val breathScale = 1.0f + (breathCycle * 0.012f)
        val finalScale = scaleBase * breathScale

        val panX = sin(timeSec * 0.16f) * 8.0f
        val panY = cos(timeSec * 0.19f) * 5.0f

        val fbW = bitmap.width * finalScale
        val fbH = bitmap.height * finalScale
        val fdx = ((width - fbW) * 0.5f) + panX
        val fdy = ((height - fbH) * 0.5f) + panY

        matrix.reset()
        matrix.postScale(finalScale, finalScale)
        matrix.postTranslate(fdx, fdy)
        canvas.drawBitmap(bitmap, matrix, basePaint)
    }

    private fun renderLiquidGlass(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        scaleBase: Float,
        timeSec: Float,
        blurProgress: Float
    ) {
        val scaledW = bitmap.width * scaleBase
        val scaledH = bitmap.height * scaleBase
        val dx = (width - scaledW) * 0.5f
        val dy = (height - scaledH) * 0.5f

        matrix.reset()
        matrix.postScale(scaleBase, scaleBase)
        matrix.postTranslate(dx, dy)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslLiquidShader != null) {
            try {
                val shader = agslLiquidShader as RuntimeShader
                if (lastLiquidCachedBmp != bitmap || lastLiquidBitmapShader == null) {
                    lastLiquidCachedBmp = bitmap
                    lastLiquidBitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(matrix)
                    }
                } else {
                    lastLiquidBitmapShader?.setLocalMatrix(matrix)
                }

                shader.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
                shader.setFloatUniform("iTime", timeSec)
                shader.setInputShader("iImage", lastLiquidBitmapShader!!)

                motionPaint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), motionPaint)
                return
            } catch (e: Throwable) {
                // Fallback
            }
        }

        // Clean CPU fallback for liquid glass
        val waveScale = 1.0f + (sin(timeSec * 0.35f) * 0.015f)
        val waveX = sin(timeSec * 0.30f) * 10f
        val waveY = cos(timeSec * 0.25f) * 8f

        val waveMatrix = Matrix(matrix)
        waveMatrix.postScale(waveScale, waveScale, width * 0.5f, height * 0.5f)
        waveMatrix.postTranslate(waveX, waveY)

        canvas.drawBitmap(bitmap, waveMatrix, basePaint)
    }

    private fun renderTextureShimmer(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        scaleBase: Float,
        timeSec: Float,
        blurProgress: Float
    ) {
        val scaledW = bitmap.width * scaleBase
        val scaledH = bitmap.height * scaleBase
        val dx = (width - scaledW) * 0.5f
        val dy = (height - scaledH) * 0.5f

        matrix.reset()
        matrix.postScale(scaleBase, scaleBase)
        matrix.postTranslate(dx, dy)
        canvas.drawBitmap(bitmap, matrix, basePaint)

        // Very slow, subtle anisotropic light depth (period ~30 seconds)
        val cycle = (timeSec * 0.10f) % 2.0f
        val pos = (cycle - 0.5f) * (width + height)

        val sheen = android.graphics.LinearGradient(
            pos - 240f, 0f, pos + 240f, height.toFloat(),
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(20, 255, 255, 255),
                Color.argb(35, 240, 245, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.45f, 0.55f, 1.0f),
            Shader.TileMode.CLAMP
        )
        lightPaint.shader = sheen
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), lightPaint)
    }
}
