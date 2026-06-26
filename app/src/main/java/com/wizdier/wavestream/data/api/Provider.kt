package com.wizdier.wavestream.data.api

/**
 * Marker for a WaveStream provider plugin. Provider plugins implement this
 * interface and are loaded by [com.wizdier.wavestream.data.plugin.PluginLoader]
 * either from the local classpath (built-in) or from an installed extension
 * APK (multi-repo support, CloudStream-style).
 *
 * Designed to mirror CloudStream's `MainAPI` surface but with a smaller,
 * coroutine-first signature. Implementers only need to override [search]
 * and [load]; the rest are optional.
 */
interface Provider {

    /** Reverse-DNS identifier — must be unique across all installed providers. */
    val id: String

    /** Human-readable name shown in the source picker and settings. */
    val name: String

    /** Catalogue types this provider can serve. Used by the unified search UI. */
    val supportedTypes: Set<CatalogType>

    /** Language codes (ISO 639-1) this provider serves content in. */
    val languages: Set<String> get() = setOf("en")

    /** Whether the provider requires network access. Always true for HTTP providers. */
    val requiresNetwork: Boolean get() = true

    /**
     * Return the homepage lists (Trending / Popular / etc.) shown on the Home
     * tab. Default: empty — provider opts in if it can.
     */
    suspend fun getMainPage(page: Int = 1): HomePageResponse? = null

    /**
     * Search the provider for [query]. The optional [filter] carries the
     * advanced-search constraints (year, type, genre, source).
     */
    suspend fun search(query: String, filter: SearchFilter = SearchFilter()): List<SearchResponse>

    /**
     * Quick suggestions for the search box as the user types.
     */
    suspend fun quickSearch(query: String): List<SearchResponse> = emptyList()

    /**
     * Load the full detail (incl. episodes for series) for a given [url].
     */
    suspend fun load(url: String): LoadResponse?

    /**
     * Resolve playable streams for a given [url] (episode or movie page).
     */
    suspend fun loadLinks(url: String): LoadLinksResponse
}

/**
 * Advanced search filter surfaced by Nuvio's redesigned search experience.
 * Carries type, year range, genre list and an optional provider id. The
 * [com.wizdier.wavestream.ui.search.SearchScreen] builds this from the
 * filter sheet and dispatches it to every installed provider.
 */
data class SearchFilter(
    val types: Set<CatalogType> = emptySet(),
    val yearStart: Int? = null,
    val yearEnd: Int? = null,
    val genres: Set<String> = emptySet(),
    val providerIds: Set<String> = emptySet()
)
