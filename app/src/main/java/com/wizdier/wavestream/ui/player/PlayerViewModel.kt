package com.wizdier.wavestream.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.wizdier.wavestream.data.api.LoadLinksResponse
import com.wizdier.wavestream.data.api.SubtitleFile
import com.wizdier.wavestream.data.api.VideoLink
import com.wizdier.wavestream.data.repository.HistoryRepository
import com.wizdier.wavestream.data.repository.ProviderRepository
import com.wizdier.wavestream.data.repository.SubtitleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val providerRepo: ProviderRepository,
    private val historyRepo: HistoryRepository,
    private val subtitleRepo: SubtitleRepository
) : ViewModel() {

    private val _links = MutableStateFlow<LoadLinksResponse?>(null)
    val links: StateFlow<LoadLinksResponse?> = _links.asStateFlow()

    private val _selected = MutableStateFlow<VideoLink?>(null)
    val selected: StateFlow<VideoLink?> = _selected.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _subtitles = MutableStateFlow<List<SubtitleFile>>(emptyList())
    val subtitles: StateFlow<List<SubtitleFile>> = _subtitles.asStateFlow()

    fun load(providerId: String, url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { providerRepo.loadLinks(providerId, url) }
                .onSuccess {
                    _links.value = it
                    _subtitles.value = it.subtitles
                    _selected.value = it.videos.firstOrNull()
                    _error.value = if (it.videos.isEmpty()) "No playable streams found." else null
                }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun selectVideo(link: VideoLink) { _selected.value = link }

    fun buildMediaItem(link: VideoLink, title: String): MediaItem {
        val subtitleConfigs = _subtitles.value.map {
            androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(it.url))
                .setMimeType(it.format.mime)
                .setLanguage(it.lang)
                .build()
        }
        return MediaItem.Builder()
            .setUri(link.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(link.name)
                    .build()
            )
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
    }

    fun reportProgress(providerId: String, url: String, title: String, pos: Long, dur: Long) {
        viewModelScope.launch {
            historyRepo.upsert(
                itemId = HistoryRepository.composeItemId(providerId, url),
                providerId = providerId,
                title = title,
                posterUrl = null,
                backdropUrl = null,
                url = url,
                type = com.wizdier.wavestream.data.api.CatalogType.OTHER,
                season = 1,
                episode = 1,
                progressMs = pos,
                durationMs = dur
            )
        }
    }

    /**
     * Look up the last saved watch position for [providerId]+[url] from the
     * history repository. Returns 0 if there's no history entry — caller
     * should skip the seek in that case.
     */
    suspend fun getResumePosition(providerId: String, url: String): Long {
        val itemId = HistoryRepository.composeItemId(providerId, url)
        val entry = historyRepo.getByItemId(itemId) ?: return 0L
        // Only resume if we're more than 5s in AND not within 10s of the end.
        return if (entry.progressMs > 5_000 &&
            (entry.durationMs == 0L || entry.progressMs < entry.durationMs - 10_000)
        ) entry.progressMs else 0L
    }
}
