package com.wavestream.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

/**
 * Desktop implementation of PlatformPlayerSurface.
 *
 * On desktop we don't have ExoPlayer. A full implementation would use:
 *   - VLCJ (VLC Java bindings) — most powerful but requires native VLC
 *   - JavaFX MediaPlayer — built into JDK but limited format support
 *   - GStreamer Java bindings
 *
 * For now this is a placeholder that simulates playback so the rest of the
 * UI can be tested on desktop.
 */
@Composable
actual fun PlatformPlayerSurface(
    url: String,
    modifier: Modifier,
    isPlaying: Boolean,
    onPositionUpdate: (Long) -> Unit,
    onDurationUpdate: (Long) -> Unit,
    onEnded: () -> Unit,
) {
    // Simulate a 90-minute video
    val simulatedDurationMs = 90L * 60 * 1000

    LaunchedEffect(url) {
        onDurationUpdate(simulatedDurationMs)
        var position = 0L
        while (position < simulatedDurationMs) {
            if (isPlaying) {
                position += 1000
                onPositionUpdate(position)
            }
            delay(1000)
        }
        onEnded()
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Playing: $url\n(Desktop simulation — no real player)",
            color = Color.White,
        )
    }
}
