package com.wavestream.ui.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Android-specific [WaveVideoPlayer] implementation backed by ExoPlayer.
 *
 * The host activity should call [LocalVideoPlayer provides ExoPlayerVideoPlayer(context)]
 * in its setContent block. Alternatively, the [PlayerSurface] composable below
 * wraps the wiring so screens can drop a player into their hierarchy without
 * manual composition-local plumbing.
 */
class ExoPlayerVideoPlayer(private val context: Context) : WaveVideoPlayer {
    @Volatile private var player: ExoPlayer? = null

    override fun play(url: String, onReady: (Boolean) -> Unit) {
        try {
            player?.release()
            val exo = ExoPlayer.Builder(context).build().apply {
                setHandleAudioBecomingNoisy(true)
                volume = AudioManager.STREAM_MUSIC.toFloat()
            }
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            exo.prepare()
            exo.playWhenReady = true
            player = exo
            onReady(true)
        } catch (e: Throwable) {
            e.printStackTrace()
            onReady(false)
        }
    }

    override fun stop() {
        player?.release()
        player = null
    }
}

/**
 * Composable that wires up an ExoPlayer-backed [PlayerView] and supplies it
 * via [LocalVideoPlayer]. Use as a wrapper around [PlayerScreen] on Android.
 */
@Composable
fun PlayerSurface(
    videoUrl: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(videoUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
    )
}
