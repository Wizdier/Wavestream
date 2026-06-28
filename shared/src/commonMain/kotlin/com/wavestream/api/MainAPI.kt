package com.wavestream.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Wavestream provider contract — every CloudStream-style extension implements this.
 *
 * This is the equivalent of CloudStream's `com.lagradost.cloudstream3.MainAPI` class.
 * Extensions compile against this and are loaded at runtime via PathClassLoader (Android)
 * or URLClassLoader (Desktop).
 *
 * Subclasses override `search`, `load`, `loadLinks`, optionally `getMainPage`/`quickSearch`.
 * Builder factories (newMovieLoadResponse etc.) provide forward-compatible construction.
 *
 * Note: Wavestream also supports Stremio addons (see plugins/stremio/) and JS plugins
 * (see plugins/js/), which don't implement MainAPI but produce equivalent data.
 */
abstract class MainAPI {
    open var name: String = "NONE"
    open var mainUrl: String = "NONE"
    open var lang: String = "en"

    open val supportedTypes: Set<TvType> = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Cartoon, TvType.Anime, TvType.OVA,
    )

    open val hasMainPage: Boolean = false
    open val hasQuickSearch: Boolean = false
    open val instantLinkLoading: Boolean = false
    open val hasChromecastSupport: Boolean = true
    open val hasDownloadSupport: Boolean = true
    open val usesWebView: Boolean = false

    open val providerType: ProviderType = ProviderType.DirectProvider
    open val vpnStatus: VPNStatus = VPNStatus.None

    open val mainPage: List<MainPageData> = listOf(MainPageData("", "", false))

    // Timeouts (provider-declared hints, clamped by APIRepository)
    open val loadLinksTimeoutMs: Long? = null
    open val searchTimeoutMs: Long? = null
    open val getMainPageTimeoutMs: Long? = null
    open val quickSearchTimeoutMs: Long? = null
    open val loadTimeoutMs: Long? = null

    /** File path of the plugin this provider was loaded from — set by PluginManager. */
    var sourcePlugin: String? = null

    /** If true, the homepage requests will be made sequentially (to avoid rate limits). */
    open val sequentialMainPage: Boolean = false
    open val sequentialMainPageDelay: Long = 0L
    open val sequentialMainPageScrollDelay: Long = 0L

    /** Used internally to track when the last homepage request was made. */
    var lastHomepageRequest: Long = 0L

    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null

    open suspend fun search(query: String, page: Int): SearchResponseList? {
        val results = search(query) ?: return null
        return SearchResponseList(results, hasNext = false)
    }

    open suspend fun search(query: String): List<SearchResponse>? = null

    open suspend fun quickSearch(query: String): List<SearchResponse>? = null

    /**
     * Based on data from search() or getMainPage() it generates a LoadResponse,
     * basically opening the info page from a link.
     */
    open suspend fun load(url: String): LoadResponse? = null

    /**
     * Callback is fired once a link is found.
     * @param data dataUrl string returned from load() function.
     * @param isCasting true if the link will be played on a Chromecast.
     * @param subtitleCallback called for each subtitle file found.
     * @param callback called for each ExtractorLink found.
     * @return true if the method executed successfully.
     */
    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean = false

    /**
     * Background job that runs while a link is playing.
     * First implemented to do polling for sflix to keep the link from getting expired.
     */
    open suspend fun extractorVerifierJob(extractorData: String?) {}

    /** Get the load() url based on a sync ID like IMDb or MAL. */
    open suspend fun getLoadUrl(name: SyncIdName, id: String): String? = null

    /** An okhttp interceptor for used in OkHttpDataSource (Android only). */
    open fun getVideoInterceptor(extractorLink: ExtractorLink): Any? = null

    /** Fix a relative URL against this provider's mainUrl. */
    fun fixUrl(url: String): String {
        if (url.startsWith("http") || url.startsWith("{\"") || url.startsWith("[")) return url
        if (url.isEmpty()) return ""
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith('/')) return mainUrl + url
        return "$mainUrl/$url"
    }

    fun fixUrlNull(url: String?): String? = url?.let { fixUrl(it) }
}

/** Sync ID names — used by getLoadUrl() to map external IDs (IMDb, MAL) to provider URLs. */
enum class SyncIdName {
    Imdb, Tmdb, Mal, AniList, Kitsu, Simkl, Trakt
}

// ============================================================================
// Builder factories — let data classes evolve without breaking compiled extensions.
// ============================================================================

fun MainAPI.newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = true,
    initializer: MovieSearchResponse.() -> Unit = {},
): MovieSearchResponse {
    val builder = MovieSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder.initializer()
    return builder
}

fun MainAPI.newTvSeriesSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    fix: Boolean = true,
    initializer: TvSeriesSearchResponse.() -> Unit = {},
): TvSeriesSearchResponse {
    val builder = TvSeriesSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder.initializer()
    return builder
}

fun MainAPI.newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = true,
    initializer: AnimeSearchResponse.() -> Unit = {},
): AnimeSearchResponse {
    val builder = AnimeSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder.initializer()
    return builder
}

fun MainAPI.newLiveSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Live,
    fix: Boolean = true,
    initializer: LiveSearchResponse.() -> Unit = {},
): LiveSearchResponse {
    val builder = LiveSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder.initializer()
    return builder
}

fun MainAPI.newTorrentSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Torrent,
    fix: Boolean = true,
    initializer: TorrentSearchResponse.() -> Unit = {},
): TorrentSearchResponse {
    val builder = TorrentSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    builder.initializer()
    return builder
}

fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    data: String? = null,
    initializer: (suspend MovieLoadResponse.() -> Unit)? = null,
): MovieLoadResponse {
    val builder = MovieLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        data = data ?: url,
        comingSoon = data.isNullOrBlank(),
    )
    if (initializer != null) {
        kotlinx.coroutines.runBlocking { builder.initializer() }
    }
    return builder
}

fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    episodes: List<Episode> = emptyList(),
    initializer: (suspend TvSeriesLoadResponse.() -> Unit)? = null,
): TvSeriesLoadResponse {
    val builder = TvSeriesLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        episodes = episodes,
        comingSoon = episodes.isEmpty(),
    )
    if (initializer != null) {
        kotlinx.coroutines.runBlocking { builder.initializer() }
    }
    return builder
}

fun MainAPI.newAnimeLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    initializer: (suspend AnimeLoadResponse.() -> Unit)? = null,
): AnimeLoadResponse {
    val builder = AnimeLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
    )
    if (initializer != null) {
        kotlinx.coroutines.runBlocking { builder.initializer() }
    }
    return builder
}

fun MainAPI.newEpisode(
    url: String,
    initializer: Episode.() -> Unit = {},
    fix: Boolean = true,
): Episode {
    val builder = Episode(data = if (fix) fixUrl(url) else url)
    builder.initializer()
    return builder
}

fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType? = null,
    initializer: ExtractorLink.() -> Unit = {},
): ExtractorLink {
    val resolvedType = type ?: ExtractorLinkType.inferFromUrl(url)
    val link = ExtractorLink(
        source = source,
        name = name,
        url = url,
        referer = "",
        quality = Qualities.Unknown.value,
        type = resolvedType,
    )
    link.initializer()
    return link
}

fun newSubtitleFile(
    lang: String,
    url: String,
    initializer: (suspend SubtitleFile.() -> Unit)? = null,
): SubtitleFile {
    val file = SubtitleFile(lang = lang, url = url)
    if (initializer != null) {
        kotlinx.coroutines.runBlocking { file.initializer() }
    }
    return file
}

// ============================================================================
// SearchResponse / LoadResponse extension helpers
// ============================================================================

fun SearchResponse.addQuality(quality: String) {
    this.quality = getQualityFromString(quality)
}

fun SearchResponse.addPoster(url: String?, headers: Map<String, String>? = null) {
    this.posterUrl = url
    this.posterHeaders = headers
}

fun LoadResponse.addPoster(url: String?, headers: Map<String, String>? = null) {
    this.posterUrl = url
    this.posterHeaders = headers
}

fun LoadResponse.addActors(actors: List<String>?) {
    this.actors = actors?.map { ActorData(Actor(it)) }
}

fun LoadResponse.addActorsWithRoles(actors: List<Pair<Actor, String?>>) {
    this.actors = actors.map { (actor, role) -> ActorData(actor, roleString = role) }
}

fun LoadResponse.addScore(score: Float?, maxValue: Int = 10) {
    this.score = score?.let { (it / maxValue.toFloat()) * 10f }
}

fun LoadResponse.addDuration(input: String?) {
    val cleanInput = input?.trim()?.replace(" ", "") ?: return
    Regex("(\\d+\\shr)|(\\d+\\shour)|(\\d+\\smin)|(\\d+\\ssec)").findAll(input).let { values ->
        var seconds = 0
        values.forEach {
            val timeText = it.value
            if (timeText.isNotBlank()) {
                val time = timeText.filter { s -> s.isDigit() }.trim().toInt()
                val scale = timeText.filter { s -> !s.isDigit() }.trim()
                seconds += when (scale) {
                    "hr", "hour" -> time * 60 * 60
                    "min" -> time * 60
                    "sec" -> time
                    else -> 0
                }
            }
        }
        if (seconds > 0) {
            this.duration = seconds / 60
        }
    }
    Regex("([0-9]*)h.*?([0-9]*)m").find(cleanInput)?.groupValues?.let { values ->
        if (values.size == 3) {
            val hours = values[1].toIntOrNull()
            val minutes = values[2].toIntOrNull()
            if (minutes != null && hours != null) {
                this.duration = hours * 60 + minutes
            }
        }
    }
}

// ============================================================================
// URL helpers
// ============================================================================

fun httpsify(url: String): String {
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> url.replaceFirst("http://", "https://")
        !url.startsWith("https://") && url.contains("://") -> url
        else -> url
    }
}

fun imdbUrlToId(url: String): String? {
    return Regex("/title/(tt[0-9]*)").find(url)?.groupValues?.get(1)
        ?: Regex("tt[0-9]{5,}").find(url)?.groupValues?.get(0)
}

fun imdbUrlToIdNullable(url: String?): String? {
    if (url == null) return null
    return imdbUrlToId(url)
}
