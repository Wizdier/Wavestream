package com.wavestream.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.wavestream.ui.components.LoadingIndicator
import com.wavestream.ui.player.LocalVideoPlayer

/**
 * Player screen. The actual rendering is delegated to a platform-specific
 * video player via [LocalVideoPlayer] — on Android this wraps ExoPlayer,
 * on desktop it falls back to a simple "open in external player" message
 * since Java doesn't ship a video decoder.
 *
 * The screen handles back-button dismissal and shows a loading spinner
 * while the platform player is buffering.
 */
@Composable
fun PlayerScreen(
    videoUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val player = LocalVideoPlayer.current

    LaunchedEffect(videoUrl) {
        loading = true
        error = null
        try {
            player.play(videoUrl) { ok ->
                if (!ok) error = "Failed to play $videoUrl"
                loading = false
            }
        } catch (e: Throwable) {
            error = e.message ?: "Unknown playback error"
            loading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top-left back button overlay
        IconButton(
            onClick = {
                player.stop()
                onBack()
            },
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        when {
            loading -> LoadingIndicator(message = "Preparing playback…")
            error != null -> Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> Text(
                text = "Now playing: $videoUrl",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
