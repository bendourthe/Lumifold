package com.ambientfold.wallpaper.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight, zero-allocation per frame dynamic particle and ambient lighting engine.
 * Renders floating atmospheric motes, subtle horizon light drift, and interactive touch ripples.
 */
class DynamicParticleSystem(private val maxParticles: Int = 42) {

    private class Particle {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var radius = 2f
        var baseAlpha = 180
        var currentAlpha = 180
        var pulseSpeed = 0.03f
        var pulsePhase = 0f
        var color = Color.WHITE
        var isTouchBurst = false
        var life = 1.0f
    }

    private val particles = Array(maxParticles) { Particle() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var screenWidth = 1080
    private var screenHeight = 2400

    private var lightSweepPhase = 0f
    private var primaryColor: Int = Color.parseColor("#FFE082")

    fun init(width: Int, height: Int, particleColor: Int) {
        screenWidth = if (width > 0) width else 1080
        screenHeight = if (height > 0) height else 2400
        primaryColor = particleColor

        for (p in particles) {
            resetAmbientParticle(p)
        }
    }

    fun setPrimaryColor(color: Int) {
        primaryColor = color
        for (p in particles) {
            if (!p.isTouchBurst) {
                p.color = primaryColor
            }
        }
    }

    private fun resetAmbientParticle(p: Particle) {
        p.x = Random.nextFloat() * screenWidth
        p.y = Random.nextFloat() * screenHeight
        p.vx = (Random.nextFloat() - 0.5f) * 0.4f
        p.vy = -0.3f - Random.nextFloat() * 0.5f
        p.radius = 1.8f + Random.nextFloat() * 3.2f
        p.baseAlpha = 70 + Random.nextInt(120)
        p.currentAlpha = p.baseAlpha
        p.pulseSpeed = 0.02f + Random.nextFloat() * 0.04f
        p.pulsePhase = Random.nextFloat() * 6.28f
        p.color = primaryColor
        p.isTouchBurst = false
        p.life = 1.0f
    }

    fun addTouchBurst(touchX: Float, touchY: Float) {
        var converted = 0
        for (p in particles) {
            if (!p.isTouchBurst) {
                p.isTouchBurst = true
                p.x = touchX
                p.y = touchY
                val angle = Random.nextFloat() * 6.28f
                val speed = 2.0f + Random.nextFloat() * 4.0f
                p.vx = kotlin.math.cos(angle) * speed
                p.vy = kotlin.math.sin(angle) * speed
                p.radius = 3.5f + Random.nextFloat() * 3.5f
                p.baseAlpha = 240
                p.currentAlpha = 240
                p.life = 1.0f
                p.color = Color.WHITE
                converted++
                if (converted >= 8) break
            }
        }
    }

    fun updateAndDraw(canvas: Canvas, foldBlurProgress: Float) {
        lightSweepPhase += 0.015f
        val blurDimmer = 1.0f - (foldBlurProgress * 0.5f)

        // 1. Draw subtle ambient horizon light breathing wave
        val sweepY = (screenHeight * 0.55f) + (sin(lightSweepPhase) * 40f)
        val sweepAlpha = ((sin(lightSweepPhase * 0.7f) * 0.5f + 0.5f) * 22f * blurDimmer).toInt()
        if (sweepAlpha > 2) {
            val sweepGradient = RadialGradient(
                screenWidth * 0.5f,
                sweepY,
                screenWidth * 0.75f,
                intArrayOf(Color.argb(sweepAlpha, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
            lightPaint.shader = sweepGradient
            canvas.drawRect(0f, sweepY - 300f, screenWidth.toFloat(), sweepY + 300f, lightPaint)
        }

        // 2. Update and draw particles
        for (p in particles) {
            if (p.isTouchBurst) {
                p.x += p.vx
                p.y += p.vy
                p.vx *= 0.94f
                p.vy *= 0.94f
                p.life -= 0.025f
                p.currentAlpha = (p.baseAlpha * p.life * blurDimmer).toInt().coerceIn(0, 255)

                if (p.life <= 0f) {
                    resetAmbientParticle(p)
                    continue
                }
            } else {
                p.pulsePhase += p.pulseSpeed
                p.x += p.vx + sin(p.pulsePhase) * 0.25f
                p.y += p.vy
                val pulseFactor = (sin(p.pulsePhase) * 0.35f + 0.65f)
                p.currentAlpha = (p.baseAlpha * pulseFactor * blurDimmer).toInt().coerceIn(0, 255)

                if (p.y < -20f) p.y = screenHeight + 10f
                if (p.y > screenHeight + 20f) p.y = -10f
                if (p.x < -20f) p.x = screenWidth + 10f
                if (p.x > screenWidth + 20f) p.x = -10f
            }

            if (p.currentAlpha > 5) {
                paint.color = p.color
                paint.alpha = p.currentAlpha
                canvas.drawCircle(p.x, p.y, p.radius, paint)
            }
        }
    }
}
