package com.lumifold.wallpaper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.core.graphics.createBitmap

/**
 * Holds the one-time screen-capture grant and takes a single picture of the current screen
 * when a fold starts. Android requires a foreground notice while the grant is active.
 */
class ScreenCaptureService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannel(CHANNEL, "Screen fold", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notice = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Lumifold")
            .setContentText("The fold effect can use the screen you are in.")
            .build()
        startForeground(NOTICE_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra(EXTRA_DATA, Intent::class.java) ?: return START_NOT_STICKY
        projection?.stop()
        projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (instance === this@ScreenCaptureService) instance = null
                stopSelf()
            }
        }, main)
        instance = this
        active.value = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        active.value = false
        projection?.stop()
        projection = null
        super.onDestroy()
    }

    fun capture(onFrame: (Bitmap) -> Unit, onMiss: () -> Unit) {
        val source = projection
        if (source == null || busy) {
            onMiss()
            return
        }
        busy = true
        try {
            val bounds = getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds
            val width = bounds.width().coerceAtMost(1080)
            val height = (bounds.height() * width / bounds.width().coerceAtLeast(1)).coerceAtLeast(1)
            val dpi = resources.displayMetrics.densityDpi
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val display: VirtualDisplay = source.createVirtualDisplay(
                "lumifold-fold",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                main
            ) ?: run {
                // Null once the projection has stopped: nothing can be captured.
                reader.close()
                busy = false
                onMiss()
                return
            }
            reader.setOnImageAvailableListener({ images ->
                val image = images.acquireLatestImage() ?: return@setOnImageAvailableListener
                val bmp = runCatching {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val stride = plane.rowStride
                    val pixelStride = plane.pixelStride.coerceAtLeast(1)
                    val rowWidth = stride / pixelStride
                    val raw = createBitmap(rowWidth, height)
                    raw.copyPixelsFromBuffer(buffer)
                    if (rowWidth == width) raw else Bitmap.createBitmap(raw, 0, 0, width, height).also { raw.recycle() }
                }.getOrNull()
                image.close()
                reader.setOnImageAvailableListener(null, null)
                reader.close()
                display.release()
                busy = false
                if (bmp != null) onFrame(bmp) else onMiss()
            }, main)
        } catch (error: Throwable) {
            busy = false
            onMiss()
        }
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "lumifold_capture"
        private const val NOTICE_ID = 41

        @Volatile var instance: ScreenCaptureService? = null
            private set

        /** Read by Settings so the switch updates when capture starts or stops. */
        val active = mutableStateOf(false)

        /** Set by the activity so Settings can open the system capture dialog. */
        var launch: (() -> Unit)? = null
    }
}
