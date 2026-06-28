package com.wavestream.features.notifications

import com.wavestream.core.network.app
import com.wavestream.core.storage.DataStore
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Episode release notification — mirrors NuvioMobile's EpisodeReleaseNotificationsRepository.
 */
object EpisodeReleaseNotifications {
    private const val TMDB_BASE = "https://api.themoviedb.org/3"
    private const val STORAGE_KEY = "tracked_shows"
    private const val NOTIFIED_KEY = "notified_episodes"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _trackedShows = MutableStateFlow<List<TrackedShow>>(emptyList())
    val trackedShows: StateFlow<List<TrackedShow>> = _trackedShows.asStateFlow()

    @Serializable
    data class TrackedShow(
        val tmdbId: Int, val name: String, val posterUrl: String?,
        val lastSeason: Int, val lastEpisode: Int,
    )

    @Serializable
    data class NotifiedEpisode(
        val showTmdbId: Int, val season: Int, val episode: Int, val notifiedAt: Long,
    )

    fun trackShow(show: TrackedShow) {
        val current = loadTrackedShows().toMutableList()
        current.removeAll { it.tmdbId == show.tmdbId }
        current.add(show)
        DataStore.setKey(STORAGE_KEY, current)
        _trackedShows.value = current
    }

    fun untrackShow(tmdbId: Int) {
        val current = loadTrackedShows().toMutableList()
        current.removeAll { it.tmdbId == tmdbId }
        DataStore.setKey(STORAGE_KEY, current)
        _trackedShows.value = current
    }

    suspend fun checkForNewEpisodes(tmdbApiKey: String) {
        val tracked = loadTrackedShows()
        val notified = loadNotifiedEpisodes().toMutableList()

        for (show in tracked) {
            try {
                val url = "$TMDB_BASE/tv/${show.tmdbId}?api_key=$tmdbApiKey"
                val response = app.get(url)
                if (!response.status.isSuccess()) continue
                val showData = json.decodeFromString<TmdbShowResponse>(response.bodyAsText())
                val latestSeason = showData.seasons?.maxByOrNull { it.season_number ?: 0 } ?: continue

                val seasonUrl = "$TMDB_BASE/tv/${show.tmdbId}/season/${latestSeason.season_number}?api_key=$tmdbApiKey"
                val seasonResp = app.get(seasonUrl)
                if (!seasonResp.status.isSuccess()) continue
                val seasonData = json.decodeFromString<TmdbSeasonResponse>(seasonResp.bodyAsText())

                val latestAired = seasonData.episodes?.maxByOrNull { it.episode_number ?: 0 } ?: continue

                val alreadyNotified = notified.any {
                    it.showTmdbId == show.tmdbId && it.season == latestSeason.season_number && it.episode == latestAired.episode_number
                }

                if (!alreadyNotified && (latestAired.episode_number ?: 0) > show.lastEpisode) {
                    notifyNewEpisode(show, latestSeason.season_number ?: 1, latestAired.episode_number ?: 1, latestAired.name)
                    notified.add(NotifiedEpisode(show.tmdbId, latestSeason.season_number ?: 1, latestAired.episode_number ?: 1, System.currentTimeMillis()))
                }
            } catch (e: Throwable) { }
        }

        DataStore.setKey(NOTIFIED_KEY, notified)
    }

    private fun notifyNewEpisode(show: TrackedShow, season: Int, episode: Int, episodeTitle: String?) {
        println("[Notifications] New episode: ${show.name} S${season}E${episode} - ${episodeTitle ?: ""}")
    }

    private fun loadTrackedShows(): List<TrackedShow> {
        @Suppress("UNCHECKED_CAST")
        return DataStore.getKey(STORAGE_KEY, List::class.java) as? List<TrackedShow> ?: emptyList()
    }

    private fun loadNotifiedEpisodes(): List<NotifiedEpisode> {
        @Suppress("UNCHECKED_CAST")
        return DataStore.getKey(NOTIFIED_KEY, List::class.java) as? List<NotifiedEpisode> ?: emptyList()
    }
}

@Serializable
private data class TmdbShowResponse(val seasons: List<TmdbSeason>? = null)
@Serializable
private data class TmdbSeason(val season_number: Int? = null, val episode_count: Int? = null)
@Serializable
private data class TmdbSeasonResponse(val episodes: List<TmdbEpisode>? = null)
@Serializable
private data class TmdbEpisode(val episode_number: Int? = null, val name: String? = null, val air_date: String? = null)
