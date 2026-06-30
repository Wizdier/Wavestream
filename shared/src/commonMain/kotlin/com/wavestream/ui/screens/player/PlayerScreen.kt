package com.wavestream.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wavestream.ui.components.LoadingIndicator
import com.wavestream.ui.player.LocalVideoPlayer
import com.wavestream.ui.player.WaveVideoPlayer
import com.wavestream.ui.player.rememberPlayerSurface
import com.wavestream.ui.player.supportsEmbeddedPlayback

/**
 * Player screen. The actual rendering is delegated to a platform-specific
 * video player via [LocalVideoPlayer] — on Android this wraps ExoPlayer,
 * on desktop it falls back to opening the URL in the system media player
 * since Java doesn't ship a video decoder.
 *
 * The screen handles:
 *  - Back-button dismissal
 *  - Loading spinner while the platform player is buffering
 *  - Error state with retry + open-in-browser fallback
 *  - Player controls overlay (back, status)
 *  - DisposableEffect to release the player when the screen exits
 */
@Composable
fun PlayerScreen(
    videoUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val player = LocalVideoPlayer.current

    // (Re)start playback when the URL or attempt counter changes.
    LaunchedEffect(videoUrl, attempt) {
        if (videoUrl.isBlank()) {
            error = "No playback URL was provided."
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            player.play(videoUrl) { ok ->
                if (!ok) error = "Failed to play this stream. The URL may be invalid, expired, or unsupported."
                loading = false
            }
        } catch (e: Throwable) {
            error = e.message ?: "Unknown playback error"
            loading = false
        }
    }

    // Release the player when leaving the screen.
    DisposableEffect(Unit) {
        onDispose { player.stop() }
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
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.5f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        // On supported platforms (Android), render the embedded ExoPlayer surface
        // so the user actually sees video. The WaveVideoPlayer interface is
        // still used for lifecycle (stop/release), but rendering goes through
        // rememberPlayerSurface.
        if (supportsEmbeddedPlayback() && videoUrl.isNotBlank() && error == null) {
            rememberPlayerSurface(
                videoUrl = videoUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            when {
                loading -> LoadingIndicator(message = "Preparing playback…")
                error != null -> PlayerErrorState(
                    error = error!!,
                    videoUrl = videoUrl,
                    onRetry = { attempt++ },
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> PlayerIdleState(
                    videoUrl = videoUrl,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun PlayerErrorState(
    error: String,
    videoUrl: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = "Playback failed",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Retry")
            }
            OutlinedButton(onClick = onBack) {
                Text("Go back")
            }
        }
        Spacer(Modifier.size(32.dp))
        // Fallback: open the URL in the system browser / media player.
        // Useful when the in-app player can't handle the codec.
        OutlinedButton(
            onClick = {
                val externalPlayer = com.wavestream.ui.player.NoopVideoPlayer
                // Use the desktop external player if available, otherwise no-op
                runCatching {
                    val cls = Class.forName("com.wavestream.ui.player.DesktopExternalPlayer")
                    val ctor = cls.getDeclaredConstructor()
                    ctor.isAccessible = true
                    val instance = ctor.newInstance() as WaveVideoPlayer
                    instance.play(videoUrl) {}
                }
            },
        ) {
            Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Open in external player")
        }
    }
}

@Composable
private fun PlayerIdleState(
    videoUrl: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = "Now playing",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = videoUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = "If you don't see video, your platform may not have a built-in decoder. " +
                "Try the 'Open in external player' option when an error appears.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
