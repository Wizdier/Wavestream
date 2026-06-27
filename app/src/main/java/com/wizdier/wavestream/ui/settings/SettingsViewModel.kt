package com.wizdier.wavestream.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizdier.wavestream.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository
) : ViewModel() {

    // Theme
    val dynamicColor: StateFlow<Boolean> = repo.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { repo.setDynamicColor(v) }

    val themeMode: StateFlow<Int> = repo.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun setThemeMode(v: Int) = viewModelScope.launch { repo.setThemeMode(v) }

    // Player
    val swipeGestures: StateFlow<Boolean> = repo.swipeGestures.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setSwipeGestures(v: Boolean) = viewModelScope.launch { repo.setSwipeGestures(v) }

    val autoPip: StateFlow<Boolean> = repo.autoPip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setAutoPip(v: Boolean) = viewModelScope.launch { repo.setAutoPip(v) }

    val skipIntro: StateFlow<Boolean> = repo.skipIntro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setSkipIntro(v: Boolean) = viewModelScope.launch { repo.setSkipIntro(v) }

    val autoPlayNext: StateFlow<Boolean> = repo.autoPlayNext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setAutoPlayNext(v: Boolean) = viewModelScope.launch { repo.setAutoPlayNext(v) }

    val preloadNext: StateFlow<Boolean> = repo.preloadNext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setPreloadNext(v: Boolean) = viewModelScope.launch { repo.setPreloadNext(v) }

    val playbackSpeed: StateFlow<Float> = repo.defaultPlaybackSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)
    fun setPlaybackSpeed(v: Float) = viewModelScope.launch { repo.setPlaybackSpeed(v) }

    val resizeMode: StateFlow<Int> = repo.resizeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun setResizeMode(v: Int) = viewModelScope.launch { repo.setResizeMode(v) }

    // Subtitles
    val subtitleLang: StateFlow<String> = repo.preferredSubtitleLang.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")
    fun setSubtitleLang(v: String) = viewModelScope.launch { repo.setPreferredSubtitleLang(v) }

    val subtitleSize: StateFlow<Int> = repo.subtitleSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16)
    fun setSubtitleSize(v: Int) = viewModelScope.launch { repo.setSubtitleSize(v) }

    // Library
    val autoDownloadNew: StateFlow<Boolean> = repo.autoDownloadNewEpisodes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setAutoDownloadNew(v: Boolean) = viewModelScope.launch { repo.setAutoDownloadNewEpisodes(v) }

    // Network
    val enableNsfw: StateFlow<Boolean> = repo.enableNsfw.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setEnableNsfw(v: Boolean) = viewModelScope.launch { repo.setEnableNsfw(v) }

    // Sync
    val traktToken: StateFlow<String> = repo.traktToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun setTraktToken(v: String) = viewModelScope.launch { repo.setTraktToken(v) }

    val malToken: StateFlow<String> = repo.malToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun setMalToken(v: String) = viewModelScope.launch { repo.setMalToken(v) }
}
