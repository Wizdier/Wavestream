package com.wavestream.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Wavestream theme — CloudStream-inspired dark color palette with a Nuvio-style accent.
 *
 * CloudStream uses a dark-first design with a primary blue/purple accent.
 * We adopt that but use a more vibrant accent gradient for visual identity.
 */

// Dark theme colors (primary mode)
val DarkPrimary = Color(0xFF6366F1)        // Indigo 500
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkPrimaryContainer = Color(0xFF3B3B8F)
val DarkOnPrimaryContainer = Color(0xFFC8C8FF)
val DarkSecondary = Color(0xFF8B5CF6)      // Violet 500
val DarkOnSecondary = Color(0xFFFFFFFF)
val DarkSecondaryContainer = Color(0xFF4C1D95)
val DarkOnSecondaryContainer = Color(0xFFE9D5FF)
val DarkTertiary = Color(0xFF06B6D4)       // Cyan 500
val DarkOnTertiary = Color(0xFFFFFFFF)
val DarkBackground = Color(0xFF0B0B11)     // Very dark blue-black
val DarkOnBackground = Color(0xFFE5E5EA)
val DarkSurface = Color(0xFF14141C)
val DarkOnSurface = Color(0xFFE5E5EA)
val DarkSurfaceVariant = Color(0xFF1F1F2C)
val DarkOnSurfaceVariant = Color(0xFFC4C4D0)
val DarkError = Color(0xFFEF4444)
val DarkOnError = Color(0xFFFFFFFF)
val DarkOutline = Color(0xFF3F3F5F)
val DarkOutlineVariant = Color(0xFF2A2A3F)

// Light theme colors (rarely used — Wavestream is dark-first)
val LightPrimary = Color(0xFF4F46E5)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE0E0FF)
val LightOnPrimaryContainer = Color(0xFF1A1A4F)
val LightSecondary = Color(0xFF7C3AED)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFFAFAFA)
val LightOnBackground = Color(0xFF1A1A1A)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1A1A1A)
val LightSurfaceVariant = Color(0xFFE5E5EA)
val LightOnSurfaceVariant = Color(0xFF4A4A5A)
val LightError = Color(0xFFDC2626)
val LightOnError = Color(0xFFFFFFFF)

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

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = LightError,
    onError = LightOnError,
)

@Composable
fun WaveStreamTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WaveTypography,
        content = content,
    )
}
