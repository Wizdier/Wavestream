package com.wavestream.ui.player

import androidx.compose.runtime.Composable

/**
 * True if this platform supports rendering video directly inside the app
 * (Android with ExoPlayer). False on platforms where the URL must be
 * opened externally (Desktop).
 */
@Composable
expect fun supportsEmbeddedPlayback(): Boolean
