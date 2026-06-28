package com.wizdier.wavestream.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * WaveStream's Material You theme. Nuvio influence: on Android 12+ we apply
 * the user's wallpaper-derived dynamic colour scheme; on older devices we
 * fall back to WaveStream's signature deep-blue palette. The status bar is
 * made transparent so blur backdrops can bleed under it.
 */
private val DarkColors = darkColorScheme(
    primary = WavePrimary,
    onPrimary = WaveOnPrimary,
    primaryContainer = WavePrimaryDark,
    secondary = WaveSecondary,
    tertiary = WaveTertiary,
    background = WaveBackground,
    onBackground = WaveOnBackground,
    surface = WaveSurface,
    onSurface = WaveOnSurface,
    surfaceVariant = WaveSurfaceVariant,
    error = WaveError,
    outline = WaveOutline
)

private val LightColors = lightColorScheme(
    primary = WavePrimary,
    onPrimary = WaveOnPrimary,
    primaryContainer = Color(0xFFDDE1FF),
    secondary = WaveSecondary,
    tertiary = WaveTertiary,
    background = WaveBackgroundLight,
    onBackground = WaveOnBackgroundLight,
    surface = WaveSurfaceLight,
    onSurface = WaveOnSurfaceLight,
    surfaceVariant = WaveSurfaceVariantLight,
    error = Color(0xFFB00020),
    outline = WaveOutlineLight
)

@Composable
fun WaveStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WaveTypography,
        shapes = WaveShapes,
        content = content
    )
}
