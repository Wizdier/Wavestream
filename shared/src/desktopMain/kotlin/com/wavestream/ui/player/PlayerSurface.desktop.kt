package com.wavestream.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Desktop implementation: no embedded video surface. The desktop player
 * ([DesktopExternalPlayer]) opens the URL in the system media player.
 *
 * The composable renders a small placeholder so the parent layout has
 * something to compose while the external player launches.
 */
@Composable
actual fun rememberPlayerSurface(videoUrl: String, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "Opening in system player…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
