package com.lagradost.cloudstream3.ui.player

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * WaveStream: bridges a [NativeBasePlayer] (MPV/VLC engine) to the Media3 [Player] interface
 * so the stock `androidx.media3.ui.PlayerView` controller — time bar, position/duration text,
 * play/pause button — keeps working exactly as it does with ExoPlayer.
 *
 * Only the small subset of the Player API that the controller uses is mapped.
 */
@OptIn(UnstableApi::class)
class NativeMedia3Adapter(
    private val engine: NativeBasePlayer,
    private val looper: Looper,
) : SimpleBasePlayer(looper) {

    private val handler = android.os.Handler(looper)

    @Volatile
    private var released = false

    /**
     * Ask the controller UI to re-read state (position, duration, playing).
     * Safe to call from any thread — native engine callbacks arrive on their own threads,
     * but [invalidateState] must run on the player looper.
     */
    fun invalidate() {
        if (released) return
        if (Looper.myLooper() == looper) {
            invalidateSafe()
        } else {
            handler.post { invalidateSafe() }
        }
    }

    private fun invalidateSafe() {
        if (released) return
        try {
            invalidateState()
        } catch (_: Throwable) {
            // Ignore invalidation on a dead adapter.
        }
    }

    override fun getState(): State {
        val durationMs = engine.getDuration() ?: 0L
        val positionMs = (engine.getPosition() ?: 0L).coerceAtLeast(0L)
        val isPlaying = engine.getIsPlaying()

        val playbackState = when {
            released -> Player.STATE_IDLE
            durationMs <= 0L -> Player.STATE_BUFFERING
            positionMs >= durationMs && durationMs > 0 -> Player.STATE_ENDED
            else -> Player.STATE_READY
        }

        val mediaItem = MediaItemData.Builder(/* uid = */ "wavestream_native")
            .setMediaItem(MediaItem.EMPTY)
            .setIsSeekable(true)
            .setIsDynamic(false)
            .setDurationUs(if (durationMs > 0) durationMs * 1000L else androidx.media3.common.C.TIME_UNSET)
            .build()

        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_SET_SPEED_AND_PITCH,
                        Player.COMMAND_STOP,
                        Player.COMMAND_RELEASE,
                    )
                    .build()
            )
            .setPlaybackState(playbackState)
            .setPlayWhenReady(isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(listOf(mediaItem))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(positionMs)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        engine.handleEvent(
            if (playWhenReady) CSPlayerEvent.Play else CSPlayerEvent.Pause,
            PlayerEventSource.UI
        )
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        engine.seekTo(positionMs, PlayerEventSource.UI)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(
        playbackParameters: androidx.media3.common.PlaybackParameters
    ): ListenableFuture<*> {
        engine.setPlaybackSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        engine.handleEvent(CSPlayerEvent.Pause, PlayerEventSource.UI)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        return Futures.immediateVoidFuture()
    }

    // The native engine renders into its own TextureView; ignore surface requests
    // coming from the Media3 PlayerView so it doesn't steal or clear the output.
    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> =
        Futures.immediateVoidFuture()

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> =
        Futures.immediateVoidFuture()
}
