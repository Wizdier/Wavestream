package com.wizdier.wavestream.data.api

import kotlinx.serialization.Serializable

/**
 * A piece of media surfaced by a provider's search or catalog endpoints.
 * Mirrors CloudStream's SearchResponse with a few Nuvio-flavoured extras
 * (year, ratings, badge).
 */
@Serializable
data class SearchResponse(
    val id: String,
    val name: String,
    val url: String,
    val type: CatalogType = CatalogType.OTHER,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val qualityLabel: String? = null,
    val providerName: String,
    val providerId: String = "",
    val description: String? = null
)

/**
 * A page of catalog results returned by a provider's home page endpoint
 * (trending, popular, etc.).
 */
@Serializable
data class HomePageList(
    val name: String,
    val items: List<SearchResponse>,
    val isHorizontal: Boolean = true
)

@Serializable
data class HomePageResponse(
    val lists: List<HomePageList>
)

/**
 * Detailed information about a single title. Equivalent to CloudStream's
 * LoadResponse. Episodes are grouped per season.
 */
@Serializable
data class LoadResponse(
    val id: String,
    val name: String,
    val url: String,
    val type: CatalogType,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val duration: String? = null,
    val recommendations: List<SearchResponse> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val providerName: String,
    val providerId: String = ""
)

@Serializable
data class Season(
    val id: String,
    val name: String,
    val seasonNumber: Int,
    val posterUrl: String? = null,
    val overview: String? = null
)

@Serializable
data class Episode(
    val id: String,
    val name: String,
    val episode: Int,
    val season: Int = 1,
    val description: String? = null,
    val posterUrl: String? = null,
    val duration: String? = null,
    val airDate: String? = null,
    val rating: Double? = null
)

/**
 * The result of loading streams for an episode / movie.
 */
@Serializable
data class LoadLinksResponse(
    val videos: List<VideoLink>,
    val subtitles: List<SubtitleFile> = emptyList()
)
