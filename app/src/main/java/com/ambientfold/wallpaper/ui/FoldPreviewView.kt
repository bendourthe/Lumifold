package com.ambientfold.wallpaper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.ambientfold.wallpaper.model.OpeningEffect
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperSource
import com.ambientfold.wallpaper.render.BlurPyramid
import com.ambientfold.wallpaper.render.SurfaceSpec
import com.ambientfold.wallpaper.render.WallpaperShader
import com.ambientfold.wallpaper.service.FoldWallpaperService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D model of a book-style foldable (Pixel Fold proportions). The back leaf stays still; the
 * front leaf, with the cover display on its outside, swings around the left hinge like a book
 * cover. Every visible display face is drawn with the same fold shader as the live wallpaper.
 */
class FoldPreviewView(context: Context) : View(context) {

    var onAngleChanged: ((Float) -> Unit)? = null
    var onAutoPlayChanged: ((Boolean) -> Unit)? = null

    /** Hinge angle at which the simulated system switches from the cover to the inner display. */
    var handoffAngle = 90f
        set(value) { field = value; invalidate() }

    var angle = 0f
        private set

    var isAutoPlaying = true
        private set

    private var item: WallpaperItem? = null
    private var effect = OpeningEffect.DEFAULT
    private var intensity = 1f
    private var motion = true

    private var coverShader: WallpaperShader? = null
    private var innerShader: WallpaperShader? = null
    private var loadingId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val startMs = SystemClock.uptimeMillis()
    private var playStartMs = SystemClock.uptimeMillis()

    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val path = Path()
    private val rect = RectF()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)

    private var dragStartX = 0f
    private var dragStartAngle = 0f
    private var eyeWorld = Vec(0.5f, 0f, 6f)

    fun setWallpaper(next: WallpaperItem) {
        if (item?.id == next.id) return
        item = next
        val cover = WallpaperShader.create(next)
        val photo = next.source as? WallpaperSource.Photo
        if (photo == null) {
            install(cover)
            return
        }
        loadingId = next.id
        loader.execute {
            val pyramid = runCatching {
                BlurPyramid.fromResource(
                    resources, photo.resId, TEX_W, (TEX_W * PLANE_H / 2f).toInt(),
                    photo.focusX, photo.focusY
                )
            }.getOrNull()
            mainHandler.post {
                if (loadingId != next.id || pyramid == null) {
                    pyramid?.recycle()
                    return@post
                }
                loadingId = null
                cover.attachPyramid(pyramid)
                install(cover)
            }
        }
    }

    private fun install(cover: WallpaperShader) {
        val oldPyramid = coverShader?.pyramid
        coverShader = cover
        innerShader = cover.duplicate()
        if (oldPyramid != null && oldPyramid !== cover.pyramid) oldPyramid.recycle()
        invalidate()
    }

    fun setEffect(effect: OpeningEffect, intensity: Float, motion: Boolean) {
        this.effect = effect
        this.intensity = intensity
        this.motion = motion
        invalidate()
    }

    fun setAngleManually(degrees: Float) {
        setAutoPlay(false)
        updateAngle(degrees)
    }

    fun setAutoPlay(enabled: Boolean) {
        if (enabled == isAutoPlaying) return
        isAutoPlaying = enabled
        if (enabled) playStartMs = SystemClock.uptimeMillis() - timelineOffsetFor(angle)
        onAutoPlayChanged?.invoke(enabled)
        invalidate()
    }

    private fun updateAngle(degrees: Float) {
        val clamped = degrees.coerceIn(0f, 180f)
        if (clamped != angle) {
            angle = clamped
            onAngleChanged?.invoke(clamped)
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                setAutoPlay(false)
                dragStartX = event.x
                dragStartAngle = angle
            }
            MotionEvent.ACTION_MOVE -> {
                updateAngle(dragStartAngle + (dragStartX - event.x) / width * 260f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isAutoPlaying) updateAngle(timelineAngle(SystemClock.uptimeMillis() - playStartMs))

        val a = Math.toRadians(angle.toDouble()).toFloat()
        val open = smooth(angle / 180f)
        val cam = Camera3(width.toFloat(), height.toFloat(), open)
        eyeWorld = cam.eye

        drawShadow(canvas, cam, a)
        drawBackLeaf(canvas, cam)
        drawMovingLeaf(canvas, cam, a)

        val live = item?.isLive == true && motion
        if (isAutoPlaying || live) postInvalidateOnAnimation()
    }

    // region Geometry

    private class Vec(val x: Float, val y: Float, val z: Float) {
        operator fun plus(o: Vec) = Vec(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: Vec) = Vec(x - o.x, y - o.y, z - o.z)
        operator fun times(s: Float) = Vec(x * s, y * s, z * s)
        fun dot(o: Vec) = x * o.x + y * o.y + z * o.z
        fun cross(o: Vec) = Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
        fun norm(): Vec { val l = sqrt(dot(this)); return Vec(x / l, y / l, z / l) }
    }

    /** Pinhole camera in front of and slightly above the phone, tracking toward the hinge. */
    private inner class Camera3(val w: Float, val h: Float, open: Float) {
        val target = Vec(lerp(0.5f, 0f, open), 0f, 0f)
        val eye = target + Vec(0.15f, 2.1f, 6.0f)
        private val f = (target - eye).norm()
        private val r = f.cross(Vec(0f, 1f, 0f)).norm()
        private val u = r.cross(f)
        private val focal: Float
        private val cx = w * 0.5f
        private val cy = h * 0.47f

        init {
            val dist = sqrt((eye - target).dot(eye - target))
            val byHeight = h * 0.80f * dist / BODY_H
            val byWidth = w * 0.88f * dist / lerp(1.15f, 2.15f, open)
            focal = min(byHeight, byWidth)
        }

        fun project(p: Vec, out: FloatArray, i: Int) {
            val d = p - eye
            val zc = d.dot(f)
            out[i] = cx + focal * d.dot(r) / zc
            out[i + 1] = cy - focal * d.dot(u) / zc
        }

        fun facing(normal: Vec, point: Vec) = normal.dot(eye - point) > 0f
    }

    private fun movingCenter(u: Float, y: Float, a: Float) =
        Vec(u * cos(a), y, THICK * 0.5f * cos(a) + u * sin(a))

    private fun outerNormal(a: Float) = Vec(-sin(a), 0f, cos(a))

    private fun outerFace(u: Float, y: Float, a: Float) = movingCenter(u, y, a) + outerNormal(a) * (THICK * 0.5f)

    private fun innerFace(u: Float, y: Float, a: Float) = movingCenter(u, y, a) - outerNormal(a) * (THICK * 0.5f)

    /** Maps the local rectangle (0,0)-(w,h) onto four projected world corners (TL, TR, BR, BL). */
    private fun mapRect(cam: Camera3, w: Float, h: Float, tl: Vec, tr: Vec, br: Vec, bl: Vec): Matrix {
        src[0] = 0f; src[1] = 0f; src[2] = w; src[3] = 0f
        src[4] = w; src[5] = h; src[6] = 0f; src[7] = h
        cam.project(tl, dst, 0); cam.project(tr, dst, 2)
        cam.project(br, dst, 4); cam.project(bl, dst, 6)
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        return matrix
    }

    private fun fillQuad(canvas: Canvas, cam: Camera3, paint: Paint, vararg pts: Vec) {
        path.reset()
        pts.forEachIndexed { i, p ->
            cam.project(p, dst, 0)
            if (i == 0) path.moveTo(dst[0], dst[1]) else path.lineTo(dst[0], dst[1])
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun shade(normal: Vec, base: Int): Int {
        val light = Vec(-0.35f, 0.75f, 0.55f).norm()
        val k = 0.55f + 0.45f * max(0f, normal.dot(light))
        return Color.rgb(
            (Color.red(base) * k).toInt().coerceIn(0, 255),
            (Color.green(base) * k).toInt().coerceIn(0, 255),
            (Color.blue(base) * k).toInt().coerceIn(0, 255)
        )
    }

    // endregion

    // region Drawing

    private fun drawShadow(canvas: Canvas, cam: Camera3, a: Float) {
        val left = min(0f, cos(a))
        cam.project(Vec((left + 1f) * 0.5f, -BODY_H * 0.5f - 0.05f, 0f), dst, 0)
        cam.project(Vec(1f, -BODY_H * 0.5f - 0.05f, 0f), dst, 2)
        cam.project(Vec(left, -BODY_H * 0.5f - 0.05f, 0f), dst, 4)
        val rx = max(40f, (dst[2] - dst[4]) * 0.62f)
        val ry = rx * 0.12f
        shadowPaint.shader = RadialGradient(
            dst[0], dst[1], rx, intArrayOf(Color.argb(150, 0, 0, 0), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.scale(1f, ry / rx, dst[0], dst[1])
        canvas.drawCircle(dst[0], dst[1], rx, shadowPaint)
        canvas.restore()
    }

    private fun drawBackLeaf(canvas: Canvas, cam: Camera3) {
        val top = BODY_H * 0.5f
        val bot = -top
        edgePaint.color = shade(Vec(0f, 1f, 0f), EDGE_COLOR)
        fillQuad(canvas, cam, edgePaint, Vec(0f, top, 0f), Vec(1f, top, 0f), Vec(1f, top, -THICK), Vec(0f, top, -THICK))
        if (cam.facing(Vec(1f, 0f, 0f), Vec(1f, 0f, 0f))) {
            edgePaint.color = shade(Vec(1f, 0f, 0f), EDGE_COLOR)
            fillQuad(canvas, cam, edgePaint, Vec(1f, top, 0f), Vec(1f, bot, 0f), Vec(1f, bot, -THICK), Vec(1f, top, -THICK))
        }

        // Inner display, right half, on the back leaf (plane z = 0).
        val hw = 1f - BEZEL
        val m = mapRect(
            cam, INNER_W, INNER_H,
            Vec(-hw, INNER_WH * 0.5f, 0f), Vec(hw, INNER_WH * 0.5f, 0f),
            Vec(hw, -INNER_WH * 0.5f, 0f), Vec(-hw, -INNER_WH * 0.5f, 0f)
        )
        canvas.save()
        canvas.concat(m)
        val k = INNER_W / (2f * hw)
        rect.set(INNER_W * 0.5f, (INNER_WH * 0.5f - top) * k, INNER_W * 0.5f + k, (INNER_WH * 0.5f + top) * k)
        bodyPaint.color = shade(Vec(0f, 0f, 1f), BODY_COLOR)
        drawRounded(canvas, rect, 0f, CORNER * k, bodyPaint)
        rect.set(INNER_W * 0.5f, 0f, INNER_W, INNER_H)
        drawScreen(canvas, rect, 0f, SCREEN_CORNER * k, innerOn(), innerShader, INNER_SPEC)
        canvas.restore()
    }

    private fun drawMovingLeaf(canvas: Canvas, cam: Camera3, a: Float) {
        val top = BODY_H * 0.5f
        val bot = -top

        edgePaint.color = shade(Vec(0f, 1f, 0f), EDGE_COLOR)
        fillQuad(canvas, cam, edgePaint, outerFace(0f, top, a), outerFace(1f, top, a), innerFace(1f, top, a), innerFace(0f, top, a))
        val edgeNormal = Vec(cos(a), 0f, sin(a))
        if (cam.facing(edgeNormal, movingCenter(1f, 0f, a))) {
            edgePaint.color = shade(edgeNormal, EDGE_COLOR)
            fillQuad(canvas, cam, edgePaint, outerFace(1f, top, a), outerFace(1f, bot, a), innerFace(1f, bot, a), innerFace(1f, top, a))
        }

        val n = outerNormal(a)
        if (cam.facing(n, outerFace(0.5f, 0f, a))) {
            // Cover display: hinge on its left edge when seen from outside.
            val m = mapRect(
                cam, COVER_W, COVER_H,
                outerFace(BEZEL, COVER_WH * 0.5f, a), outerFace(1f - BEZEL, COVER_WH * 0.5f, a),
                outerFace(1f - BEZEL, -COVER_WH * 0.5f, a), outerFace(BEZEL, -COVER_WH * 0.5f, a)
            )
            canvas.save()
            canvas.concat(m)
            val k = COVER_W / (1f - 2f * BEZEL)
            rect.set(-BEZEL * k, (COVER_WH * 0.5f - top) * k, (1f - BEZEL) * k, (COVER_WH * 0.5f + top) * k)
            bodyPaint.color = shade(n, BODY_COLOR)
            drawRounded(canvas, rect, CORNER * k, CORNER * k, bodyPaint)
            rect.set(0f, 0f, COVER_W, COVER_H)
            drawScreen(canvas, rect, SCREEN_CORNER * k, SCREEN_CORNER * k, !innerOn(), coverShader, COVER_SPEC)
            canvas.restore()
        } else if (cam.facing(n * -1f, innerFace(0.5f, 0f, a))) {
            // Inner display, left half, on the moving leaf.
            val hw = 1f - BEZEL
            val m = mapRect(
                cam, INNER_W, INNER_H,
                innerFace(hw, INNER_WH * 0.5f, a), innerFace(-hw, INNER_WH * 0.5f, a),
                innerFace(-hw, -INNER_WH * 0.5f, a), innerFace(hw, -INNER_WH * 0.5f, a)
            )
            canvas.save()
            canvas.concat(m)
            val k = INNER_W / (2f * hw)
            rect.set(INNER_W * 0.5f - k, (INNER_WH * 0.5f - top) * k, INNER_W * 0.5f, (INNER_WH * 0.5f + top) * k)
            bodyPaint.color = shade(n * -1f, BODY_COLOR)
            drawRounded(canvas, rect, CORNER * k, 0f, bodyPaint)
            rect.set(0f, 0f, INNER_W * 0.5f + CREASE_OVERLAP, INNER_H)
            drawScreen(canvas, rect, SCREEN_CORNER * k, 0f, innerOn(), innerShader, INNER_SPEC)
            canvas.restore()
        }
    }

    private fun innerOn() = angle >= handoffAngle

    /** Rounded rectangle with separate left and right corner radii. */
    private fun drawRounded(canvas: Canvas, r: RectF, left: Float, right: Float, paint: Paint) {
        path.reset()
        path.addRoundRect(r, floatArrayOf(left, left, right, right, right, right, left, left), Path.Direction.CW)
        canvas.drawPath(path, paint)
    }

    private fun drawScreen(
        canvas: Canvas, r: RectF, left: Float, right: Float,
        on: Boolean, ws: WallpaperShader?, spec: SurfaceSpec
    ) {
        canvas.save()
        path.reset()
        path.addRoundRect(r, floatArrayOf(left, left, right, right, right, right, left, left), Path.Direction.CW)
        canvas.clipPath(path)
        if (on && ws != null && ws.isReady) {
            val t = if (motion) (SystemClock.uptimeMillis() - startMs) / 1000f else FoldWallpaperService.STILL_TIME
            // The shader traces rays from this camera, in units of the surface's panel width.
            val unit = if (spec.isInner) 1f - BEZEL else 1f - 2f * BEZEL
            ws.bind(
                spec, Math.toRadians(angle.toDouble()).toFloat(), t, effect, intensity, motion,
                eyeX = eyeWorld.x / unit,
                eyeY = spec.planeHeight * 0.5f - eyeWorld.y / unit,
                eyeZ = eyeWorld.z / unit
            )
            screenPaint.shader = ws.shader
            canvas.drawRect(0f, 0f, spec.width.toFloat(), spec.height.toFloat(), screenPaint)
        } else {
            offPaint.shader = LinearGradient(
                r.left, r.top, r.right, r.bottom,
                Color.rgb(16, 18, 24), Color.rgb(4, 5, 7), Shader.TileMode.CLAMP
            )
            canvas.drawRect(r, offPaint)
        }
        canvas.restore()
    }

    // endregion

    // region Timeline

    private fun timelineAngle(elapsedMs: Long): Float {
        val t = (elapsedMs % PERIOD_MS) / 1000f
        return when {
            t < 0.9f -> 0f
            t < 4.1f -> 180f * smooth((t - 0.9f) / 3.2f)
            t < 5.5f -> 180f
            else -> 180f * (1f - smooth((t - 5.5f) / 2.3f))
        }
    }

    private fun timelineOffsetFor(degrees: Float): Long {
        val p = degrees / 180f
        var lo = 0f
        var hi = 1f
        repeat(20) {
            val mid = (lo + hi) * 0.5f
            if (smooth(mid) < p) lo = mid else hi = mid
        }
        return ((0.9f + lo * 3.2f) * 1000f).toLong()
    }

    // endregion

    private companion object {
        /** Shared so a view that Compose detaches and reattaches can still load photos. */
        val loader: ExecutorService = Executors.newSingleThreadExecutor()

        const val PERIOD_MS = 7_800L
        const val TEX_W = 1024

        const val BEZEL = 0.04f
        const val THICK = 0.068f
        const val CORNER = 0.11f
        const val SCREEN_CORNER = 0.07f

        /** Local pixels the moving half overdraws past the crease to hide anti-aliased seams. */
        const val CREASE_OVERLAP = 2f

        // Surfaces in virtual pixels, Pixel 9 Pro Fold proportions.
        const val COVER_W = 540f
        const val COVER_H = 1212f
        const val INNER_W = 1038f
        const val INNER_H = 1076f

        // Heights in panel widths (the leaf is 1.0 wide).
        val COVER_WH = COVER_H / (COVER_W / (1f - 2f * BEZEL))
        val INNER_WH = INNER_H / (INNER_W / (2f * (1f - BEZEL)))
        val BODY_H = max(COVER_WH, INNER_WH) + 2f * BEZEL

        val PLANE_H = max(COVER_H / COVER_W, INNER_H / (INNER_W * 0.5f))
        val COVER_SPEC = SurfaceSpec(COVER_W.toInt(), COVER_H.toInt(), 0, false, COVER_H / COVER_W, PLANE_H)
        val INNER_SPEC = SurfaceSpec(INNER_W.toInt(), INNER_H.toInt(), 0, true, INNER_H / (INNER_W * 0.5f), PLANE_H)

        val BODY_COLOR = Color.rgb(34, 37, 44)
        val EDGE_COLOR = Color.rgb(92, 98, 110)

        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        fun smooth(t: Float): Float { val c = t.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
    }
}
