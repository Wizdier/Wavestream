package com.wavestream.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkPrimary = Color(0xFF6366F1)
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkPrimaryContainer = Color(0xFF3B3B8F)
val DarkOnPrimaryContainer = Color(0xFFC8C8FF)
val DarkSecondary = Color(0xFF8B5CF6)
val DarkOnSecondary = Color(0xFFFFFFFF)
val DarkSecondaryContainer = Color(0xFF4C1D95)
val DarkOnSecondaryContainer = Color(0xFFE9D5FF)
val DarkTertiary = Color(0xFF06B6D4)
val DarkOnTertiary = Color(0xFFFFFFFF)
val DarkBackground = Color(0xFF0B0B11)
val DarkOnBackground = Color(0xFFE5E5EA)
val DarkSurface = Color(0xFF14141C)
val DarkOnSurface = Color(0xFFE5E5EA)
val DarkSurfaceVariant = Color(0xFF1F1F2C)
val DarkOnSurfaceVariant = Color(0xFFC4C4D0)
val DarkError = Color(0xFFEF4444)
val DarkOnError = Color(0xFFFFFFFF)
val DarkOutline = Color(0xFF3F3F5F)
val DarkOutlineVariant = Color(0xFF2A2A3F)

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    onError = DarkOnError,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

@Composable
fun WaveStreamTheme(
    useDarkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = WaveTypography,
        content = content,
    )
}
