package com.ambientfold.wallpaper.model

import androidx.annotation.DrawableRes

enum class WallpaperCategory(val displayName: String) {
    LANDSCAPES("Landscapes"),
    ABSTRACT_DYNAMIC("Abstract Flow"),
    TEXTURES("Textures"),
    GEOMETRIC("Geometric"),
    MINIMAL("Minimal")
}

enum class MotionType {
    LANDSCAPE_BREATHING,
    LIQUID_GLASS,
    TEXTURE_SHIMMER
}

data class WallpaperItem(
    val id: String,
    val title: String,
    val category: WallpaperCategory,
    val isDark: Boolean = true,
    @DrawableRes val drawableResId: Int,
    val description: String,
    val isDynamic: Boolean = true,
    val motionType: MotionType = MotionType.LANDSCAPE_BREATHING,
    val particleColor: Int = -0x1
)
