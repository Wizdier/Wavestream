package com.wavestream.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — deep ocean blue with cyan/electric accent.
private val WaveBackground = Color(0xFF0B0F14)
private val WaveSurface = Color(0xFF121821)
private val WaveSurfaceVariant = Color(0xFF1B2430)
private val WavePrimary = Color(0xFF5EEAD4)        // cyan-teal
private val WaveOnPrimary = Color(0xFF003B33)
private val WaveSecondary = Color(0xFF818CF8)      // indigo
private val WaveOnSecondary = Color(0xFF1E1B4B)
private val WaveOnBackground = Color(0xFFE2E8F0)
private val WaveOnSurface = Color(0xFFE2E8F0)
private val WaveOnSurfaceVariant = Color(0xFF94A3B8)
private val WaveOutline = Color(0xFF334155)
private val WaveError = Color(0xFFF87171)

private val WaveColorScheme = darkColorScheme(
    primary = WavePrimary,
    onPrimary = WaveOnPrimary,
    secondary = WaveSecondary,
    onSecondary = WaveOnSecondary,
    background = WaveBackground,
    onBackground = WaveOnBackground,
    surface = WaveSurface,
    onSurface = WaveOnSurface,
    surfaceVariant = WaveSurfaceVariant,
    onSurfaceVariant = WaveOnSurfaceVariant,
    outline = WaveOutline,
    error = WaveError,
    onError = Color(0xFF410E0B),
)

@Composable
fun WaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WaveColorScheme,
        typography = WaveTypography,
        content = content,
    )
}
