package com.wavestream.api

import kotlinx.serialization.Serializable

/**
 * Search response hierarchy — mirrors CloudStream's SearchResponse tree.
 *
 * When a provider's `search()` is called, it returns a list of SearchResponse objects.
 * Each subclass carries type-specific metadata (year for movies, dub status for anime, etc.).
 *
 * Builder factories (newMovieSearchResponse etc.) are in MainAPI.kt and should be used
 * instead of direct construction — this lets the data classes evolve without breaking
 * compiled extensions.
 */
interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    var type: TvType?
    var posterUrl: String?
    var posterHeaders: Map<String, String>?
    var id: Int?
    var quality: SearchQuality?
}

@Serializable
data class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = TvType.Movie,
    override var posterUrl: String? = null,
    var year: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
) : SearchResponse

@Serializable
data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = TvType.TvSeries,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var episodeCount: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
) : SearchResponse

@Serializable
data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = TvType.Anime,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var dubStatus: MutableSet<DubStatus> = mutableSetOf(),
    var otherName: String? = null,
    var episodes: MutableMap<DubStatus, Int> = mutableMapOf(),
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
) : SearchResponse

@Serializable
data class LiveSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = TvType.Live,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
) : SearchResponse

@Serializable
data class TorrentSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = TvType.Torrent,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
) : SearchResponse

// ============================================================================
// LoadResponse hierarchy
// ============================================================================

interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var year: Int?
    var plot: String?
    var score: Float?
    var tags: List<String>?
    var duration: Int?  // in minutes
    var recommendations: List<SearchResponse>?
    var actors: List<ActorData>?
    var comingSoon: Boolean
    var syncData: MutableMap<String, String>
    var posterHeaders: Map<String, String>?
    var backgroundPosterUrl: String?
    var logoUrl: String?
    var contentRating: String?
    var uniqueUrl: String

    companion object {
        fun LoadResponse.isMovie(): Boolean = type.isMovieType() || this is MovieLoadResponse
        fun LoadResponse.isEpisodeBased(): Boolean = type.isEpisodeBased()
    }
}

@Serializable
data class Actor(
    val name: String,
    val image: String? = null,
)

@Serializable
data class ActorData(
    val actor: Actor,
    val role: ActorRole? = null,
    val roleString: String? = null,
    val voiceActor: Actor? = null,
)

@Serializable
enum class ActorRole { Main, Supporting, Background }

@Serializable
data class TrailerData(
    val url: String,
    val referer: String? = null,
    val addRaw: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class NextAiring(
    val episode: Int,
    val unixTime: Long,
    val season: Int? = null,
)

@Serializable
data class SeasonData(
    val season: Int,
    val name: String? = null,
    val displaySeason: Int? = null,
)

interface EpisodeResponse {
    var showStatus: ShowStatus?
    var nextAiring: NextAiring?
    var seasonNames: List<SeasonData>?
    fun getLatestEpisodes(): Map<DubStatus, Int?>
    fun getTotalEpisodeIndex(episode: Int, season: Int): Int
}

@Serializable
data class MovieLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Movie,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var score: Float? = null,
    var data: String? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url,
) : LoadResponse

@Serializable
data class Episode(
    var data: String,
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null,
    var score: Float? = null,
    var description: String? = null,
    var date: Long? = null,
    var runTime: Int? = null,
)

@Serializable
data class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.TvSeries,
    var episodes: List<Episode> = emptyList(),
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var score: Float? = null,
    override var showStatus: ShowStatus? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url,
) : LoadResponse, EpisodeResponse {

    override fun getLatestEpisodes(): Map<DubStatus, Int?> {
        val maxSeason = episodes.maxOfOrNull { it.season ?: Int.MIN_VALUE }
            ?.takeUnless { it == Int.MIN_VALUE }
        val max = episodes
            .filter { it.season == maxSeason }
            .maxOfOrNull { it.episode ?: Int.MIN_VALUE }
            ?.takeUnless { it == Int.MIN_VALUE }
        return mapOf(DubStatus.None to max)
    }

    override fun getTotalEpisodeIndex(episode: Int, season: Int): Int {
        val displayMap = seasonNames?.associate { it.season to it.displaySeason } ?: emptyMap()
        return episodes.count { ep ->
            val epSeason = displayMap[ep.season] ?: ep.season ?: Int.MIN_VALUE
            epSeason in 1..<season
        } + episode
    }
}

@Serializable
data class AnimeLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Anime,
    var episodes: MutableMap<DubStatus, List<Episode>> = mutableMapOf(),
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var score: Float? = null,
    override var showStatus: ShowStatus? = null,
    var otherName: String? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url,
) : LoadResponse, EpisodeResponse {

    override fun getLatestEpisodes(): Map<DubStatus, Int?> {
        return episodes.mapValues { (_, eps) ->
            eps.maxOfOrNull { it.episode ?: Int.MIN_VALUE }?.takeUnless { it == Int.MIN_VALUE }
        }
    }

    override fun getTotalEpisodeIndex(episode: Int, season: Int): Int {
        val displayMap = seasonNames?.associate { it.season to it.displaySeason } ?: emptyMap()
        val allEps = episodes.values.flatten()
        return allEps.count { ep ->
            val epSeason = displayMap[ep.season] ?: ep.season ?: Int.MIN_VALUE
            epSeason in 1..<season
        } + episode
    }
}

// ============================================================================
// SubtitleFile + ExtractorLink (the output of loadLinks)
// ============================================================================

@Serializable
data class SubtitleFile(
    var lang: String,
    var url: String,
    var headers: Map<String, String>? = null,
) {
    val langTag: String? get() = lang  // TODO: proper IETF tag conversion
}

@Serializable
data class ExtractorLink(
    val source: String,
    val name: String,
    val url: String,
    var referer: String,
    var quality: Int,
    var headers: Map<String, String> = emptyMap(),
    var extractorData: String? = null,
    var type: ExtractorLinkType,
    var audioTracks: List<String> = emptyList(),
) {
    val isM3u8: Boolean get() = type == ExtractorLinkType.M3U8
    val isDash: Boolean get() = type == ExtractorLinkType.DASH

    fun getAllHeaders(): Map<String, String> {
        return if (referer.isBlank()) {
            headers
        } else if (headers.keys.none { it.equals("referer", ignoreCase = true) }) {
            headers + mapOf("referer" to referer)
        } else {
            headers
        }
    }
}

// ============================================================================
// Homepage models
// ============================================================================

@Serializable
data class MainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false,
)

@Serializable
data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean,
)

@Serializable
data class HomePageList(
    val name: String,
    val list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false,
)

@Serializable
data class HomePageResponse(
    val items: List<HomePageList>,
    val hasNext: Boolean = false,
)

@Serializable
data class SearchResponseList(
    val items: List<SearchResponse>,
    val hasNext: Boolean = false,
)
