package com.lumifold.wallpaper.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import com.lumifold.wallpaper.model.WallpaperItem
import com.lumifold.wallpaper.model.WallpaperSource
import com.lumifold.wallpaper.service.VideoCache
import java.io.Closeable
import java.io.File
import java.util.concurrent.Semaphore
import kotlin.math.max

/** Where a clip's frames come from: the downloaded full clip, or the loop bundled with the app. */
sealed interface ClipSource {
    data class Downloaded(val file: File) : ClipSource
    data class Bundled(val rawResId: Int) : ClipSource

    companion object {
        /** The small loop bundled for [item], or 0. It is cut from the same footage as the full clip. */
        fun bundledId(item: WallpaperItem): Int =
            (item.source as? WallpaperSource.Video)?.previewResId ?: 0

        /** Full clip when it is on the phone, otherwise the bundled loop, otherwise null. */
        fun best(context: Context, item: WallpaperItem): ClipSource? {
            if (VideoCache.isCached(context, item)) return Downloaded(VideoCache.file(context, item))
            return bundledId(item).takeIf { it != 0 }?.let { Bundled(it) }
        }

        /** Bundled loop first: grid thumbnails stay light and start at once. */
        fun light(context: Context, item: WallpaperItem): ClipSource? =
            bundledId(item).takeIf { it != 0 }?.let { Bundled(it) } ?: best(context, item)
    }
}

/**
 * Plays a clip on a silent loop and hands each decoded frame to [onFrame] as a hardware bitmap.
 * Frames are delivered as the decoder produces them. Slowing and seamless looping are baked into
 * the clips themselves (see tools/live_loops.py), so the player never changes speed.
 */
class VideoFrames(
    context: Context,
    source: ClipSource,
    handler: Handler,
    maxLongEdge: Int = 0,
    private val onError: () -> Unit = {},
    private val onFrame: (Bitmap) -> Unit
) : Closeable {

    private val reader: ImageReader
    private val player = MediaPlayer()
    private var prepared = false
    private var wantPlaying = true
    private var closed = false
    private val held = ArrayDeque<Image>()

    init {
        val (w, h) = MediaMetadataRetriever().run {
            try {
                when (source) {
                    is ClipSource.Downloaded -> setDataSource(source.file.path)
                    is ClipSource.Bundled -> context.resources.openRawResourceFd(source.rawResId).use {
                        setDataSource(it.fileDescriptor, it.startOffset, it.length)
                    }
                }
                val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
                val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
                val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) height to width else width to height
            } finally {
                release()
            }
        }
        val (tw, th) = fit(w, h, maxLongEdge)
        reader = ImageReader.newInstance(tw, th, ImageFormat.PRIVATE, MAX_IMAGES, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
        reader.setOnImageAvailableListener({ r -> onImage(r) }, handler)
        player.setSurface(reader.surface)
        when (source) {
            is ClipSource.Downloaded -> player.setDataSource(source.file.path)
            is ClipSource.Bundled -> context.resources.openRawResourceFd(source.rawResId).use { player.setDataSource(it) }
        }
        player.isLooping = true
        player.setVolume(0f, 0f)
        player.setOnPreparedListener {
            prepared = true
            if (wantPlaying && !closed) it.start()
        }
        player.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "Playback error $what/$extra for $source")
            if (!closed) onError()
            true
        }
        player.prepareAsync()
    }

    private fun onImage(r: ImageReader) {
        val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return
        if (closed) {
            image.close()
            return
        }
        val buffer = image.hardwareBuffer
        val bitmap = buffer?.let { Bitmap.wrapHardwareBuffer(it, ColorSpace.get(ColorSpace.Named.SRGB)) }
        buffer?.close()
        if (bitmap == null) {
            image.close()
            return
        }
        held.addLast(image)
        onFrame(bitmap)
        while (held.size > HELD_FRAMES) held.removeFirst().close()
    }

    fun play() {
        wantPlaying = true
        if (prepared && !closed && !player.isPlaying) player.start()
    }

    fun pause() {
        wantPlaying = false
        if (prepared && !closed && player.isPlaying) player.pause()
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { player.release() }
        held.forEach { it.close() }
        held.clear()
        reader.close()
    }

    private companion object {
        const val TAG = "VideoFrames"
        const val MAX_IMAGES = 4
        const val HELD_FRAMES = 2

        fun fit(width: Int, height: Int, maxLongEdge: Int): Pair<Int, Int> {
            if (maxLongEdge <= 0 || max(width, height) <= maxLongEdge) return even(width) to even(height)
            val scale = maxLongEdge.toFloat() / max(width, height)
            return even((width * scale).toInt()) to even((height * scale).toInt())
        }

        fun even(value: Int): Int = (value.coerceAtLeast(2)) and 1.inv()
    }
}

/** Limits how many grid tiles decode video at once. The wallpaper and the main preview do not take a slot. */
internal object VideoPreviewSlots {
    private val slots = Semaphore(2, true)
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    @Volatile var scrolling: Boolean = false
        private set

    fun tryAcquire(): Boolean = slots.tryAcquire()
    fun release() {
        slots.release()
    }

    fun setScrolling(value: Boolean) {
        if (scrolling == value) return
        scrolling = value
        listeners.forEach { it() }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}
