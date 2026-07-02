package com.lagradost.cloudstream3.ui.player

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import com.lagradost.cloudstream3.mvvm.logError
import dev.jdtech.mpv.MPVLib

/**
 * WaveStream: fully in-app MPV playback engine built on libmpv
 * (dev.jdtech.mpv:libmpv — the same library used by Findroid/mpvKt).
 *
 * Selected via Settings → Player → Playback engine. No external app needed.
 *
 * Note: MPVLib is a singleton native context, so only one MpvPlayer may be
 * active at a time — which matches how CloudStream uses its player.
 */
class MpvPlayer : NativeBasePlayer(), MPVLib.EventObserver {

    companion object {
        private const val TAG = "WaveMpvPlayer"

        /** Only allow a single native mpv context. */
        @Volatile
        private var activeInstance: MpvPlayer? = null
    }

    private var created = false
    private var surface: Surface? = null
    private var loadedUrl: String? = null
    private var playing = false
    private var endedEmitted = false

    private var cachedDurationMs: Long = 0L
    private var cachedPositionMs: Long = 0L
    private var pendingWidth = 0
    private var pendingHeight = 0

    /* ---- surface plumbing ---- */

    override fun onSurfaceAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        this.surface = Surface(surface)
        if (created) {
            try {
                MPVLib.attachSurface(this.surface!!)
                MPVLib.setOptionString("force-window", "yes")
                MPVLib.setPropertyString("vo", "gpu")
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (created) {
            try {
                MPVLib.setPropertyString(
                    "android-surface-size", "${width}x${height}"
                )
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    override fun onSurfaceDestroyed() {
        if (created) {
            try {
                MPVLib.setPropertyString("vo", "null")
                MPVLib.setOptionString("force-window", "no")
                MPVLib.detachSurface()
            } catch (t: Throwable) {
                logError(t)
            }
        }
        surface?.release()
        surface = null
    }

    /* ---- engine implementation ---- */

    override fun engineLoad(request: LoadRequest) {
        // Kill any previous instance owning the native context.
        activeInstance?.let { previous ->
            if (previous !== this) previous.destroyNative()
        }
        activeInstance = this

        if (!created) {
            MPVLib.create(request.context)

            // Sensible defaults for streaming
            MPVLib.setOptionString("profile", "fast")
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("opengl-es", "yes")
            MPVLib.setOptionString("hwdec", "mediacodec-copy")
            MPVLib.setOptionString("ao", "audiotrack,opensles")
            MPVLib.setOptionString("tls-verify", "no")
            MPVLib.setOptionString("cache", "yes")
            MPVLib.setOptionString("cache-secs", "30")
            MPVLib.setOptionString("demuxer-max-bytes", "32MiB")
            MPVLib.setOptionString("demuxer-max-back-bytes", "32MiB")
            MPVLib.setOptionString("sub-scale-with-window", "yes")
            MPVLib.setOptionString("keep-open", "yes")
            MPVLib.setOptionString("save-position-on-quit", "no")
            MPVLib.setOptionString("force-window", "no")
            // No config dir / scripts — keep it hermetic inside the app.
            MPVLib.setOptionString("config", "no")
            MPVLib.setOptionString("input-default-bindings", "no")

            MPVLib.init()
            created = true

            MPVLib.addObserver(this)
            MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_INT64)
            MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_INT64)
            MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("paused-for-cache", MPVLib.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("eof-reached", MPVLib.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("video-params/w", MPVLib.MPV_FORMAT_INT64)
            MPVLib.observeProperty("video-params/h", MPVLib.MPV_FORMAT_INT64)
        }

        surface?.let { s ->
            try {
                MPVLib.attachSurface(s)
                MPVLib.setOptionString("force-window", "yes")
            } catch (t: Throwable) {
                logError(t)
            }
        }

        // HTTP headers
        val headerString = request.headers.entries.joinToString(",") { (k, v) -> "$k: $v" }
        if (headerString.isNotBlank()) {
            MPVLib.setOptionString("http-header-fields", headerString)
        }
        request.headers["User-Agent"]?.let { MPVLib.setOptionString("user-agent", it) }

        endedEmitted = false
        loadedUrl = request.url

        // The start option must be set before loadfile for it to apply.
        if (request.startPosition > 0) {
            MPVLib.setOptionString("start", "+${request.startPosition / 1000}")
        } else {
            MPVLib.setOptionString("start", "+0")
        }
        MPVLib.command(arrayOf("loadfile", request.url))

        MPVLib.setPropertyBoolean("pause", !request.autoPlay)
        playing = request.autoPlay

        // External subtitle
        request.subtitle?.let { sub ->
            try {
                MPVLib.command(arrayOf("sub-add", sub.url, "select", sub.name))
            } catch (t: Throwable) {
                logError(t)
            }
        }
        updateStatus(CSPlayerLoading.IsBuffering)
    }

    override fun enginePlay() {
        if (!created) return
        playing = true
        MPVLib.setPropertyBoolean("pause", false)
    }

    override fun enginePause() {
        if (!created) return
        playing = false
        MPVLib.setPropertyBoolean("pause", true)
    }

    override fun engineSeekTo(positionMs: Long) {
        if (!created) return
        MPVLib.command(arrayOf("seek", (positionMs / 1000.0).toString(), "absolute"))
    }

    override fun engineSetSpeed(speed: Float) {
        if (!created) return
        MPVLib.setPropertyDouble("speed", speed.toDouble())
    }

    override fun engineSetSubtitle(subtitle: SubtitleData?) {
        if (!created) return
        if (subtitle == null) {
            MPVLib.setPropertyString("sid", "no")
        } else {
            MPVLib.command(arrayOf("sub-add", subtitle.url, "select", subtitle.name))
        }
    }

    override fun engineIsPlaying(): Boolean {
        if (!created) return false
        return playing && MPVLib.getPropertyBoolean("pause") != true
    }

    override fun engineGetPosition(): Long? {
        if (!created) return null
        return cachedPositionMs
    }

    override fun engineGetDuration(): Long? {
        if (!created) return null
        return cachedDurationMs
    }

    override fun engineRelease() {
        destroyNative()
    }

    private fun destroyNative() {
        if (!created) return
        created = false
        try {
            MPVLib.removeObserver(this)
        } catch (t: Throwable) {
            logError(t)
        }
        try {
            MPVLib.command(arrayOf("stop"))
            MPVLib.detachSurface()
        } catch (t: Throwable) {
            logError(t)
        }
        try {
            MPVLib.destroy()
        } catch (t: Throwable) {
            logError(t)
        }
        if (activeInstance === this) activeInstance = null
        surface?.release()
        surface = null
        Log.i(TAG, "mpv released")
    }

    /* ---- MPVLib.EventObserver ---- */

    override fun eventProperty(property: String) = Unit

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "time-pos" -> cachedPositionMs = value * 1000L
            "duration" -> cachedDurationMs = value * 1000L
            "video-params/w" -> {
                pendingWidth = value.toInt()
                if (pendingWidth > 0 && pendingHeight > 0)
                    onVideoSizeChanged(pendingWidth, pendingHeight)
            }
            "video-params/h" -> {
                pendingHeight = value.toInt()
                if (pendingWidth > 0 && pendingHeight > 0)
                    onVideoSizeChanged(pendingWidth, pendingHeight)
            }
        }
    }

    override fun eventProperty(property: String, value: Double) = Unit

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> {
                playing = !value
                updateStatus(if (value) CSPlayerLoading.IsPaused else CSPlayerLoading.IsPlaying)
            }

            "paused-for-cache" -> {
                if (value) updateStatus(CSPlayerLoading.IsBuffering)
                else updateStatus(
                    if (engineIsPlaying()) CSPlayerLoading.IsPlaying
                    else CSPlayerLoading.IsPaused
                )
            }

            "eof-reached" -> {
                if (value && !endedEmitted) {
                    endedEmitted = true
                    onPlaybackEnded()
                }
            }
        }
    }

    override fun eventProperty(property: String, value: String) = Unit

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MPV_EVENT_FILE_LOADED -> {
                try {
                    cachedDurationMs = (MPVLib.getPropertyInt("duration") ?: 0) * 1000L
                    val w = MPVLib.getPropertyInt("video-params/w") ?: 0
                    val h = MPVLib.getPropertyInt("video-params/h") ?: 0
                    if (w > 0 && h > 0) onVideoSizeChanged(w, h)
                } catch (t: Throwable) {
                    logError(t)
                }
                updateStatus(
                    if (engineIsPlaying()) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused
                )
            }

            MPVLib.MPV_EVENT_PLAYBACK_RESTART -> updateStatus(
                if (engineIsPlaying()) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused
            )
        }
    }
}
