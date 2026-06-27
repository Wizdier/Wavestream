package com.wizdier.wavestream.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "wavestream_settings")

/**
 * Persisted app settings backed by Jetpack DataStore. Every toggle on the
 * Settings screen reads from / writes to this store so user preferences
 * survive across app restarts.
 */
class SettingsRepository(private val context: Context) {

    // ── Theme ──────────────────────────────────────────────────────────

    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { it[DYNAMIC_COLOR] ?: true }
    suspend fun setDynamicColor(value: Boolean) = context.settingsDataStore.edit { it[DYNAMIC_COLOR] = value }

    /** 0 = System, 1 = Light, 2 = Dark */
    val themeMode: Flow<Int> = context.settingsDataStore.data.map { it[THEME_MODE] ?: 0 }
    suspend fun setThemeMode(value: Int) = context.settingsDataStore.edit { it[THEME_MODE] = value }

    // ── Player ─────────────────────────────────────────────────────────

    val swipeGestures: Flow<Boolean> = context.settingsDataStore.data.map { it[SWIPE_GESTURES] ?: true }
    val autoPip: Flow<Boolean> = context.settingsDataStore.data.map { it[AUTO_PIP] ?: true }
    val skipIntro: Flow<Boolean> = context.settingsDataStore.data.map { it[SKIP_INTRO] ?: true }
    val autoPlayNext: Flow<Boolean> = context.settingsDataStore.data.map { it[AUTO_PLAY_NEXT] ?: true }
    val preloadNext: Flow<Boolean> = context.settingsDataStore.data.map { it[PRELOAD_NEXT] ?: true }
    val defaultPlaybackSpeed: Flow<Float> = context.settingsDataStore.data.map { (it[PLAYBACK_SPEED] ?: 100) / 100f }
    val resizeMode: Flow<Int> = context.settingsDataStore.data.map { it[RESIZE_MODE] ?: 0 }

    suspend fun setSwipeGestures(value: Boolean) = context.settingsDataStore.edit { it[SWIPE_GESTURES] = value }
    suspend fun setAutoPip(value: Boolean) = context.settingsDataStore.edit { it[AUTO_PIP] = value }
    suspend fun setSkipIntro(value: Boolean) = context.settingsDataStore.edit { it[SKIP_INTRO] = value }
    suspend fun setAutoPlayNext(value: Boolean) = context.settingsDataStore.edit { it[AUTO_PLAY_NEXT] = value }
    suspend fun setPreloadNext(value: Boolean) = context.settingsDataStore.edit { it[PRELOAD_NEXT] = value }
    suspend fun setPlaybackSpeed(value: Float) {
        context.settingsDataStore.edit { it[PLAYBACK_SPEED] = (value * 100).toInt() }
    }
    suspend fun setResizeMode(value: Int) = context.settingsDataStore.edit { it[RESIZE_MODE] = value }

    // ── Subtitles ──────────────────────────────────────────────────────

    val preferredSubtitleLang: Flow<String> = context.settingsDataStore.data.map { it[SUBTITLE_LANG] ?: "en" }
    val subtitleSize: Flow<Int> = context.settingsDataStore.data.map { it[SUBTITLE_SIZE] ?: 16 }

    suspend fun setPreferredSubtitleLang(value: String) = context.settingsDataStore.edit { it[SUBTITLE_LANG] = value }
    suspend fun setSubtitleSize(value: Int) = context.settingsDataStore.edit { it[SUBTITLE_SIZE] = value }

    // ── Library ────────────────────────────────────────────────────────

    val autoDownloadNewEpisodes: Flow<Boolean> = context.settingsDataStore.data.map { it[AUTO_DOWNLOAD_NEW] ?: false }
    suspend fun setAutoDownloadNewEpisodes(value: Boolean) = context.settingsDataStore.edit { it[AUTO_DOWNLOAD_NEW] = value }

    // ── Network ────────────────────────────────────────────────────────

    val defaultProvider: Flow<String> = context.settingsDataStore.data.map { it[DEFAULT_PROVIDER] ?: "" }
    val enableNsfw: Flow<Boolean> = context.settingsDataStore.data.map { it[ENABLE_NSFW] ?: false }

    suspend fun setDefaultProvider(value: String) = context.settingsDataStore.edit { it[DEFAULT_PROVIDER] = value }
    suspend fun setEnableNsfw(value: Boolean) = context.settingsDataStore.edit { it[ENABLE_NSFW] = value }

    // ── Sync credentials (Trakt + MAL) ─────────────────────────────────

    val traktToken: Flow<String> = context.settingsDataStore.data.map { it[TRAKT_TOKEN] ?: "" }
    val malToken: Flow<String> = context.settingsDataStore.data.map { it[MAL_TOKEN] ?: "" }

    suspend fun setTraktToken(value: String) = context.settingsDataStore.edit { it[TRAKT_TOKEN] = value }
    suspend fun setMalToken(value: String) = context.settingsDataStore.edit { it[MAL_TOKEN] = value }

    companion object {
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val THEME_MODE = intPreferencesKey("theme_mode")  // 0=System, 1=Light, 2=Dark
        private val SWIPE_GESTURES = booleanPreferencesKey("swipe_gestures")
        private val AUTO_PIP = booleanPreferencesKey("auto_pip")
        private val SKIP_INTRO = booleanPreferencesKey("skip_intro")
        private val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        private val PRELOAD_NEXT = booleanPreferencesKey("preload_next")
        private val PLAYBACK_SPEED = intPreferencesKey("playback_speed")
        private val RESIZE_MODE = intPreferencesKey("resize_mode")
        private val SUBTITLE_LANG = stringPreferencesKey("subtitle_lang")
        private val SUBTITLE_SIZE = intPreferencesKey("subtitle_size")
        private val AUTO_DOWNLOAD_NEW = booleanPreferencesKey("auto_download_new")
        private val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        private val ENABLE_NSFW = booleanPreferencesKey("enable_nsfw")
        private val TRAKT_TOKEN = stringPreferencesKey("trakt_token")
        private val MAL_TOKEN = stringPreferencesKey("mal_token")
    }
}
