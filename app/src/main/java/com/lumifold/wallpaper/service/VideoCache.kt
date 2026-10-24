package com.lumifold.wallpaper.service

import android.content.Context
import com.lumifold.wallpaper.model.WallpaperItem
import com.lumifold.wallpaper.model.WallpaperSource
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Video clips are downloaded on demand into app storage and kept until the app is removed. */
object VideoCache {

    // Wikimedia asks clients to identify themselves.
    private const val USER_AGENT = "Lumifold/1.0 (Android live wallpaper; benjamin.dourthe@gmail.com)"

    fun file(context: Context, item: WallpaperItem): File =
        File(File(context.filesDir, "videos"), "${item.id}.webm")

    fun isCached(context: Context, item: WallpaperItem): Boolean =
        item.source !is WallpaperSource.Video || file(context, item).length() > 0

    /**
     * Downloads [item]'s clip, reporting progress from 0 to 1. Blocking: call off the main thread.
     * Returns false on failure or cancellation (when [isCancelled] turns true); never leaves a
     * partial file in place of the clip.
     */
    fun download(
        context: Context,
        item: WallpaperItem,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit
    ): Boolean {
        val video = item.source as? WallpaperSource.Video ?: return true
        val target = file(context, item)
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, "${item.id}.part")
        return runCatching {
            val conn = (URL(video.url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching false
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: (video.sizeMb * 1_000_000).toLong()
                var read = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (isCancelled()) return@runCatching false
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                read > 0 && part.renameTo(target)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false).also { if (!it) part.delete() }
    }
}
