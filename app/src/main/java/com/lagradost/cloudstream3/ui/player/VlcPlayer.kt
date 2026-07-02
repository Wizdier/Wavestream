package com.lagradost.cloudstream3.ui.player

import android.graphics.SurfaceTexture
import android.net.Uri
import android.util.Log
import com.lagradost.cloudstream3.mvvm.logError
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

/**
 * WaveStream: fully in-app VLC playback engine built on libVLC
 * (org.videolan.android:libvlc-all — the official VideoLAN library).
 *
 * Selected via Settings → Player → Playback engine. No external app needed.
 */
class VlcPlayer : NativeBasePlayer() {

    companion object {
        private const val TAG = "WaveVlcPlayer"
    }

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var endedEmitted = false

    private var cachedDurationMs: Long = 0L
    private var cachedPositionMs: Long = 0L

    /* ---- surface plumbing ---- */

    override fun onSurfaceAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceTexture = surface
        attachVout(width, height)
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        try {
            mediaPlayer?.getVLCVout()?.setWindowSize(width, height)
        } catch (t: Throwable) {
            logError(t)
        }
    }

    override fun onSurfaceDestroyed() {
        try {
            mediaPlayer?.getVLCVout()?.detachViews()
        } catch (t: Throwable) {
            logError(t)
        }
        surfaceTexture = null
    }

    private fun attachVout(width: Int, height: Int) {
        val player = mediaPlayer ?: return
        val texture = surfaceTexture ?: return
        try {
            val vout = player.getVLCVout()
            if (!vout.areViewsAttached()) {
                vout.setVideoSurface(texture)
                if (width > 0 && height > 0) vout.setWindowSize(width, height)
                vout.attachViews()
            }
        } catch (t: Throwable) {
            logError(t)
        }
    }

    /* ---- engine implementation ---- */

    override fun engineLoad(request: LoadRequest) {
        // Recreate cleanly for each load.
        destroyNative()

        val options = arrayListOf(
            "--no-sub-autodetect-file",
            "--audio-time-stretch",
            "--avcodec-skiploopfilter", "1",
            "--network-caching=3000",
        )
        val vlc = LibVLC(request.context, options)
        libVlc = vlc

        val player = MediaPlayer(vlc)
        mediaPlayer = player
        endedEmitted = false

        player.setEventListener { ev ->
            try {
                when (ev.type) {
                    MediaPlayer.Event.Playing -> updateStatus(CSPlayerLoading.IsPlaying)
                    MediaPlayer.Event.Paused -> updateStatus(CSPlayerLoading.IsPaused)
                    MediaPlayer.Event.Buffering -> {
                        if (ev.buffering < 100f) updateStatus(CSPlayerLoading.IsBuffering)
                        else updateStatus(
                            if (player.isPlaying) CSPlayerLoading.IsPlaying
                            else CSPlayerLoading.IsPaused
                        )
                    }

                    MediaPlayer.Event.TimeChanged -> cachedPositionMs = ev.timeChanged
                    MediaPlayer.Event.LengthChanged -> cachedDurationMs = ev.lengthChanged

                    MediaPlayer.Event.Vout -> {
                        if (ev.voutCount > 0) {
                            val track = player.currentVideoTrack
                            if (track != null && track.width > 0 && track.height > 0) {
                                onVideoSizeChanged(track.width, track.height)
                            }
                        }
                    }

                    MediaPlayer.Event.EndReached -> {
                        if (!endedEmitted) {
                            endedEmitted = true
                            onPlaybackEnded()
                        }
                    }

                    MediaPlayer.Event.EncounteredError -> {
                        event(ErrorEvent(ErrorLoadingExceptionCompat("VLC playback error")))
                    }
                }
            } catch (t: Throwable) {
                logError(t)
            }
        }

        val media = Media(vlc, Uri.parse(request.url))
        media.setHWDecoderEnabled(true, false)

        // HTTP headers: libVLC only supports UA + referer as options.
        request.headers.forEach { (key, value) ->
            when (key.lowercase()) {
                "user-agent" -> media.addOption(":http-user-agent=$value")
                "referer", "referrer" -> media.addOption(":http-referrer=$value")
            }
        }
        if (request.startPosition > 0) {
            media.addOption(":start-time=${request.startPosition / 1000}")
        }

        player.setMedia(media)
        media.release()

        // Attach video output before playing.
        attachVout(videoView?.width ?: 0, videoView?.height ?: 0)

        // External subtitle as a slave track.
        request.subtitle?.let { sub ->
            try {
                player.addSlave(IMedia.Slave.Type.Subtitle, Uri.parse(sub.url), true)
            } catch (t: Throwable) {
                logError(t)
            }
        }

        if (request.autoPlay) {
            player.play()
        }
        updateStatus(CSPlayerLoading.IsBuffering)
        Log.i(TAG, "libVLC ${LibVLC.version()} loading ${request.url}")
    }

    override fun enginePlay() {
        mediaPlayer?.play()
    }

    override fun enginePause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) player.pause()
    }

    override fun engineSeekTo(positionMs: Long) {
        mediaPlayer?.setTime(positionMs)
        cachedPositionMs = positionMs
    }

    override fun engineSetSpeed(speed: Float) {
        mediaPlayer?.rate = speed
    }

    override fun engineSetSubtitle(subtitle: SubtitleData?) {
        val player = mediaPlayer ?: return
        if (subtitle == null) {
            player.spuTrack = -1
        } else {
            player.addSlave(IMedia.Slave.Type.Subtitle, Uri.parse(subtitle.url), true)
        }
    }

    override fun engineIsPlaying(): Boolean = mediaPlayer?.isPlaying == true

    override fun engineGetPosition(): Long? {
        val time = mediaPlayer?.time ?: return cachedPositionMs
        if (time >= 0) cachedPositionMs = time
        return cachedPositionMs
    }

    override fun engineGetDuration(): Long? {
        val length = mediaPlayer?.length ?: return cachedDurationMs
        if (length > 0) cachedDurationMs = length
        return cachedDurationMs
    }

    override fun engineRelease() {
        destroyNative()
    }

    private fun destroyNative() {
        try {
            mediaPlayer?.setEventListener(null)
            mediaPlayer?.stop()
            mediaPlayer?.getVLCVout()?.detachViews()
            mediaPlayer?.release()
        } catch (t: Throwable) {
            logError(t)
        }
        mediaPlayer = null
        try {
            libVlc?.release()
        } catch (t: Throwable) {
            logError(t)
        }
        libVlc = null
    }
}
