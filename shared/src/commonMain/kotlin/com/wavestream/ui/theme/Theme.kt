package com.wavestream.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), onPrimary = Color.White, primaryContainer = Color(0xFF3B3B8F), onPrimaryContainer = Color(0xFFC8C8FF),
    secondary = Color(0xFF8B5CF6), onSecondary = Color.White, secondaryContainer = Color(0xFF4C1D95), onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = Color(0xFF06B6D4), onTertiary = Color.White,
    background = Color(0xFF0B0B11), onBackground = Color(0xFFE5E5EA),
    surface = Color(0xFF14141C), onSurface = Color(0xFFE5E5EA), surfaceVariant = Color(0xFF1F1F2C), onSurfaceVariant = Color(0xFFC4C4D0),
    error = Color(0xFFEF4444), onError = Color.White, outline = Color(0xFF3F3F5F),
)

@Composable fun WaveStreamTheme(useDarkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = WaveTypography, content = content)
}
