package com.lumifold.wallpaper.render

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
import java.io.Closeable
import java.io.File

/**
 * Plays a clip on a silent loop and hands each decoded frame to [onFrame] as a hardware bitmap,
 * without copying pixels: the decoder writes into GPU buffers that the shader samples directly.
 */
class VideoFrames(
    file: File,
    handler: Handler,
    private val onFrame: (Bitmap) -> Unit
) : Closeable {

    private val reader: ImageReader
    private val player = MediaPlayer()
    private var prepared = false
    private var wantPlaying = true
    private var closed = false

    // The GPU may still be drawing the previous frame, so its buffer is released one frame late.
    private val held = ArrayDeque<Image>()

    init {
        val (w, h) = MediaMetadataRetriever().run {
            try {
                setDataSource(file.path)
                val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
                val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
                width to height
            } finally {
                release()
            }
        }
        reader = ImageReader.newInstance(w, h, ImageFormat.PRIVATE, MAX_IMAGES, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
        reader.setOnImageAvailableListener({ r -> onImage(r) }, handler)
        player.setSurface(reader.surface)
        player.setDataSource(file.path)
        player.isLooping = true
        player.setVolume(0f, 0f)
        player.setOnPreparedListener {
            prepared = true
            if (wantPlaying && !closed) it.start()
        }
        player.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "Playback error $what/$extra for ${file.name}")
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
    }
}
