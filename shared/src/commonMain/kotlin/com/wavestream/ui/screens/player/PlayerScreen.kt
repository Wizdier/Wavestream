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
import com.wavestream.ui.player.loadVideoLinks
import com.wavestream.ui.player.rememberPlayerSurface
import com.wavestream.ui.player.supportsEmbeddedPlayback

/**
 * Player screen. Implements the full CloudStream playback pipeline:
 *
 * 1. Call [loadVideoLinks] on the provider to extract real stream URLs
 *    from the `data` parameter returned by `MainAPI.load()`.
 * 2. Pick the best-quality link.
 * 3. Hand the URL to the platform player:
 *    - Android: ExoPlayer via [rememberPlayerSurface] (supports M3U8,
 *      DASH, and direct video with headers/referer).
 *    - Desktop: opens the URL in the system media player.
 *
 * The screen handles loading, error (with retry + external fallback),
 * and lifecycle (releases the player on exit).
 *
 * @param apiName The provider name (e.g. "Cineplex BD") — used to look up
 *        the MainAPI instance that will extract links.
 * @param data The data string from LoadResponse (movie.dataUrl or
 *        Episode.data) — passed to MainAPI.loadLinks() as the `data`
 *        parameter.
 */
@Composable
fun PlayerScreen(
    apiName: String,
    data: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var videoUrl by remember { mutableStateOf("") }
    var attempt by remember { mutableStateOf(0) }
    val player = LocalVideoPlayer.current

    // Step 1: Load links from the provider.
    // Re-runs when apiName/data/attempt changes.
    LaunchedEffect(apiName, data, attempt) {
        if (data.isBlank()) {
            error = "No playback data was provided."
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        videoUrl = ""

        val result = loadVideoLinks(apiName, data)
        if (result == null || result.bestUrl.isBlank()) {
            error = "Failed to extract playable links from '$apiName'. " +
                "The provider may be offline, the content may have been removed, " +
                "or the site's extractor may need updating."
            loading = false
            return@LaunchedEffect
        }

        videoUrl = result.bestUrl
        loading = false

        // Also notify the platform player (for lifecycle management on desktop)
        runCatching {
            player.play(videoUrl) { /* ok ignored — we render the surface separately */ }
        }
    }

    // Release the platform player when leaving the screen.
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

        when {
            loading -> LoadingIndicator(message = "Extracting stream from '$apiName'…")
            error != null -> PlayerErrorState(
                error = error!!,
                onRetry = { attempt++ },
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
            videoUrl.isNotBlank() && supportsEmbeddedPlayback() -> {
                // Android: render ExoPlayer surface
                rememberPlayerSurface(
                    videoUrl = videoUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            videoUrl.isNotBlank() -> {
                // Desktop: show the URL and offer to open externally
                PlayerIdleState(
                    videoUrl = videoUrl,
                    apiName = apiName,
                    onOpenExternal = {
                        runCatching {
                            val cls = Class.forName("com.wavestream.ui.player.DesktopExternalPlayer")
                            val ctor = cls.getDeclaredConstructor()
                            ctor.isAccessible = true
                            val instance = ctor.newInstance() as WaveVideoPlayer
                            instance.play(videoUrl) {}
                        }
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun PlayerErrorState(
    error: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
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
            OutlinedButton(onClick = onBack) { Text("Go back") }
        }
    }
}

@Composable
private fun PlayerIdleState(
    videoUrl: String,
    apiName: String,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
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
            text = "Ready to play",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Source: $apiName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = videoUrl.take(80) + if (videoUrl.length > 80) "…" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        OutlinedButton(onClick = onOpenExternal) {
            Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Open in system player")
        }
    }
}
