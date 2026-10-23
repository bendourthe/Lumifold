package com.ambientfold.wallpaper.model

import androidx.annotation.DrawableRes

enum class WallpaperCategory {
    LANDSCAPES,
    GEOMETRIC,
    MINIMAL
}

data class WallpaperItem(
    val id: String,
    val title: String,
    val category: WallpaperCategory,
    val isDark: Boolean,
    @DrawableRes val drawableResId: Int,
    val description: String
)
