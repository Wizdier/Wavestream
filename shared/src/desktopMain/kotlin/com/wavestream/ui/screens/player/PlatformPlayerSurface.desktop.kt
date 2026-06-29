package com.wavestream.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformPlayerSurface(
    url: String,
    modifier: Modifier,
    isPlaying: Boolean,
) {
    // Desktop: Use JavaFX media player in a real implementation
    // For now, just show a placeholder
    Box(modifier = modifier) {
        Text("Desktop Player: $url")
    }
}
