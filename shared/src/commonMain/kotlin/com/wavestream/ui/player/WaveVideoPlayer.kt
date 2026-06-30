package com.wavestream.ui.player

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Abstraction over the platform video player. The host platform (Android
 * ExoPlayer / Desktop fallback) supplies an instance via
 * [LocalVideoPlayer].
 */
interface WaveVideoPlayer {
    /**
     * Begin playback of [url]. Invokes [onReady] (true on success, false on
     * failure) exactly once when the player has either started playing or
     * failed. May be called on the main thread.
     */
    fun play(url: String, onReady: (Boolean) -> Unit)

    /** Stop playback and release any platform resources. */
    fun stop()
}

/**
 * Composition local that provides the current [WaveVideoPlayer]. Defaults
 * to [NoopVideoPlayer] so screens can render in previews / tests without
 * a real player.
 */
val LocalVideoPlayer = staticCompositionLocalOf<WaveVideoPlayer> { NoopVideoPlayer }

/** Player that does nothing — used as a fallback on platforms without a decoder. */
object NoopVideoPlayer : WaveVideoPlayer {
    override fun play(url: String, onReady: (Boolean) -> Unit) { onReady(false) }
    override fun stop() {}
}
