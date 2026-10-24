package com.ambientfold.wallpaper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Colors = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    onPrimary = Color(0xFF0B1230),
    primaryContainer = Color(0xFF223061),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFFF9A76),
    onSecondary = Color(0xFF2B0E04),
    background = Color(0xFF0A0B0F),
    onBackground = Color(0xFFECEEF3),
    surface = Color(0xFF0A0B0F),
    onSurface = Color(0xFFECEEF3),
    surfaceVariant = Color(0xFF1B1E26),
    onSurfaceVariant = Color(0xFF9AA1AF),
    surfaceContainerLowest = Color(0xFF07080B),
    surfaceContainerLow = Color(0xFF101217),
    surfaceContainer = Color(0xFF14161C),
    surfaceContainerHigh = Color(0xFF1B1E26),
    surfaceContainerHighest = Color(0xFF232731),
    outline = Color(0xFF3A404C),
    outlineVariant = Color(0xFF242832)
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
    )
}

@Composable
fun DuoBlurTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
