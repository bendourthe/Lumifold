package com.lumifold.wallpaper.ui

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
import androidx.core.graphics.withMatrix
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import com.lumifold.wallpaper.model.OpeningEffect
import com.lumifold.wallpaper.model.WallpaperItem
import com.lumifold.wallpaper.model.WallpaperRepository
import com.lumifold.wallpaper.model.WallpaperSource
import com.lumifold.wallpaper.render.FoldOptics
import com.lumifold.wallpaper.render.SurfaceSpec
import com.lumifold.wallpaper.render.ClipSource
import com.lumifold.wallpaper.render.VideoFrames
import com.lumifold.wallpaper.render.VideoPreviewSlots
import com.lumifold.wallpaper.render.WallpaperShader
import com.lumifold.wallpaper.service.FoldWallpaperService
import com.lumifold.wallpaper.service.VideoCache
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.exp
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

    /** Shows the open inner display head-on instead of the 3D device. */
    var flat = false
        set(value) { field = value; invalidate() }

    /** Thumbnails leave touches to the card around them. The opening model keeps them. */
    var handleTouches = true

    /** Real hinge angle for the flat view's opening effect; NaN draws the screen at rest. */
    var hingeDegrees = Float.NaN
        set(value) { field = value; invalidate() }

    private var item: WallpaperItem? = null
    private var effect = OpeningEffect.DEFAULT
    private var intensity = 1f
    private var motion = true
    private var foldEffect = true

    private var coverShader: WallpaperShader? = null
    private var innerShader: WallpaperShader? = null
    private var loadingId: String? = null
    private var video: VideoFrames? = null
    private var videoId: String? = null
    private var photoLoadedEdge = -1
    private var videoSource: ClipSource? = null
    private var holdsSlot = false
    private var slotRetry = false
    private var videoToken = 0
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
    private var camera: Camera3? = null
    private var cameraOpen = Float.NaN

    /** The camera for this view size and opening, rebuilt only when either changes. */
    private fun cameraFor(open: Float): Camera3 {
        camera?.let { if (it.w == width.toFloat() && it.h == height.toFloat() && cameraOpen == open) return it }
        cameraOpen = open
        return Camera3(width.toFloat(), height.toFloat(), open).also { camera = it }
    }

    fun setWallpaper(next: WallpaperItem) {
        val edge = photoEdge()
        if (item?.id == next.id) {
            val photoNeedsReload = next.source is WallpaperSource.Photo &&
                coverShader != null && photoLoadedEdge != edge
            if (!photoNeedsReload) {
                refreshVideo()
                return
            }
        }
        item = next
        photoLoadedEdge = edge
        stopVideo()
        loadingId = next.id
        // Compiling a shader and decoding a photo take tens of milliseconds; doing them here made
        // the grid stutter while scrolling.
        loader.execute {
            val cover = runCatching { WallpaperShader.create(next) }.getOrNull() ?: return@execute
            var pyramid: com.lumifold.wallpaper.render.BlurPyramid? = null
            when (val source = next.source) {
                is WallpaperSource.Video ->
                    runCatching { WallpaperShader.decodePoster(resources, source) }.getOrNull()?.let { cover.setVideoFrame(it) }
                is WallpaperSource.Photo -> {
                    pyramid = runCatching {
                        val plane = measuredPlane()
                        val texW = min(1600, measuredInnerWidth())
                        WallpaperShader.buildPyramid(resources, source, texW, (texW * plane / 2f).toInt(), edge)
                    }.getOrNull() ?: return@execute
                    cover.attachPyramid(pyramid)
                }
                is WallpaperSource.Generative -> Unit
            }
            mainHandler.post {
                if (loadingId != next.id) {
                    pyramid?.recycle()
                    return@post
                }
                loadingId = null
                install(cover)
                refreshVideo()
            }
        }
    }

    private fun install(cover: WallpaperShader) {
        val oldPyramid = coverShader?.pyramid
        coverShader = cover
        // The flat view draws one screen, so it needs only one shader.
        innerShader = if (flat) cover else cover.duplicate()
        if (oldPyramid != null && oldPyramid !== cover.pyramid) oldPyramid.recycle()
        invalidate()
    }

    fun setEffect(effect: OpeningEffect, intensity: Float, motion: Boolean, foldEffect: Boolean = true) {
        val stopped = this.motion && !motion
        this.effect = effect
        this.intensity = intensity
        this.motion = motion
        this.foldEffect = foldEffect
        updateVideoPlayback()
        if (stopped) showPoster()
        invalidate()
    }

    /** With the live effect off, a clip shows its still poster, as the wallpaper does, not a paused frame. */
    private fun showPoster() {
        val current = item ?: return
        val source = current.source as? WallpaperSource.Video ?: return
        loader.execute {
            val poster = runCatching { WallpaperShader.decodePoster(resources, source) }.getOrNull() ?: return@execute
            mainHandler.post {
                if (motion || item?.id != current.id) return@post
                coverShader?.setVideoFrame(poster)
                if (innerShader !== coverShader) innerShader?.setVideoFrame(poster)
                invalidate()
            }
        }
    }

    /**
     * Plays the current video wallpaper. Grid tiles play the bundled loop; the large preview plays
     * the full clip once it is on the phone, the same footage the wallpaper uses.
     */
    fun refreshVideo() {
        val current = item ?: return
        if (current.source !is WallpaperSource.Video || !isAttachedToWindow || coverShader?.item?.id != current.id) return
        val edge = previewEdge()
        if (edge == 0) return
        val needsSlot = edge <= 640
        val source = (if (needsSlot) ClipSource.light(context, current) else ClipSource.best(context, current)) ?: return
        if (videoId == current.id && videoSource == source) return
        stopVideo()
        if (needsSlot) {
            if (!VideoPreviewSlots.tryAcquire()) {
                if (!slotRetry) {
                    slotRetry = true
                    mainHandler.postDelayed({
                        slotRetry = false
                        refreshVideo()
                    }, 500)
                }
                return
            }
            holdsSlot = true
        }
        videoSource = source
        videoId = current.id
        val token = ++videoToken
        // Opening the file and reading its size blocks for a moment; keep it off the main thread.
        videoLoader.execute {
            val frames = runCatching {
                VideoFrames(context, source, mainHandler, edge, onError = { retryVideoSoon() }) { frame ->
                    // A frame already decoded when playback paused must not replace the poster.
                    if (item?.id == current.id && motion) {
                        coverShader?.setVideoFrame(frame)
                        if (innerShader !== coverShader) innerShader?.setVideoFrame(frame)
                        invalidate()
                    }
                }
            }.getOrNull()
            mainHandler.post {
                if (token != videoToken) {
                    frames?.close()
                    return@post
                }
                if (frames == null) {
                    stopVideo()
                    return@post
                }
                video = frames
                updateVideoPlayback()
            }
        }
    }

    private fun runsOnClock(): Boolean = when (val s = item?.source) {
        is WallpaperSource.Generative -> true
        is WallpaperSource.Photo -> s.motion != com.lumifold.wallpaper.model.MotionStyle.NONE
        else -> false
    }

    /** A decoder can be refused while many tiles start at once; try again shortly. */
    private fun retryVideoSoon() {
        mainHandler.post {
            stopVideo()
            mainHandler.postDelayed({ refreshVideo() }, 900L + (Math.random() * 1200).toLong())
        }
    }

    private fun previewEdge(): Int = when {
        width <= 0 -> 0
        width < 900 -> 640
        else -> 1920
    }

    private fun photoEdge(): Int = when {
        width <= 0 -> 960
        width < 900 -> 960
        else -> 1920
    }

    private fun stopVideo() {
        videoToken++
        video?.close()
        video = null
        videoId = null
        videoSource = null
        if (holdsSlot) {
            holdsSlot = false
            VideoPreviewSlots.release()
        }
    }

    private val slotListener = {
        updateVideoPlayback()
        if (!VideoPreviewSlots.scrolling) refreshVideo()
    }

    private fun updateVideoPlayback() {
        if (motion && !VideoPreviewSlots.scrolling) video?.play() else video?.pause()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        VideoPreviewSlots.addListener(slotListener)
        refreshVideo()
    }

    override fun onDetachedFromWindow() {
        VideoPreviewSlots.removeListener(slotListener)
        stopVideo()
        super.onDetachedFromWindow()
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        item?.let { setWallpaper(it) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!handleTouches) return false
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
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    /** A tap, or an accessibility click, takes manual control of the model like a drag does. */
    override fun performClick(): Boolean {
        super.performClick()
        setAutoPlay(false)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (flat) {
            drawFlat(canvas)
            // Video frames redraw as they arrive; only drawn and drifting scenes need a clock.
            if (motion && runsOnClock() && !VideoPreviewSlots.scrolling) {
                if (width < 900) postInvalidateDelayed(THUMB_FRAME_MS) else postInvalidateOnAnimation()
            }
            return
        }
        if (isAutoPlaying && !VideoPreviewSlots.scrolling) updateAngle(timelineAngle(SystemClock.uptimeMillis() - playStartMs))

        val a = Math.toRadians(angle.toDouble()).toFloat()
        val cam = cameraFor(smooth(angle / 180f))
        eyeWorld = cam.eye

        drawShadow(canvas, cam, a)
        drawBackLeaf(canvas, cam)
        drawMovingLeaf(canvas, cam, a)
        drawCrease(canvas, cam, a)

        if (!VideoPreviewSlots.scrolling && (isAutoPlaying || (motion && item != null))) postInvalidateOnAnimation()
    }

    private fun prefs() =
        context.getSharedPreferences(WallpaperRepository.PREFS_NAME, Context.MODE_PRIVATE)

    private fun measuredInnerWidth(): Int =
        prefs().getInt(WallpaperRepository.KEY_INNER_W, WallpaperRepository.DEFAULT_INNER_W)

    /** Plane height from the sizes the wallpaper has measured on this phone. */
    private fun measuredPlane(): Float {
        val p = prefs()
        return FoldOptics.planeHeight(
            p.getInt(WallpaperRepository.KEY_INNER_W, WallpaperRepository.DEFAULT_INNER_W),
            p.getInt(WallpaperRepository.KEY_INNER_H, WallpaperRepository.DEFAULT_INNER_H),
            p.getInt(WallpaperRepository.KEY_COVER_W, WallpaperRepository.DEFAULT_COVER_W),
            p.getInt(WallpaperRepository.KEY_COVER_H, WallpaperRepository.DEFAULT_COVER_H)
        )
    }

    private fun innerSpec() = INNER_SPEC.copy(planeHeight = measuredPlane())

    private fun coverSpec() = COVER_SPEC.copy(planeHeight = measuredPlane())

    /**
     * The display the app is open on. A tall cover uses the cover crop; the near-square inner
     * display uses the inner crop. Both use the measured plane, so this matches the wallpaper.
     */
    private fun liveSpec(): SurfaceSpec {
        val p = prefs()
        val iw = p.getInt(WallpaperRepository.KEY_INNER_W, WallpaperRepository.DEFAULT_INNER_W)
        val ih = p.getInt(WallpaperRepository.KEY_INNER_H, WallpaperRepository.DEFAULT_INNER_H)
        val cw = p.getInt(WallpaperRepository.KEY_COVER_W, WallpaperRepository.DEFAULT_COVER_W)
        val ch = p.getInt(WallpaperRepository.KEY_COVER_H, WallpaperRepository.DEFAULT_COVER_H)
        val plane = FoldOptics.planeHeight(iw, ih, cw, ch)
        val dm = resources.displayMetrics
        return if (FoldOptics.isInnerDisplay(dm.widthPixels, dm.heightPixels)) {
            SurfaceSpec(iw, ih, 0, true, ih.toFloat() / (iw * 0.5f), plane)
        } else {
            SurfaceSpec(cw, ch, 0, false, ch.toFloat() / cw.toFloat(), plane)
        }
    }

    private fun drawFlat(canvas: Canvas) {
        val spec = liveSpec()
        val sw = spec.width.toFloat()
        val sh = spec.height.toFloat()
        val scale = min(width / sw, height / sh) * 0.96f
        val w = sw * scale
        val h = sh * scale
        canvas.withTranslation((width - w) * 0.5f, (height - h) * 0.5f) {
            scale(scale, scale)
            rect.set(0f, 0f, sw, sh)
            val radius = 26f * resources.displayMetrics.density / scale
            val ws = innerShader
            if (ws != null && ws.isReady) {
                val t = if (motion) (SystemClock.uptimeMillis() - startMs) / 1000f else FoldWallpaperService.STILL_TIME
                val rest = if (spec.isInner) 180f else 0f
                val raw = hingeDegrees.takeUnless { it.isNaN() } ?: rest
                // Same resting snap as the wallpaper, so a phone held open or shut shows a sharp picture.
                val shown = when {
                    raw >= 165f -> 180f
                    raw <= 15f -> 0f
                    else -> raw
                }.let { if (spec.isInner) it.coerceAtLeast(90f) else it.coerceAtMost(90f) }
                ws.bind(spec, Math.toRadians(shown.toDouble()).toFloat(), t, effect, intensity, motion, foldEffect,
                    symmetricInner = WallpaperRepository.isSymmetricInner(context),
                    fullSwipe = WallpaperRepository.isFullSwipe(context))
                screenPaint.shader = ws.shader
                path.reset()
                path.addRoundRect(rect, radius, radius, Path.Direction.CW)
                clipPath(path)
                drawRect(rect, screenPaint)
            } else {
                offPaint.shader = null
                offPaint.color = Color.rgb(16, 18, 24)
                drawRoundRect(rect, radius, radius, offPaint)
            }
        }
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
        val eye = target + Vec(2.1f, 1.65f, 8.2f)
        private val f = (target - eye).norm()
        private val r = f.cross(Vec(0f, 1f, 0f)).norm()
        private val u = r.cross(f)
        private val focal: Float
        private val cx = w * 0.5f
        private val cy = h * 0.47f

        init {
            val dist = sqrt((eye - target).dot(eye - target))
            val byHeight = h * 0.76f * dist / BODY_H
            val byWidth = w * 0.82f * dist / lerp(1.15f, 2.15f, open)
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
        Vec(u * cos(a) - THICK * 0.5f * sin(a), y, THICK * 0.5f * cos(a) + u * sin(a))

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

    /** Broad studio cards reflected by a satin metal edge, in the same world as the camera. */
    private fun metal(normal: Vec, point: Vec): Int {
        val view = (eyeWorld - point).norm()
        val reflected = normal * (2f * normal.dot(view)) - view
        val key = Vec(-0.55f, 0.65f, 0.8f).norm()
        val strip = exp(-((reflected.x + 0.45f) * (reflected.x + 0.45f)) / 0.045f) *
            (0.6f + 0.4f * max(0f, reflected.y))
        val fill = exp(-((reflected.x - 0.8f) * (reflected.x - 0.8f)) / 0.12f)
        val light = 29f + 24f * max(0f, normal.dot(key)) + 92f * strip + 28f * fill
        return Color.rgb(light.toInt(), (light * 1.015f).toInt(), (light * 1.025f).toInt())
    }

    /** Rounded perimeter rings form the sidewall and both chamfers, including the corners. */
    private fun drawChassis(canvas: Canvas, cam: Camera3, a: Float, moving: Boolean) {
        fun world(x: Float, y: Float, z: Float): Vec = if (moving) {
            Vec(x * cos(a) - z * sin(a), y, x * sin(a) + z * cos(a))
        } else Vec(x, y, z - THICK)
        val rings = listOf(0.007f to 0f, 0f to 0.009f, 0f to THICK - 0.009f, 0.007f to THICK)
        val contours = rings.map { (inset, z) ->
            List(52) { i ->
                val corner = i / 13
                val outer = corner == 0 || corner == 3
                val cornerRadius = if (outer) CORNER else HINGE_CORNER
                val radius = cornerRadius - inset
                val theta = Math.toRadians((corner * 90 + (i % 13) * 7.5).toDouble()).toFloat()
                val cx = if (outer) 1f - cornerRadius else cornerRadius
                val cy = if (corner < 2) BODY_H * 0.5f - cornerRadius else -BODY_H * 0.5f + cornerRadius
                world(cx + radius * cos(theta), cy + radius * sin(theta), z)
            }
        }
        data class Facet(val points: Array<Vec>, val normal: Vec, val center: Vec)
        val faces = ArrayList<Facet>(156)
        for (ring in 0..2) {
            for (i in contours[ring].indices) {
                val j = (i + 1) % contours[ring].size
                val p = arrayOf(contours[ring][i], contours[ring][j], contours[ring + 1][j], contours[ring + 1][i])
                val normal = (p[1] - p[0]).cross(p[3] - p[0]).norm()
                val center = (p[0] + p[1] + p[2] + p[3]) * 0.25f
                if (cam.facing(normal, center)) faces.add(Facet(p, normal, center))
            }
        }
        // Far faces first. The screen is a separate glass surface on top of this solid body.
        faces.sortByDescending { (it.center - eyeWorld).dot(it.center - eyeWorld) }
        edgePaint.shader = null
        edgePaint.style = Paint.Style.FILL
        for (face in faces) {
            edgePaint.color = metal(face.normal, face.center)
            fillQuad(canvas, cam, edgePaint, *face.points)
        }
    }

    private fun drawHinge(canvas: Canvas, cam: Camera3) {
        edgePaint.shader = null
        for (i in 0 until 18) {
            val a = Math.PI.toFloat() * (0.5f + i / 18f)
            val b = Math.PI.toFloat() * (0.5f + (i + 1) / 18f)
            val radius = THICK * 0.48f
            val normal = Vec(cos((a + b) * 0.5f), 0f, sin((a + b) * 0.5f))
            val point = Vec(-0.002f, 0f, 0f)
            if (!cam.facing(normal, point)) continue
            edgePaint.color = metal(normal, point)
            val top = BODY_H * 0.5f - 0.055f
            fillQuad(canvas, cam, edgePaint,
                Vec(radius * cos(a), top, radius * sin(a)), Vec(radius * cos(b), top, radius * sin(b)),
                Vec(radius * cos(b), -top, radius * sin(b)), Vec(radius * cos(a), -top, radius * sin(a)))
        }
    }

    private fun drawRim(canvas: Canvas, r: RectF, left: Float, right: Float) {
        bodyPaint.shader = LinearGradient(r.left, r.top, r.right, r.bottom,
            intArrayOf(Color.rgb(97, 101, 104), Color.rgb(28, 30, 32), Color.rgb(66, 70, 73)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        drawRounded(canvas, r, left, right, bodyPaint)
        bodyPaint.shader = null
        bodyPaint.color = Color.rgb(8, 9, 10)
        val inset = r.height() * 0.0018f
        val glass = RectF(r).apply { inset(inset, inset) }
        drawRounded(canvas, glass, max(0f, left - inset), max(0f, right - inset), bodyPaint)
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
        canvas.withScale(1f, ry / rx, dst[0], dst[1]) {
            drawCircle(dst[0], dst[1], rx, shadowPaint)
        }
    }

    private fun drawBackLeaf(canvas: Canvas, cam: Camera3) {
        val top = BODY_H * 0.5f
        drawChassis(canvas, cam, 0f, false)
        drawHinge(canvas, cam)

        // Inner display, right half, on the back leaf (plane z = 0).
        val hw = 1f - BEZEL
        val m = mapRect(
            cam, INNER_W, INNER_H,
            Vec(-hw, INNER_WH * 0.5f, 0f), Vec(hw, INNER_WH * 0.5f, 0f),
            Vec(hw, -INNER_WH * 0.5f, 0f), Vec(-hw, -INNER_WH * 0.5f, 0f)
        )
        canvas.withMatrix(m) {
            val k = INNER_W / (2f * hw)
            rect.set(INNER_W * 0.5f, (INNER_WH * 0.5f - top) * k, INNER_W * 0.5f + k, (INNER_WH * 0.5f + top) * k)
            drawRim(this, rect, HINGE_CORNER * k, CORNER * k)
            rect.set(INNER_W * 0.5f, 0f, INNER_W, INNER_H)
            drawScreen(this, rect, 0f, SCREEN_CORNER * k, true, innerShader, innerSpec())
            drawCamera(this, INNER_W - 42f, 34f, 7f)
        }
    }

    private fun drawMovingLeaf(canvas: Canvas, cam: Camera3, a: Float) {
        val top = BODY_H * 0.5f
        drawChassis(canvas, cam, a, true)

        val n = outerNormal(a)
        if (cam.facing(n, outerFace(0.5f, 0f, a))) {
            // Cover display: hinge on its left edge when seen from outside.
            val m = mapRect(
                cam, COVER_W, COVER_H,
                outerFace(COVER_INSET, COVER_WH * 0.5f, a), outerFace(1f - COVER_INSET, COVER_WH * 0.5f, a),
                outerFace(1f - COVER_INSET, -COVER_WH * 0.5f, a), outerFace(COVER_INSET, -COVER_WH * 0.5f, a)
            )
            canvas.withMatrix(m) {
                val k = COVER_W / (1f - 2f * COVER_INSET)
                rect.set(-COVER_INSET * k, (COVER_WH * 0.5f - top) * k, (1f - COVER_INSET) * k, (COVER_WH * 0.5f + top) * k)
                drawRim(this, rect, HINGE_CORNER * k, CORNER * k)
                rect.set(0f, 0f, COVER_W, COVER_H)
                drawScreen(this, rect, HINGE_CORNER * k, SCREEN_CORNER * k, true, coverShader, coverSpec())
                drawCamera(this, COVER_W * 0.5f, 27f, 8f)
            }
        } else if (cam.facing(n * -1f, innerFace(0.5f, 0f, a))) {
            // Inner display, left half, on the moving leaf.
            val hw = 1f - BEZEL
            val m = mapRect(
                cam, INNER_W, INNER_H,
                innerFace(hw, INNER_WH * 0.5f, a), innerFace(-hw, INNER_WH * 0.5f, a),
                innerFace(-hw, -INNER_WH * 0.5f, a), innerFace(hw, -INNER_WH * 0.5f, a)
            )
            canvas.withMatrix(m) {
                val k = INNER_W / (2f * hw)
                rect.set(INNER_W * 0.5f - k, (INNER_WH * 0.5f - top) * k, INNER_W * 0.5f, (INNER_WH * 0.5f + top) * k)
                drawRim(this, rect, CORNER * k, HINGE_CORNER * k)
                rect.set(0f, 0f, INNER_W * 0.5f + CREASE_OVERLAP, INNER_H)
                drawScreen(this, rect, SCREEN_CORNER * k, 0f, true, innerShader, innerSpec())
            }
        }
    }

    private fun drawCamera(canvas: Canvas, x: Float, y: Float, radius: Float) {
        offPaint.shader = null
        offPaint.color = Color.rgb(34, 37, 40)
        canvas.drawCircle(x, y, radius + 1.3f, offPaint)
        offPaint.color = Color.rgb(2, 3, 5)
        canvas.drawCircle(x, y, radius, offPaint)
        offPaint.color = Color.rgb(21, 30, 41)
        canvas.drawCircle(x - radius * 0.2f, y - radius * 0.25f, radius * 0.28f, offPaint)
    }

    /** The flexible display is continuous over the hinge, with a small curved bend radius. */
    private fun drawCrease(canvas: Canvas, cam: Camera3, a: Float) {
        if (!cam.facing(outerNormal(a) * -1f, innerFace(0.5f, 0f, a))) return
        val bend = 0.025f
        val halfHeight = INNER_WH * 0.5f
        val k = INNER_W / (2f * (1f - BEZEL))
        fun point(t: Float, y: Float): Vec {
            val left = innerFace(bend, y, a)
            val right = Vec(bend, y, 0f)
            val middle = Vec(0f, y, 0f)
            return left * ((1f - t) * (1f - t)) + middle * (2f * t * (1f - t)) + right * (t * t)
        }
        // Use the same UVs and glass reflection as both adjacent flat portions.
        for (i in 0 until 16) {
            val t0 = i / 16f
            val t1 = (i + 1) / 16f
            val x0 = INNER_W * 0.5f + (2f * t0 - 1f) * bend * k
            val x1 = INNER_W * 0.5f + (2f * t1 - 1f) * bend * k
            val mapped = mapRect(cam, x1 - x0, INNER_H,
                point(t0, halfHeight), point(t1, halfHeight), point(t1, -halfHeight), point(t0, -halfHeight))
            canvas.withMatrix(mapped) {
                translate(-x0, 0f)
                rect.set(x0 - 0.5f, 0f, x1 + 0.5f, INNER_H)
                drawScreen(this, rect, 0f, 0f, true, innerShader, innerSpec())
            }
        }
    }

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
        canvas.withSave {
            path.reset()
            path.addRoundRect(r, floatArrayOf(left, left, right, right, right, right, left, left), Path.Direction.CW)
            clipPath(path)
            if (on && ws != null && ws.isReady) {
                val t = if (motion) (SystemClock.uptimeMillis() - startMs) / 1000f else FoldWallpaperService.STILL_TIME
                ws.bind(
                    spec, Math.toRadians(angle.toDouble()).toFloat(), t, effect, intensity, motion, foldEffect,
                    symmetricInner = WallpaperRepository.isSymmetricInner(context),
                    fullSwipe = WallpaperRepository.isFullSwipe(context)
                )
                screenPaint.shader = ws.shader
                drawRect(0f, 0f, spec.width.toFloat(), spec.height.toFloat(), screenPaint)
            } else {
                offPaint.shader = LinearGradient(
                    r.left, r.top, r.right, r.bottom,
                    Color.rgb(16, 18, 24), Color.rgb(4, 5, 7), Shader.TileMode.CLAMP
                )
                drawRect(r, offPaint)
            }
            // A broad softbox reflection sits on the glass, independent of the wallpaper lighting.
            offPaint.shader = LinearGradient(0f, 0f, spec.width.toFloat(), spec.height.toFloat(),
                intArrayOf(Color.argb(19, 230, 237, 243), Color.TRANSPARENT, Color.argb(5, 230, 237, 243)),
                floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
            drawRect(r, offPaint)
        }
    }

    // endregion

    // region Timeline

    private fun timelineAngle(elapsedMs: Long): Float {
        val t = (elapsedMs % PERIOD_MS) / 1000f
        return when {
            t < 0.3f -> 0f
            t < 3.5f -> 180f * smooth((t - 0.3f) / 3.2f)
            t < 4.4f -> 180f
            else -> 180f * (1f - smooth((t - 4.4f) / 2.6f))
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
        /** Grid thumbnails redraw at about 20 frames a second; the main preview keeps the display rate. */
        const val THUMB_FRAME_MS = 50L

        /** Shared so a view that Compose detaches and reattaches can still load photos. */
        val loader: ExecutorService = Executors.newFixedThreadPool(2)

        /** Opens video files for tiles without blocking the main thread or the shader loader. */
        val videoLoader: ExecutorService = Executors.newSingleThreadExecutor()

        const val PERIOD_MS = 7_800L
        const val TEX_W = 1024

        const val BEZEL = 0.025f
        const val THICK = 5.0f / 75.2f
        const val CORNER = 0.13f
        const val HINGE_CORNER = 0.025f
        const val SCREEN_CORNER = 0.09f

        /** Local pixels the moving half overdraws past the crease to hide anti-aliased seams. */
        const val CREASE_OVERLAP = 2f

        // Pixel 11 Pro Fold: cover 1080×2342 (19.5:9), inner 2076×2152, half size for the preview.
        // Closed body is 155.2 × 76.0 × 10.1 mm. Open slab is 5.0 mm, which is THICK in panel widths.
        const val COVER_W = 540f
        const val COVER_H = 1170f
        const val INNER_W = 1038f
        const val INNER_H = 1076f

        // Heights in panel widths (the leaf is 1.0 wide).
        const val BODY_H = 155.2f / 75.2f
        const val COVER_WH = BODY_H - 0.07f
        const val COVER_INSET = (1f - COVER_WH * COVER_W / COVER_H) * 0.5f
        val INNER_WH = INNER_H / (INNER_W / (2f * (1f - BEZEL)))

        val PLANE_H = max(COVER_H / COVER_W, INNER_H / (INNER_W * 0.5f))
        val COVER_SPEC = SurfaceSpec(COVER_W.toInt(), COVER_H.toInt(), 0, false, COVER_H / COVER_W, PLANE_H)
        val INNER_SPEC = SurfaceSpec(INNER_W.toInt(), INNER_H.toInt(), 0, true, INNER_H / (INNER_W * 0.5f), PLANE_H)

        val BODY_COLOR = Color.rgb(24, 24, 26)
        val EDGE_COLOR = Color.rgb(150, 150, 154)

        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        fun smooth(t: Float): Float { val c = t.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
    }
}
