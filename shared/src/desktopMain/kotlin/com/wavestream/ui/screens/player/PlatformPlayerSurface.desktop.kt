package com.wavestream.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformPlayerSurface(url: String, modifier: Modifier, isPlaying: Boolean) {
    Box(modifier) { Text("Desktop Player: $url") }
}
