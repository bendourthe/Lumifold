package com.ambientfold.wallpaper.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
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
 * High-performance ambient dynamic wallpaper rendering engine.
 * Delivers continuous, organic, distinctly visible 60fps motion:
 * 1. Landscapes: Visible cinematic breathing zoom, horizon drifting pan, sweeping atmospheric cloud mist,
 *    warm golden sunlight/god-ray beams, and wind/water caustics.
 * 2. Abstract Flow: Real-time GPU AGSL liquid glass flow with chromatic dispersion and specular highlights.
 * 3. Textures: Continuous rolling anisotropic light sheen across architectural materials.
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

    // AGSL RuntimeShader for API 33+
    private var agslLiquidShader: Any? = null
    private var lastBitmapShader: BitmapShader? = null
    private var lastCachedBmp: Bitmap? = null

    companion object {
        private const val AGSL_LIQUID_GLASS_SRC = """
            uniform float2 iResolution;
            uniform float iTime;
            uniform shader iImage;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution;
                float t = iTime * 0.75;
                
                // Multi-octave domain warping for continuous dynamic liquid glass flow
                float2 p = uv * 3.2;
                float wave1 = sin(p.y * 2.2 + t * 1.4) * 0.045 + cos(p.x * 2.5 - t * 0.9) * 0.035;
                float wave2 = cos(p.x * 2.0 + t * 1.2) * 0.045 + sin(p.y * 3.1 + t * 1.1) * 0.035;
                float2 offset = float2(wave1, wave2);
                
                // Chromatic dispersion: sample R, G, B channels with slight refraction offsets
                float2 coordR = (uv + offset * 1.06) * iResolution;
                float2 coordG = (uv + offset) * iResolution;
                float2 coordB = (uv + offset * 0.94) * iResolution;
                
                half r = iImage.eval(coordR).r;
                half g = iImage.eval(coordG).g;
                half b = iImage.eval(coordB).b;
                half a = iImage.eval(coordG).a;
                
                half4 col = half4(r, g, b, a);
                
                // Shimmering specular glass refraction highlights
                float specPattern = sin((uv.x + offset.x) * 5.0 + (uv.y + offset.y) * 4.0 + t * 1.6);
                float spec = pow(clamp(specPattern, 0.0, 1.0), 5.0) * 0.40;
                col.rgb += half3(spec * 1.15, spec * 1.0, spec * 1.3);
                
                return col;
            }
        """
    }

    init {
        initShaderIfSupported()
    }

    private fun initShaderIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                agslLiquidShader = RuntimeShader(AGSL_LIQUID_GLASS_SRC)
            } catch (e: Throwable) {
                agslLiquidShader = null
            }
        }
    }

    fun addTouchRipple(x: Float, y: Float) {
        touchX = x
        touchY = y
        touchTimeMs = SystemClock.uptimeMillis()
    }

    /**
     * Renders wallpaper to the canvas with Center-Crop and dynamic ambient motion.
     */
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

        // Dynamic Motion Mode based on category / motionType
        when (wallpaper.motionType) {
            MotionType.LIQUID_GLASS -> {
                renderLiquidGlass(canvas, bitmap, width, height, scaleBase, elapsedSec, blurProgress)
            }
            MotionType.TEXTURE_SHIMMER -> {
                renderTextureShimmer(canvas, bitmap, width, height, scaleBase, elapsedSec, blurProgress)
            }
            MotionType.LANDSCAPE_BREATHING -> {
                renderLandscapeBreathing(canvas, bitmap, width, height, scaleBase, elapsedSec, blurProgress)
            }
        }

        // Interactive touch ripple
        val touchElapsed = (nowMs - touchTimeMs) / 1000.0f
        if (touchElapsed in 0.0f..1.2f && touchX >= 0f && touchY >= 0f) {
            val rippleRadius = touchElapsed * (width.coerceAtLeast(height) * 0.65f)
            val alpha = ((1.0f - (touchElapsed / 1.2f)) * 90f).toInt().coerceIn(0, 255)
            val rippleGradient = RadialGradient(
                touchX, touchY, rippleRadius.coerceAtLeast(10f),
                intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0.7f, 0.88f, 1.0f),
                Shader.TileMode.CLAMP
            )
            lightPaint.shader = rippleGradient
            canvas.drawCircle(touchX, touchY, rippleRadius, lightPaint)
        }
    }

    private fun renderLandscapeBreathing(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        scaleBase: Float,
        timeSec: Float,
        blurProgress: Float
    ) {
        // Living cinematic zoom: 12-second gentle oscillation between 1.000 and 1.035
        val breathCycle = sin(timeSec * 0.52f)
        val breathScale = 1.0f + (breathCycle * 0.025f)
        val finalScale = scaleBase * breathScale

        // Micro-pan horizontal and vertical
        val panX = sin(timeSec * 0.38f) * 22.0f
        val panY = cos(timeSec * 0.45f) * 12.0f

        val scaledW = bitmap.width * finalScale
        val scaledH = bitmap.height * finalScale
        val dx = ((width - scaledW) * 0.5f) + panX
        val dy = ((height - scaledH) * 0.5f) + panY

        matrix.reset()
        matrix.postScale(finalScale, finalScale)
        matrix.postTranslate(dx, dy)
        canvas.drawBitmap(bitmap, matrix, basePaint)

        // 1. Drifting Atmospheric Cloud Mist across the sky (moving horizontally)
        val mistPhase1 = (timeSec * 0.07f) % 2.0f
        val mistX1 = (mistPhase1 - 0.5f) * width * 1.8f
        val mistGradient1 = LinearGradient(
            mistX1 - width * 0.6f, height * 0.15f, mistX1 + width * 0.6f, height * 0.35f,
            intArrayOf(Color.TRANSPARENT, Color.argb(45, 255, 255, 255), Color.argb(30, 240, 248, 255), Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.45f, 0.60f, 1.0f),
            Shader.TileMode.CLAMP
        )
        lightPaint.shader = mistGradient1
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.55f, lightPaint)

        // 2. Sweeping Sunlight Beam / God Rays across the horizon
        val sunPhase = sin(timeSec * 0.32f) * 0.5f + 0.5f
        val sunX = width * (0.15f + sunPhase * 0.70f)
        val sunY = height * 0.20f
        val sunRadius = width * 0.85f
        val sunAlpha = (48 + (sin(timeSec * 0.6f) * 18).toInt()).coerceIn(20, 75)

        val sunBeam = RadialGradient(
            sunX, sunY, sunRadius,
            intArrayOf(
                Color.argb(sunAlpha, 255, 245, 210),
                Color.argb((sunAlpha * 0.5f).toInt(), 255, 225, 175),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.55f, 1.0f),
            Shader.TileMode.CLAMP
        )
        lightPaint.shader = sunBeam
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.70f, lightPaint)

        // 3. Gentle Wind / Water Caustic Ripples over the lower landscape area
        val waterPhase = (timeSec * 0.25f) % 1.0f
        val waterY = height * (0.65f + waterPhase * 0.30f)
        val waterAlpha = (28 + (sin(timeSec * 1.2f) * 14).toInt()).coerceIn(12, 48)
        val waterShimmer = LinearGradient(
            0f, waterY - 35f, 0f, waterY + 35f,
            intArrayOf(Color.TRANSPARENT, Color.argb(waterAlpha, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
        lightPaint.shader = waterShimmer
        canvas.drawRect(0f, height * 0.60f, width.toFloat(), height.toFloat(), lightPaint)
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
                if (lastCachedBmp != bitmap || lastBitmapShader == null) {
                    lastCachedBmp = bitmap
                    lastBitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(matrix)
                    }
                } else {
                    lastBitmapShader?.setLocalMatrix(matrix)
                }

                shader.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
                shader.setFloatUniform("iTime", timeSec)
                shader.setInputShader("iImage", lastBitmapShader!!)

                motionPaint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), motionPaint)
                return
            } catch (e: Throwable) {
                // Fallback to CPU multi-pass
            }
        }

        // High-fidelity fallback for API 31-32 or preview canvas
        canvas.drawBitmap(bitmap, matrix, basePaint)

        // Dual undulating fluid glass distortion layers
        val wave1X = sin(timeSec * 0.9f) * 26f
        val wave1Y = cos(timeSec * 0.75f) * 20f
        val wave1Scale = 1.0f + (sin(timeSec * 1.1f) * 0.035f)

        val wave1Matrix = Matrix(matrix)
        wave1Matrix.postScale(wave1Scale, wave1Scale, width * 0.5f, height * 0.5f)
        wave1Matrix.postTranslate(wave1X, wave1Y)

        motionPaint.shader = null
        motionPaint.alpha = 75
        canvas.drawBitmap(bitmap, wave1Matrix, motionPaint)

        // Rolling chromatic specular glass highlight band
        val specCenter = ((timeSec * 0.28f) % 1.8f - 0.4f) * (width + height)
        val specGradient = LinearGradient(
            specCenter - 180f, specCenter - 180f, specCenter + 180f, specCenter + 180f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(35, 99, 102, 241),
                Color.argb(85, 255, 255, 255),
                Color.argb(35, 236, 72, 153),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.35f, 0.50f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
        lightPaint.shader = specGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), lightPaint)
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

        // Continuous anisotropic specular sheen sweeping diagonally across the material
        val cycle = (timeSec * 0.22f) % 2.0f
        val pos = (cycle - 0.5f) * (width + height)

        val sheenGradient = LinearGradient(
            pos - 220f, 0f, pos + 220f, height.toFloat(),
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(40, 255, 255, 255),
                Color.argb(80, 240, 245, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.45f, 0.55f, 1.0f),
            Shader.TileMode.CLAMP
        )
        lightPaint.shader = sheenGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), lightPaint)
    }
}
