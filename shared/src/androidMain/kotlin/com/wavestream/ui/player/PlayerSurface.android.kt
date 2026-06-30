package com.wavestream.ui.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Android implementation: renders an ExoPlayer-backed [PlayerView].
 *
 * The ExoPlayer is created once per [videoUrl] and released when the
 * composable leaves the composition (or the URL changes). Player controls
 * are enabled by default — tap the surface to toggle them.
 */
@Composable
actual fun rememberPlayerSurface(videoUrl: String, modifier: Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) { ExoPlayer.Builder(context).build() }

    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    DisposableEffect(videoUrl) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowFastForwardButton(true)
                setShowRewindButton(true)
                controllerAutoShow = false
            }
        },
        modifier = modifier,
    )
}
