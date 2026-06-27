package com.wizdier.wavestream.ui.player

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.wizdier.wavestream.ui.components.LoadingState
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * Nuvio-style fullscreen player surface. Lifecycle-aware: pauses on STOP,
 * resumes on START, releases the ExoPlayer on dispose. CloudStream-style
 * double-tap left/right to skip ±10s, with a brief toast-style hint.
 */
@Composable
fun PlayerScreen(
    providerId: String,
    url: String,
    onBack: () -> Unit,
    onPlayerReady: (ExoPlayer) -> Unit,
    viewModel: PlayerViewModel = koinViewModel()
) {
    val links by viewModel.links.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var skipHint by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(providerId, url) { viewModel.load(providerId, url) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            isLoading -> {
                LoadingState()
                return@Box
            }
            error != null -> {
                Text(
                    text = error ?: "Playback error",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                return@Box
            }
            selected == null -> {
                Text(
                    text = "No playable stream found",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                return@Box
            }
        }

        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val player = remember {
            ExoPlayer.Builder(context).build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        // Hook for skip-intro / next-episode triggers later.
                    }
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        // Could be used to switch aspect-ratio on PiP.
                    }
                })
            }
        }

        // Report the player up to PlayerActivity so PiP can consult it.
        // The player is released in the lifecycle DisposableEffect below.
        DisposableEffect(player) {
            onPlayerReady(player)
            onDispose { }
        }

        // Lifecycle: pause on STOP, resume on START, always release on dispose.
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> player.pause()
                    Lifecycle.Event.ON_START -> {
                        if (!player.isPlaying && player.playbackState == Player.STATE_READY) {
                            player.play()
                        }
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                player.release()
            }
        }

        // Periodic progress reporting to history (every 5s while playing).
        LaunchedEffect(player, selected) {
            val link = selected ?: return@LaunchedEffect
            val mediaItem = viewModel.buildMediaItem(link, link.name)
            player.setMediaItem(mediaItem)
            player.prepare()

            // ── Resume playback ──────────────────────────────────────────
            // Look up the last watch position for this title from the history
            // repository and seek to it before playback starts. CloudStream-
            // style: skip the resume prompt and just seek silently — the user
            // can always tap "Restart" in the player controls.
            val resumeMs = viewModel.getResumePosition(providerId, url)
            if (resumeMs > 5_000) {
                // Wait until the player is ready, then seek.
                kotlinx.coroutines.withTimeoutOrNull(10_000) {
                    while (player.playbackState != Player.STATE_READY) {
                        kotlinx.coroutines.delay(100)
                    }
                }
                player.seekTo(resumeMs)
            }

            while (true) {
                delay(5_000)
                if (player.duration > 0) {
                    viewModel.reportProgress(
                        providerId = providerId,
                        url = url,
                        title = link.name,
                        pos = player.currentPosition,
                        dur = player.duration
                    )
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false  // We use our custom Compose overlay
                    this.player = player
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )

        // Custom Compose controls overlay — auto-hides after 4s, has
        // play/pause/skip/seek/speed/subtitle/settings buttons.
        PlayerControlsOverlay(
            player = player,
            title = selected?.name ?: "WaveStream",
            isVisible = controlsVisible,
            onToggleVisibility = { controlsVisible = !controlsVisible },
            onBack = onBack
        )

        // Gesture overlay — Nuvio-style: left/right thirds skip ±10s on double-tap.
        // Single tap toggles the controls overlay.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val width = size.width
                            val x = offset.x
                            val newPos = when {
                                x < width / 3f -> {
                                    skipHint = "⏪ 10s" to System.currentTimeMillis()
                                    (player.currentPosition - 10_000).coerceAtLeast(0)
                                }
                                x > 2 * width / 3f -> {
                                    skipHint = "⏩ 10s" to System.currentTimeMillis()
                                    player.currentPosition + 10_000
                                }
                                else -> return@detectTapGestures
                            }
                            player.seekTo(newPos)
                        }
                    )
                }
        )

        // Top-left back button (always visible)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Close",
                tint = Color.White
            )
        }

        // Skip hint flash
        skipHint?.let { (text, ts) ->
            LaunchedEffect(ts) {
                delay(700)
                skipHint = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(text = text, color = Color.White)
            }
        }
    }
}
