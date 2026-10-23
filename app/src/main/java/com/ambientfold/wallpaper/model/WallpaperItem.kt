package com.ambientfold.wallpaper.model

import android.graphics.Color
import androidx.annotation.DrawableRes

enum class WallpaperCategory {
    LANDSCAPES,
    GEOMETRIC,
    MINIMAL,
    ABSTRACT_DYNAMIC
}

data class WallpaperItem(
    val id: String,
    val title: String,
    val category: WallpaperCategory,
    val isDark: Boolean = true,
    @DrawableRes val drawableResId: Int,
    val description: String,
    val isDynamic: Boolean = true,
    val particleColor: Int = Color.WHITE
)
