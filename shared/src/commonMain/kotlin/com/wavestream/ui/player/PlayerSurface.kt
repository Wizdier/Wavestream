package com.wavestream.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific player surface. On Android this renders an ExoPlayer
 * [PlayerView]; on desktop it renders nothing (the desktop player opens
 * the URL externally).
 *
 * Called by the Android [PlayerScreen] to actually display video.
 */
@Composable
expect fun rememberPlayerSurface(videoUrl: String, modifier: Modifier): Unit
