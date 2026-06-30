package com.wavestream.ui.settings

import com.wavestream.platform.wavePlatform

/**
 * Typed access to all persisted app settings. Each property reads/writes
 * [wavePlatform.preferences] under a stable string key — changing the
 * underlying key would invalidate existing installs, so don't.
 *
 * Conventions:
 *  - Boolean defaults use `false` unless a feature is on by default.
 *  - All keys are prefixed with `wavestream.` to avoid collisions with
 *    library keys (which use `PLUGINS_KEY` etc).
 *  - Strings are stored as-is, no JSON encoding.
 */
object WavePreferences {
    // General
    var jsdelivrProxy: Boolean
        get() = wavePlatform.preferences.getBool("wavestream.jsdelivr", false)
        set(v) { wavePlatform.preferences.putBool("wavestream.jsdelivr", v) }

    var autoRescanOnBoot: Boolean
        get() = wavePlatform.preferences.getBool("wavestream.auto_rescan", true)
        set(v) { wavePlatform.preferences.putBool("wavestream.auto_rescan", v) }

    var defaultTab: String
        get() = wavePlatform.preferences.getString("wavestream.default_tab", "home") ?: "home"
        set(v) { wavePlatform.preferences.putString("wavestream.default_tab", v) }

    // Streaming
    var preferStremioStreams: Boolean
        get() = wavePlatform.preferences.getBool("wavestream.prefer_stremio", false)
        set(v) { wavePlatform.preferences.putBool("wavestream.prefer_stremio", v) }

    var defaultQuality: String
        get() = wavePlatform.preferences.getString("wavestream.default_quality", "auto") ?: "auto"
        set(v) { wavePlatform.preferences.putString("wavestream.default_quality", v) }

    var preferHlsOverMp4: Boolean
        get() = wavePlatform.preferences.getBool("wavestream.prefer_hls", true)
        set(v) { wavePlatform.preferences.putBool("wavestream.prefer_hls", v) }

    // Player
    var rememberPlaybackPosition: Boolean
        get() = wavePlatform.preferences.getBool("wavestream.remember_position", true)
        set(v) { wavePlatform.preferences.putBool("wavestream.remember_position", v) }

    var playbackSpeed: Float
        get() = wavePlatform.preferences.getInt("wavestream.playback_speed_x10", 10) / 10f
        set(v) { wavePlatform.preferences.putInt("wavestream.playback_speed_x10", (v * 10).toInt()) }

    var enableGestures: Boolean
        get() = wavePlatform.preferences.getBool("wavestream.player_gestures", true)
        set(v) { wavePlatform.preferences.putBool("wavestream.player_gestures", v) }

    var pictureInPicture: Boolean
        get() = wavePlatform.preferences.getBool("wavestream.pip", true)
        set(v) { wavePlatform.preferences.putBool("wavestream.pip", v) }

    // Subtitles
    var subtitlesEnabled: Boolean
        get() = wavePlatform.preferences.getBool("wavestream.subtitles_enabled", true)
        set(v) { wavePlatform.preferences.putBool("wavestream.subtitles_enabled", v) }

    var subtitleLanguage: String
        get() = wavePlatform.preferences.getString("wavestream.subtitle_lang", "en") ?: "en"
        set(v) { wavePlatform.preferences.putString("wavestream.subtitle_lang", v) }

    var subtitleFontSize: Float
        get() = wavePlatform.preferences.getInt("wavestream.subtitle_size_x10", 16) / 10f
        set(v) { wavePlatform.preferences.putInt("wavestream.subtitle_size_x10", (v * 10).toInt()) }

    // Appearance
    var themeMode: String
        get() = wavePlatform.preferences.getString("wavestream.theme_mode", "dark") ?: "dark"
        set(v) { wavePlatform.preferences.putString("wavestream.theme_mode", v) }

    var posterCardWidth: Int
        get() = wavePlatform.preferences.getInt("wavestream.poster_width", 110)
        set(v) { wavePlatform.preferences.putInt("wavestream.poster_width", v) }

    var showQualityBadges: Boolean
        get() = wavePlatform.preferences.getBool("wavestream.show_badges", true)
        set(v) { wavePlatform.preferences.putBool("wavestream.show_badges", v) }

    // Network
    var requestTimeoutSeconds: Int
        get() = wavePlatform.preferences.getInt("wavestream.timeout_sec", 30)
        set(v) { wavePlatform.preferences.putInt("wavestream.timeout_sec", v) }

    var concurrentRequests: Int
        get() = wavePlatform.preferences.getInt("wavestream.concurrent_req", 8)
        set(v) { wavePlatform.preferences.putInt("wavestream.concurrent_req", v.coerceIn(1, 32)) }

    // Advanced
    var verboseLogging: Boolean
        get() = wavePlatform.preferences.getBool("wavestream.verbose_log", false)
        set(v) { wavePlatform.preferences.putBool("wavestream.verbose_log", v) }
}
