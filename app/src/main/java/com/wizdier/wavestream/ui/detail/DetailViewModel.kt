package com.wizdier.wavestream.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizdier.wavestream.data.api.LoadResponse
import com.wizdier.wavestream.data.api.SubtitleFile
import com.wizdier.wavestream.data.api.VideoLink
import com.wizdier.wavestream.data.repository.FavoritesRepository
import com.wizdier.wavestream.data.repository.HistoryRepository
import com.wizdier.wavestream.data.repository.ProviderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val providerRepo: ProviderRepository,
    private val favoritesRepo: FavoritesRepository,
    private val historyRepo: HistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    private val _links = MutableStateFlow<List<VideoLink>>(emptyList())
    val links: StateFlow<List<VideoLink>> = _links.asStateFlow()

    private val _subtitles = MutableStateFlow<List<SubtitleFile>>(emptyList())
    val subtitles: StateFlow<List<SubtitleFile>> = _subtitles.asStateFlow()

    private val _selectedLink = MutableStateFlow<VideoLink?>(null)
    val selectedLink: StateFlow<VideoLink?> = _selectedLink.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private var itemId: String = ""
    private var loaded: LoadResponse? = null
    private var favObserverJob: Job? = null
    private var lastProviderId: String = ""
    private var lastUrl: String = ""

    fun load(providerId: String, url: String) {
        // Skip if we're already loading the same item.
        if (providerId == lastProviderId && url == lastUrl && loaded != null) return
        lastProviderId = providerId
        lastUrl = url

        viewModelScope.launch {
            _state.value = DetailState.Loading
            _links.value = emptyList()
            _subtitles.value = emptyList()
            _selectedLink.value = null

            runCatching { providerRepo.load(providerId, url) }
                .onSuccess { resp ->
                    if (resp == null) {
                        _state.value = DetailState.Error("Could not load title.")
                    } else {
                        loaded = resp
                        itemId = FavoritesRepository.composeItemId(providerId, url)
                        _state.value = DetailState.Loaded(resp)

                        // Cancel any previous favourite observer before starting a new one.
                        favObserverJob?.cancel()
                        favObserverJob = viewModelScope.launch {
                            favoritesRepo.observeIsFavorite(itemId).collect { _isFavorite.value = it }
                        }
                        loadLinks(providerId, url)
                    }
                }
                .onFailure { _state.value = DetailState.Error(it.message ?: "Unknown error") }
        }
    }

    private fun loadLinks(providerId: String, url: String) {
        viewModelScope.launch {
            runCatching { providerRepo.loadLinks(providerId, url) }
                .onSuccess {
                    _links.value = it.videos
                    _subtitles.value = it.subtitles
                    _selectedLink.value = it.videos.firstOrNull()
                }
                .onFailure {
                    // Soft-fail: detail screen still works, just no sources.
                    _links.value = emptyList()
                }
        }
    }

    fun selectLink(link: VideoLink) { _selectedLink.value = link }

    fun toggleFavorite() {
        val resp = loaded ?: return
        viewModelScope.launch {
            runCatching {
                favoritesRepo.toggle(
                    itemId = itemId,
                    providerId = resp.providerId,
                    title = resp.name,
                    posterUrl = resp.posterUrl,
                    backdropUrl = resp.backdropUrl,
                    url = resp.url,
                    type = resp.type
                )
            }
        }
    }

    /**
     * Persist watch progress so the Continue-Watching carousel can resume.
     * Called by the player periodically.
     */
    fun recordProgress(progressMs: Long, durationMs: Long) {
        val resp = loaded ?: return
        if (itemId.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                historyRepo.upsert(
                    itemId = itemId,
                    providerId = resp.providerId,
                    title = resp.name,
                    posterUrl = resp.posterUrl,
                    backdropUrl = resp.backdropUrl,
                    url = resp.url,
                    type = resp.type,
                    season = 1,
                    episode = 1,
                    progressMs = progressMs,
                    durationMs = durationMs
                )
            }
        }
    }
}

sealed interface DetailState {
    data object Loading : DetailState
    data class Loaded(val data: LoadResponse) : DetailState
    data class Error(val message: String) : DetailState
}
