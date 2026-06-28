package com.wavestream.features.tmdb

import com.wavestream.core.network.app
import com.wavestream.core.storage.DataStore
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * TMDB API service — provides metadata enrichment.
 * Mirrors NuvioMobile's TmdbMetadataService.
 */
object TmdbService {
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p"
    private const val API_KEY_STORAGE = "tmdb_api_key"

    private val json = Json { ignoreUnknownKeys = true }

    fun getApiKey(): String? = DataStore.getKey(API_KEY_STORAGE, String::class.java)
    fun setApiKey(key: String) { DataStore.setKey(API_KEY_STORAGE, key) }

    fun posterUrl(path: String?, size: String = "w500"): String? =
        if (path == null) null else "$IMAGE_BASE/$size$path"

    fun backdropUrl(path: String?, size: String = "w1280"): String? =
        if (path == null) null else "$IMAGE_BASE/$size$path"

    suspend fun findTmdbId(imdbId: String): Int? {
        val apiKey = getApiKey() ?: return null
        return try {
            val url = "$BASE_URL/find/$imdbId?api_key=$apiKey&external_source=imdb_id"
            val response = app.get(url)
            if (!response.status.isSuccess()) return null
            val data = json.decodeFromString<FindResponse>(response.bodyAsText())
            data.movie_results?.firstOrNull()?.id ?: data.tv_results?.firstOrNull()?.id
        } catch (e: Throwable) { null }
    }

    suspend fun getMovie(tmdbId: Int): TmdbMovie? {
        val apiKey = getApiKey() ?: return null
        return try {
            val url = "$BASE_URL/movie/$tmdbId?api_key=$apiKey&append_to_response=credits,recommendations,videos,images"
            val response = app.get(url)
            if (!response.status.isSuccess()) return null
            json.decodeFromString(response.bodyAsText())
        } catch (e: Throwable) { null }
    }

    suspend fun getTvShow(tmdbId: Int): TmdbTvShow? {
        val apiKey = getApiKey() ?: return null
        return try {
            val url = "$BASE_URL/tv/$tmdbId?api_key=$apiKey&append_to_response=credits,recommendations,videos,images"
            val response = app.get(url)
            if (!response.status.isSuccess()) return null
            json.decodeFromString(response.bodyAsText())
        } catch (e: Throwable) { null }
    }

    suspend fun getSeason(tmdbId: Int, seasonNumber: Int): TmdbSeason? {
        val apiKey = getApiKey() ?: return null
        return try {
            val url = "$BASE_URL/tv/$tmdbId/season/$seasonNumber?api_key=$apiKey"
            val response = app.get(url)
            if (!response.status.isSuccess()) return null
            json.decodeFromString(response.bodyAsText())
        } catch (e: Throwable) { null }
    }

    suspend fun search(query: String, page: Int = 1): TmdbSearchResponse? {
        val apiKey = getApiKey() ?: return null
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/search/multi?api_key=$apiKey&query=$encodedQuery&page=$page"
            val response = app.get(url)
            if (!response.status.isSuccess()) return null
            json.decodeFromString(response.bodyAsText())
        } catch (e: Throwable) { null }
    }
}

@Serializable data class FindResponse(val movie_results: List<TmdbSearchItem>? = null, val tv_results: List<TmdbSearchItem>? = null)
@Serializable data class TmdbSearchItem(val id: Int, val title: String? = null, val name: String? = null, val overview: String? = null, val poster_path: String? = null, val backdrop_path: String? = null, val release_date: String? = null, val first_air_date: String? = null, val vote_average: Double? = null, val media_type: String? = null)
@Serializable data class TmdbSearchResponse(val page: Int = 1, val results: List<TmdbSearchItem> = emptyList(), val total_pages: Int = 1, val total_results: Int = 0)
@Serializable data class TmdbMovie(val id: Int, val title: String, val overview: String? = null, val poster_path: String? = null, val backdrop_path: String? = null, val release_date: String? = null, val runtime: Int? = null, val vote_average: Double? = null, val genres: List<TmdbGenre>? = null, val credits: TmdbCredits? = null, val recommendations: TmdbRecommendations? = null, val videos: TmdbVideos? = null, val images: TmdbImages? = null, val imdb_id: String? = null)
@Serializable data class TmdbTvShow(val id: Int, val name: String, val overview: String? = null, val poster_path: String? = null, val backdrop_path: String? = null, val first_air_date: String? = null, val last_air_date: String? = null, val number_of_seasons: Int? = null, val number_of_episodes: Int? = null, val episode_run_time: List<Int>? = null, val vote_average: Double? = null, val genres: List<TmdbGenre>? = null, val seasons: List<TmdbSeason>? = null, val credits: TmdbCredits? = null, val recommendations: TmdbRecommendations? = null, val videos: TmdbVideos? = null, val images: TmdbImages? = null, val external_ids: TmdbExternalIds? = null)
@Serializable data class TmdbSeason(val id: Int? = null, val season_number: Int? = null, val name: String? = null, val overview: String? = null, val poster_path: String? = null, val air_date: String? = null, val episode_count: Int? = null, val episodes: List<TmdbEpisode>? = null)
@Serializable data class TmdbEpisode(val id: Int? = null, val episode_number: Int? = null, val season_number: Int? = null, val name: String? = null, val overview: String? = null, val still_path: String? = null, val air_date: String? = null, val runtime: Int? = null, val vote_average: Double? = null)
@Serializable data class TmdbGenre(val id: Int, val name: String)
@Serializable data class TmdbCredits(val cast: List<TmdbCast> = emptyList(), val crew: List<TmdbCrew> = emptyList())
@Serializable data class TmdbCast(val id: Int, val name: String, val character: String? = null, val profile_path: String? = null, val order: Int = 0)
@Serializable data class TmdbCrew(val id: Int, val name: String, val job: String? = null, val department: String? = null, val profile_path: String? = null)
@Serializable data class TmdbRecommendations(val results: List<TmdbSearchItem> = emptyList())
@Serializable data class TmdbVideos(val results: List<TmdbVideo> = emptyList())
@Serializable data class TmdbVideo(val id: String, val key: String, val name: String, val site: String, val type: String, val official: Boolean = false, val published_at: String? = null)
@Serializable data class TmdbImages(val backdrops: List<TmdbImage> = emptyList(), val posters: List<TmdbImage> = emptyList(), val logos: List<TmdbImage> = emptyList())
@Serializable data class TmdbImage(val file_path: String, val width: Int? = null, val height: Int? = null, val iso_639_1: String? = null, val vote_average: Double? = null)
@Serializable data class TmdbExternalIds(val imdb_id: String? = null, val tvdb_id: Int? = null, val facebook_id: String? = null, val instagram_id: String? = null, val twitter_id: String? = null)
