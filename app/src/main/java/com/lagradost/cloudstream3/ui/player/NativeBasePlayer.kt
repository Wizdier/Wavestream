package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.TextureView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp

/**
 * WaveStream: selects which in-app playback engine to use for the main player.
 * "exo" (default) = CS3IPlayer/ExoPlayer, "mpv" = embedded libmpv, "vlc" = embedded libVLC.
 */
object PlayerEngine {
    const val KEY = "player_engine_key"

    const val ENGINE_EXO = "exo"
    const val ENGINE_MPV = "mpv"
    const val ENGINE_VLC = "vlc"

    fun getPreferredEngine(context: Context?): String {
        val ctx = context ?: CloudStreamApp.context ?: return ENGINE_EXO
        return try {
            PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(KEY, ENGINE_EXO) ?: ENGINE_EXO
        } catch (t: Throwable) {
            logError(t)
            ENGINE_EXO
        }
    }

    /** libmpv requires Android 8.0 (API 26) or newer. */
    val isMpvSupported: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O

    /** Creates the preferred player engine, always falling back to ExoPlayer on failure. */
    fun createPlayer(context: Context? = null): IPlayer {
        return try {
            when (getPreferredEngine(context)) {
                ENGINE_MPV -> if (isMpvSupported) MpvPlayer() else CS3IPlayer()
                ENGINE_VLC -> VlcPlayer()
                else -> CS3IPlayer()
            }
        } catch (t: Throwable) {
            // Missing native libs on exotic ABIs etc. — never break playback.
            logError(t)
            CS3IPlayer()
        }
    }
}

/**
 * Shared scaffolding for the embedded MPV and VLC engines.
 *
 * Responsibilities:
 *  - IPlayer event plumbing (PositionEvent loop, StatusEvent, VideoEndedEvent, timestamps)
 *  - Video surface handling via a [TextureView] injected into the Media3 content frame
 *  - Deferred loading: the engine only starts once the surface is available
 *  - A Media3 [NativeMedia3Adapter] so the stock controller UI (time bar, position text,
 *    play/pause state) keeps working exactly like with ExoPlayer
 */
abstract class NativeBasePlayer : IPlayer {

    companion object {
        private const val TAG = "NativePlayer"
        private const val POSITION_UPDATE_MS = 500L
    }

    protected data class LoadRequest(
        val context: Context,
        val url: String,
        val headers: Map<String, String>,
        val startPosition: Long,
        val autoPlay: Boolean,
        val subtitles: Set<SubtitleData>,
        val subtitle: SubtitleData?,
    )

    protected val mainHandler = Handler(Looper.getMainLooper())

    private var eventHandler: ((PlayerEvent) -> Unit)? = null
    private var lastEmittedPosition = 0L
    private var playbackSpeed: Float = 1.0f
    private var subtitleOffsetMs: Long = 0L
    private var timeStamps: List<VideoSkipStamp> = emptyList()
    private var lastTimeStamp: VideoSkipStamp? = null
    private var activeSubtitles: Set<SubtitleData> = emptySet()
    private var preferredSubtitle: SubtitleData? = null

    protected var pendingLoad: LoadRequest? = null
    protected var surfaceReady = false
    protected var videoView: TextureView? = null
    protected var contentFrame: AspectRatioFrameLayout? = null
    protected var lastStatus: CSPlayerLoading = CSPlayerLoading.IsBuffering
    protected var videoWidth = 0
    protected var videoHeight = 0

    /** Media3 adapter so the stock controller UI stays functional. */
    val media3Adapter: NativeMedia3Adapter by lazy {
        NativeMedia3Adapter(this, Looper.getMainLooper())
    }

    /* ---- engine contract, implemented by MpvPlayer / VlcPlayer ---- */

    /** Start the engine with the given request. Surface is guaranteed to be ready. */
    protected abstract fun engineLoad(request: LoadRequest)
    protected abstract fun enginePlay()
    protected abstract fun enginePause()
    protected abstract fun engineSeekTo(positionMs: Long)
    protected abstract fun engineSetSpeed(speed: Float)
    protected abstract fun engineSetSubtitle(subtitle: SubtitleData?)
    protected abstract fun engineRelease()
    protected abstract fun engineIsPlaying(): Boolean
    protected abstract fun engineGetPosition(): Long?
    protected abstract fun engineGetDuration(): Long?

    /** Called by [PlayerView] once the TextureView has been injected into the view tree. */
    fun attachVideoView(view: TextureView, frame: AspectRatioFrameLayout?) {
        videoView = view
        contentFrame = frame
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: android.graphics.SurfaceTexture, width: Int, height: Int
            ) {
                surfaceReady = true
                onSurfaceAvailable(surface, width, height)
                pendingLoad?.let { request ->
                    pendingLoad = null
                    startEngineSafe(request)
                }
            }

            override fun onSurfaceTextureSizeChanged(
                surface: android.graphics.SurfaceTexture, width: Int, height: Int
            ) {
                onSurfaceSizeChanged(width, height)
            }

            override fun onSurfaceTextureDestroyed(
                surface: android.graphics.SurfaceTexture
            ): Boolean {
                surfaceReady = false
                onSurfaceDestroyed()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
        }
        // The texture might already be available (e.g. reattach).
        view.surfaceTexture?.let { st ->
            surfaceReady = true
            onSurfaceAvailable(st, view.width, view.height)
            pendingLoad?.let { request ->
                pendingLoad = null
                startEngineSafe(request)
            }
        }
    }

    protected open fun onSurfaceAvailable(
        surface: android.graphics.SurfaceTexture, width: Int, height: Int
    ) = Unit

    protected open fun onSurfaceSizeChanged(width: Int, height: Int) = Unit
    protected open fun onSurfaceDestroyed() = Unit

    private fun startEngineSafe(request: LoadRequest) {
        try {
            engineLoad(request)
        } catch (t: Throwable) {
            event(ErrorEvent(t))
        }
    }

    /* ---- event helpers ---- */

    protected fun event(event: PlayerEvent) {
        mainHandler.post {
            try {
                eventHandler?.invoke(event)
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    protected fun updateStatus(status: CSPlayerLoading) {
        if (status == lastStatus) return
        val previous = lastStatus
        lastStatus = status
        event(StatusEvent(wasPlaying = previous, isPlaying = status))
        media3Adapter.invalidate()
    }

    protected fun onVideoSizeChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        videoWidth = width
        videoHeight = height
        mainHandler.post {
            try {
                contentFrame?.setAspectRatio(width.toFloat() / height.toFloat())
            } catch (t: Throwable) {
                logError(t)
            }
        }
        event(ResizedEvent(height = height, width = width))
    }

    protected fun onPlaybackEnded() {
        updateStatus(CSPlayerLoading.IsEnded)
        event(VideoEndedEvent())
    }

    /* ---- position loop ---- */

    private val positionRunnable = object : Runnable {
        override fun run() {
            try {
                val position = engineGetPosition() ?: 0L
                val duration = engineGetDuration() ?: 0L
                if (duration > 0) {
                    event(
                        PositionEvent(
                            source = PlayerEventSource.Player,
                            fromMs = lastEmittedPosition,
                            toMs = position,
                            durationMs = duration,
                        )
                    )
                    lastEmittedPosition = position
                    checkTimestamps(position)
                }
                media3Adapter.invalidate()
            } catch (t: Throwable) {
                logError(t)
            }
            mainHandler.postDelayed(this, POSITION_UPDATE_MS)
        }
    }

    protected fun startPositionLoop() {
        mainHandler.removeCallbacks(positionRunnable)
        mainHandler.post(positionRunnable)
    }

    protected fun stopPositionLoop() {
        mainHandler.removeCallbacks(positionRunnable)
    }

    private fun checkTimestamps(positionMs: Long) {
        val current = timeStamps.firstOrNull { stamp ->
            positionMs >= stamp.timestamp.startMs && positionMs < stamp.timestamp.endMs
        }
        if (current != lastTimeStamp) {
            lastTimeStamp = current
            if (current != null) {
                event(TimestampInvokedEvent(current))
            }
        }
    }

    /* ---- IPlayer implementation ---- */

    override fun getPlaybackSpeed(): Float = playbackSpeed

    override fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        try {
            engineSetSpeed(speed)
        } catch (t: Throwable) {
            logError(t)
        }
        media3Adapter.invalidate()
    }

    override fun getIsPlaying(): Boolean = try {
        engineIsPlaying()
    } catch (t: Throwable) {
        false
    }

    override fun getDuration(): Long? = try {
        engineGetDuration()
    } catch (t: Throwable) {
        null
    }

    override fun getPosition(): Long? = try {
        engineGetPosition()
    } catch (t: Throwable) {
        null
    }

    override fun seekTime(time: Long, source: PlayerEventSource) {
        val position = getPosition() ?: return
        seekTo(position + time, source)
    }

    override fun seekTo(time: Long, source: PlayerEventSource) {
        val duration = getDuration() ?: Long.MAX_VALUE
        val target = time.coerceIn(0L, duration)
        try {
            engineSeekTo(target)
        } catch (t: Throwable) {
            logError(t)
        }
        event(
            PositionEvent(
                source = source,
                fromMs = getPosition() ?: 0L,
                toMs = target,
                durationMs = getDuration() ?: 0L,
            )
        )
        media3Adapter.invalidate()
    }

    override fun getSubtitleOffset(): Long = subtitleOffsetMs

    override fun setSubtitleOffset(offset: Long) {
        subtitleOffsetMs = offset
    }

    override fun initCallbacks(
        eventHandler: (PlayerEvent) -> Unit,
        requestedListeningPercentages: List<Int>?,
    ) {
        this.eventHandler = eventHandler
    }

    override fun releaseCallbacks() {
        eventHandler = null
    }

    // Native engines render their own subtitles; the Exo subtitle style doesn't apply.
    override fun updateSubtitleStyle(style: SaveCaptionStyle) = Unit

    override fun saveData() = Unit

    override fun addTimeStamps(timeStamps: List<VideoSkipStamp>) {
        this.timeStamps = timeStamps
    }

    override fun loadPlayer(
        context: Context,
        sameEpisode: Boolean,
        link: ExtractorLink?,
        data: ExtractorUri?,
        startPosition: Long?,
        subtitles: Set<SubtitleData>,
        subtitle: SubtitleData?,
        autoPlay: Boolean?,
        preview: Boolean,
    ) {
        val url = link?.url ?: data?.uri?.toString()
        if (url.isNullOrBlank()) {
            event(ErrorEvent(ErrorLoadingExceptionCompat("No link provided to native player")))
            return
        }
        val headers = try {
            link?.getAllHeaders() ?: emptyMap()
        } catch (t: Throwable) {
            emptyMap()
        }

        activeSubtitles = subtitles
        preferredSubtitle = subtitle

        val request = LoadRequest(
            context = context.applicationContext,
            url = url,
            headers = headers,
            startPosition = startPosition ?: 0L,
            autoPlay = autoPlay ?: true,
            subtitles = subtitles,
            subtitle = subtitle,
        )

        updateStatus(CSPlayerLoading.IsBuffering)
        event(PlayerAttachedEvent(media3Adapter))
        event(RequestAudioFocusEvent())

        if (surfaceReady) {
            startEngineSafe(request)
        } else {
            Log.i(TAG, "Surface not ready, deferring load of $url")
            pendingLoad = request
        }
        startPositionLoop()
    }

    override fun reloadPlayer(context: Context) = Unit

    override fun getPreview(fraction: Float): Bitmap? = null
    override fun hasPreview(): Boolean = false

    override fun setActiveSubtitles(subtitles: Set<SubtitleData>) {
        activeSubtitles = subtitles
    }

    override fun setPreferredSubtitles(subtitle: SubtitleData?): Boolean {
        preferredSubtitle = subtitle
        try {
            engineSetSubtitle(subtitle)
        } catch (t: Throwable) {
            logError(t)
        }
        return false
    }

    override fun getCurrentPreferredSubtitle(): SubtitleData? = preferredSubtitle

    override fun handleEvent(event: CSPlayerEvent, source: PlayerEventSource) {
        try {
            when (event) {
                CSPlayerEvent.Play -> {
                    enginePlay()
                    updateStatus(CSPlayerLoading.IsPlaying)
                    this.event(PlayEvent(source))
                }

                CSPlayerEvent.Pause -> {
                    enginePause()
                    updateStatus(CSPlayerLoading.IsPaused)
                    this.event(PauseEvent(source))
                }

                CSPlayerEvent.PlayPauseToggle -> {
                    if (engineIsPlaying()) {
                        handleEvent(CSPlayerEvent.Pause, source)
                    } else {
                        handleEvent(CSPlayerEvent.Play, source)
                    }
                }

                CSPlayerEvent.SeekForward -> seekTime(30_000L, source)
                CSPlayerEvent.SeekBack -> seekTime(-30_000L, source)
                CSPlayerEvent.Restart -> seekTo(0L, source)

                CSPlayerEvent.NextEpisode -> this.event(EpisodeSeekEvent(offset = 1, source = source))
                CSPlayerEvent.PrevEpisode -> this.event(EpisodeSeekEvent(offset = -1, source = source))

                CSPlayerEvent.SkipCurrentChapter -> {
                    lastTimeStamp?.let { stamp ->
                        if (stamp.skipToNextEpisode) {
                            this.event(EpisodeSeekEvent(offset = 1, source = source))
                        } else {
                            seekTo(stamp.timestamp.endMs, source)
                        }
                        this.event(TimestampSkippedEvent(timestamp = stamp, source = source))
                    }
                }

                CSPlayerEvent.ToggleMute,
                CSPlayerEvent.PlayAsAudio -> Unit // Not supported by native engines yet
            }
        } catch (t: Throwable) {
            this.event(ErrorEvent(t, source))
        }
    }

    override fun onStop() {
        try {
            enginePause()
        } catch (t: Throwable) {
            logError(t)
        }
    }

    override fun onPause() {
        try {
            enginePause()
        } catch (t: Throwable) {
            logError(t)
        }
    }

    override fun onResume(context: Context) = Unit

    override fun release() {
        stopPositionLoop()
        pendingLoad = null
        try {
            engineRelease()
        } catch (t: Throwable) {
            logError(t)
        }
        try {
            media3Adapter.release()
        } catch (t: Throwable) {
            logError(t)
        }
    }

    override fun isActive(): Boolean = surfaceReady || pendingLoad != null

    override fun getVideoTracks(): CurrentTracks = CurrentTracks(
        currentVideoTrack = null,
        currentAudioTrack = null,
        currentTextTracks = emptyList(),
        allVideoTracks = emptyList(),
        allAudioTracks = emptyList(),
        allTextTracks = emptyList(),
    )

    override fun getAspectRatio(): Rational? {
        if (videoWidth <= 0 || videoHeight <= 0) return null
        return Rational(videoWidth, videoHeight)
    }

    override fun setMaxVideoSize(width: Int, height: Int, id: String?) = Unit

    override fun setPreferredAudioTrack(trackLanguage: String?, id: String?, formatIndex: Int?) = Unit

    override fun getSubtitleCues(): List<SubtitleCue> = emptyList()
}

/** Small local exception so we don't depend on the library module's ErrorLoadingException here. */
class ErrorLoadingExceptionCompat(message: String) : Exception(message)
