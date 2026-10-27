package com.lumifold.wallpaper.service

/** Window metadata only; capture never needs the accessibility node tree. */
internal data class CaptureWindow(
    val id: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val active: Boolean
) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Accept one full-width window or adjacent panes, keeping the physical hinge at screen center. */
internal fun selectCaptureWindows(windows: List<CaptureWindow>, width: Int, height: Int): List<CaptureWindow>? {
    if (width <= 0 || height <= 0 || windows.count { it.active } != 1) return null
    val ordered = windows.sortedBy { it.left }
    val first = ordered.first()
    if (first.top < 0 || first.bottom > height || first.height < height * 0.8f) return null
    var edge = 0
    for (window in ordered) {
        if (window.left != edge || window.width <= 0 || window.right > width ||
            window.top != first.top || window.bottom != first.bottom) return null
        edge = window.right
    }
    return ordered.takeIf { edge == width && it.map { w -> w.id }.distinct().size == it.size }
}

/** Android throttles takeScreenshotOfWindow separately for each accessibility window ID. */
internal class WindowCaptureThrottle {
    private val requests = mutableMapOf<Int, Long>()

    fun delayMs(ids: List<Int>, now: Long): Long {
        requests.entries.removeAll { now - it.value >= INTERVAL_MS }
        return ids.maxOfOrNull { id ->
            requests[id]?.let { (INTERVAL_MS - (now - it)).coerceIn(0L, INTERVAL_MS) } ?: 0L
        } ?: 0L
    }

    fun record(id: Int, now: Long) { requests[id] = now }

    private companion object { const val INTERVAL_MS = 340L }
}

/** A retry shares the original motion budget, including time waiting for Android's interval. */
internal fun captureRetryDelay(now: Long, deadline: Long, requestedDelay: Long?): Long? {
    val delay = requestedDelay?.coerceAtLeast(16L) ?: return null
    return delay.takeIf { it < deadline - now }
}
