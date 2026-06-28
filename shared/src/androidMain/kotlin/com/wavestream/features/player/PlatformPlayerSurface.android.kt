package com.wavestream.features.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Android implementation of PlatformPlayerSurface — uses ExoPlayer via androidx.media3.
 *
 * Mirrors CloudStream's CS3IPlayer + PlayerView setup.
 *
 * Features (to be added incrementally):
 *   - HLS/DASH/SmoothStreaming support (via media3-exoplayer-hls/dash)
 *   - Custom OkHttpDataSource for headers/referer
 *   - Subtitle rendering (SRT, VTT, ASS via libass on Android)
 *   - Skip intro via AniSkip API
 *   - Preview seek bar
 *   - PiP mode
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
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        onDurationUpdate(duration)
                    } else if (playbackState == Player.STATE_ENDED) {
                        onEnded()
                    }
                }
            })
        }
    }

    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }

    // Position polling
    LaunchedEffect(player) {
        while (true) {
            onPositionUpdate(player.currentPosition)
            kotlinx.coroutines.delay(500)
        }
    }

    DisposableEffect(url) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
            }
        },
    )
}
