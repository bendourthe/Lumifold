package com.lumifold.wallpaper.service

import android.graphics.Bitmap
import android.graphics.Canvas

/** Inspect actual pixels: hasAlpha alone only says the format permits transparency. */
internal fun captureNeedsWallpaper(image: Bitmap): Boolean {
    if (!image.hasAlpha()) return false
    val row = IntArray(image.width)
    for (y in 0 until image.height) {
        image.getPixels(row, 0, row.size, 0, y, row.size, 1)
        if (row.any { it ushr 24 != 255 }) return true
    }
    return false
}

/** Both images cover the same screen bounds. Caller owns both; the wallpaper becomes the result. */
internal fun compositeWindowOnWallpaper(window: Bitmap, wallpaper: Bitmap): Bitmap {
    require(window.width == wallpaper.width && window.height == wallpaper.height)
    Canvas(wallpaper).drawBitmap(window, 0f, 0f, null)
    return wallpaper
}
