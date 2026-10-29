package com.lumifold.wallpaper.render

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Light paths around a Schwarzschild black hole (units of the Schwarzschild radius), tabulated
 * for the black hole scene. A ray leaving the camera with impact parameter b moves in a plane;
 * its inverse radius u = 1/r obeys u'' = -u + 1.5 u^2 against the swept angle phi, starting at
 * u = 0, u' = 1/b on the camera side.
 *
 * [orbit]: x = phi in [0, [PHI_MAX]], y = b in [0, [B_MAX]], value u (0 once escaped, 1 once
 * captured). [escape]: x = b, value the angle at which the ray escapes, or 0 if it is captured.
 * Values are 16-bit, split over red (high byte) and green (low byte), which filters linearly.
 */
object BlackHoleLens {
    const val N_PHI = 768
    const val N_B = 512
    const val B_MAX = 16f
    const val PHI_MAX = (3.0 * PI).toFloat()

    val orbit: Bitmap by lazy { build().first }
    val escape: Bitmap by lazy { build().second }

    private var built: Pair<Bitmap, Bitmap>? = null

    @Synchronized
    private fun build(): Pair<Bitmap, Bitmap> {
        built?.let { return it }
        val orbitPx = IntArray(N_PHI * N_B)
        val escapePx = IntArray(N_B)
        val h = PHI_MAX / (N_PHI - 1)
        for (j in 0 until N_B) {
            val b = j * B_MAX / (N_B - 1)
            var u = 0.0
            var du = if (b > 1e-4f) 1.0 / b else 1e4
            var end = 0.0
            var state = 0 // 0 travelling, 1 escaped, 2 captured
            for (i in 0 until N_PHI) {
                val value = when (state) {
                    1 -> 0.0
                    2 -> 1.0
                    else -> u.coerceIn(0.0, 1.0)
                }
                orbitPx[j * N_PHI + i] = encode(value)
                if (state != 0) continue
                // RK4 step of (u, u') -> (u', -u + 1.5 u^2).
                val k1u = du; val k1v = -u + 1.5 * u * u
                val u2 = u + 0.5 * h * k1u; val v2 = du + 0.5 * h * k1v
                val k2u = v2; val k2v = -u2 + 1.5 * u2 * u2
                val u3 = u + 0.5 * h * k2u; val v3 = du + 0.5 * h * k2v
                val k3u = v3; val k3v = -u3 + 1.5 * u3 * u3
                val u4 = u + h * k3u; val v4 = du + h * k3v
                val k4u = v4; val k4v = -u4 + 1.5 * u4 * u4
                val nu = u + h / 6.0 * (k1u + 2 * k2u + 2 * k3u + k4u)
                val nv = du + h / 6.0 * (k1v + 2 * k2v + 2 * k3v + k4v)
                if (nu >= 1.0) {
                    state = 2
                } else if (nu < 0.0 && i > 0) {
                    state = 1
                    end = i * h + h * (u / (u - nu))
                }
                u = nu
                du = nv
            }
            escapePx[j] = encode(if (state == 1) end / PHI_MAX else 0.0)
        }
        val result = Bitmap.createBitmap(orbitPx, N_PHI, N_B, Bitmap.Config.ARGB_8888) to
            Bitmap.createBitmap(escapePx, N_B, 1, Bitmap.Config.ARGB_8888)
        built = result
        return result
    }

    private fun encode(v: Double): Int {
        val q = (v.coerceIn(0.0, 1.0) * 65535.0).roundToInt()
        return (0xFF shl 24) or ((q shr 8) shl 16) or ((q and 0xFF) shl 8)
    }
}
