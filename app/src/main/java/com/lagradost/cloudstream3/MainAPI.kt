@file:Suppress("UNUSED", "unused", "MemberVisibilityCanBePrivate", "DeprecatedCallableReplaceWith")

package com.lagradost.cloudstream3

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  CLOUDSTREAM 3 COMPATIBILITY SHIM
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  WaveStream's CS3 shim — provides stub implementations of the
 *  `com.lagradost.cloudstream3` package so that real CloudStream 3 `.cs3`
 *  plugin files can be loaded at runtime via DexClassLoader.
 *
 *  This file is intentionally ONE FILE so the symbol surface is easy to
 *  audit. It contains every class / interface / function the average CS3
 *  plugin .dex references at the top level:
 *
 *    - MainAPI (abstract provider base class)
 *    - SearchResponse interface + MovieSearchResponse, TvSeriesSearchResponse,
 *      AnimeSearchResponse data classes
 *    - LoadResponse sealed class + MovieLoadResponse, TvSeriesLoadResponse,
 *      AnimeLoadResponse subtypes
 *    - Episode, Actor, ActorData, ActorRole, Score, SubtitleFile
 *    - HomePageList, HomePageResponse, MainPageData, MainPageRequest
 *    - ExtractorLink, ExtractorType
 *    - TvType enum (all 18 values)
 *    - SearchQuality enum
 *    - ErrorLoadingException
 *    - APIHolder singleton with allProviders list
 *    - Top-level constants: USER_AGENT, AllLanguagesName, json, mapper
 *    - Top-level utility functions: fixUrl, base64Decode, etc.
 *
 *  This is a "good enough" shim — method signatures match CS3 3.x so the
 *  .dex can resolve symbols. Network calls are routed through WaveStream's
 *  own OkHttpClient. Iframe / JS-based extractors are NOT supported
 *  (those require CloudStream's full WebView + extractor framework).
 *
 *  When a plugin is loaded:
 *    1. Cs3PluginLoader opens the .cs3 ZIP
 *    2. Extracts manifest.json + classes.dex
 *    3. DexClassLoader loads classes.dex with this shim as the parent
 *    4. Instantiates the Plugin subclass named in manifest.pluginClassName
 *    5. Calls plugin.load(context) → plugin calls registerMainAPI(api)
 *    6. Cs3ProviderAdapter wraps each MainAPI as a WaveStream Provider
 */

// ─────────────────────────────────────────────────────────────────────────────
//  Constants
// ─────────────────────────────────────────────────────────────────────────────

const val AllLanguagesName = "universal"

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

@Suppress("MayBeConstant")
val json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

// Jackson mapper stub — CS3 plugins reference `mapper` as a top-level
// property. We expose a placeholder; full Jackson behavior is not provided.
// Plugins that use Jackson for JSON parsing may fail at runtime — they
// should use kotlinx.serialization (the `json` value above) instead.
object Mapper {
    fun writeValueAsString(obj: Any?): String = obj.toString()

    fun <T> readValue(value: String, clazz: Class<T>): T? = try {
        @Suppress("UNCHECKED_CAST")
        val ser = kotlinx.serialization.serializer(clazz.kotlin)
            as kotlinx.serialization.KSerializer<T>
        com.lagradost.cloudstream3.json.decodeFromString(ser, value)
    } catch (t: Throwable) { null }

    inline fun <reified T> readValue(value: String): T? = try {
        com.lagradost.cloudstream3.json.decodeFromString(value)
    } catch (t: Throwable) { null }
}
val mapper: Mapper = Mapper

// ─────────────────────────────────────────────────────────────────────────────
//  Exceptions
// ─────────────────────────────────────────────────────────────────────────────

class ErrorLoadingException(message: String? = null) : Exception(message)

// ─────────────────────────────────────────────────────────────────────────────
//  TvType enum — every CS3 3.x value
// ─────────────────────────────────────────────────────────────────────────────

enum class TvType(val value: Int?) {
    Movie(1),
    AnimeMovie(2),
    TvSeries(3),
    Cartoon(4),
    Anime(5),
    OVA(6),
    Torrent(7),
    Documentary(8),
    AsianDrama(9),
    Live(10),
    NSFW(11),
    Others(12),
    Music(13),
    AudioBook(14),
    CustomMedia(15),
    Audio(16),
    Podcast(17),
    Video(18);
}

enum class SearchQuality(val value: Int?) {
    Cam(1), CamRip(2), HdCam(3), Telesync(4), WorkPrint(5), Telecine(6),
    HQ(7), HD(8), HDR(9), HDRiso(10), DolbyVision(11), 4K(12), 8K(13),
    BluRay(14), DVD(15), Remux(16), SD(17), Unknown(null)
}

enum class AutoDownloadMode(val value: Int) {
    Disable(0), FilterByLang(1), All(2), NsfwOnly(3)
}

enum class ExtractorType {
    DIRECT, M3U8, DASH, TORRENT, HTTP
}

// ─────────────────────────────────────────────────────────────────────────────
//  Data classes — Score, Actor, ActorData, ActorRole, Episode, SubtitleFile
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class Score(
    val value: Double? = null,
    val votes: Int? = null,
    val starRating: Double? = null,
    val tmdbRating: Double? = null,
    val malRating: Double? = null,
    val imdbRating: Double? = null
)

@Serializable
open class Actor(
    open val name: String? = null,
    open val image: String? = null
)

@Serializable
open class ActorRole(
    open val name: String? = null,
    open val image: String? = null,
    open val roleString: String? = null
)

@Serializable
open class ActorData(
    open val actor: Actor? = null,
    open val role: ActorRole? = null,
    open val roleString: String? = null
)

@Serializable
data class Episode(
    var id: Int? = null,
    var name: String? = null,
    var episode: Int? = null,
    var season: Int? = null,
    var data: String? = null,
    var apiName: String? = null,
    var url: String? = null,
    var posterUrl: String? = null,
    var rating: Int? = null,
    var description: String? = null,
    var date: Long? = null,
    var score: Score? = null,
    var runtime: Int? = null,
    var posterHeaders: Map<String, String>? = null
)

@Serializable
data class SubtitleFile(
    val name: String,
    val url: String,
    val lang: String? = null,
    val mimeType: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
//  AudioFile + IDownloadableMinimum — referenced by utils/ExtractorApi.kt
//  and by .cs3 plugins that ship their own ExtractorLink subclasses.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A separate audio track that can be muxed into a video ExtractorLink
 * (e.g. for multi-language m3u8 streams). Mirrors CS3's `AudioFile`.
 */
@Serializable
data class AudioFile(
    val url: String,
    val name: String,
    val headers: Map<String, String> = mapOf(),
    val duration: Long? = null
)

/**
 * Minimum interface implemented by anything that can be passed to the
 * download manager. [ExtractorLink] (both the [com.lagradost.cloudstream3.ExtractorLink]
 * in this file and [com.lagradost.cloudstream3.utils.ExtractorLink] in
 * `utils/ExtractorApi.kt`) implements this.
 */
interface IDownloadableMinimum {
    val url: String
    val referer: String?
    val headers: Map<String, String>?
}

// ─────────────────────────────────────────────────────────────────────────────
//  Search response types
// ─────────────────────────────────────────────────────────────────────────────

interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    var type: TvType?
    var posterUrl: String?
    var posterHeaders: Map<String, String>?
    var id: Int?
    var quality: SearchQuality?
    var score: Score?
}

@Serializable
data class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
    var year: Int? = null,
    var tags: List<String>? = null,
    var rating: Int? = null,
    var duration: String? = null,
    var recommendations: List<SearchResponse>? = null,
    var actors: List<ActorData>? = null,
    var comingSoon: Boolean = false,
    var technicalName: String? = null,
    var backgroundPosterUrl: String? = null,
    var signLangUrl: String? = null
) : SearchResponse

@Serializable
data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
    var year: Int? = null,
    var episodes: Int? = null,
    var tags: List<String>? = null,
    var rating: Int? = null,
    var duration: String? = null,
    var recommendations: List<SearchResponse>? = null,
    var actors: List<ActorData>? = null,
    var comingSoon: Boolean = false,
    var technicalName: String? = null,
    var backgroundPosterUrl: String? = null,
    var nextEpisode: Episode? = null,
    var prevEpisode: Episode? = null,
    var seasonNames: List<String>? = null
) : SearchResponse

@Serializable
data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
    var year: Int? = null,
    var episodes: Int? = null,
    var dubStatus: List<DubStatus>? = null,
    var tags: List<String>? = null,
    var rating: Int? = null,
    var duration: String? = null,
    var recommendations: List<SearchResponse>? = null,
    var actors: List<ActorData>? = null,
    var comingSoon: Boolean = false,
    var technicalName: String? = null,
    var backgroundPosterUrl: String? = null,
    var nextEpisode: Episode? = null,
    var prevEpisode: Episode? = null,
    var seasonNames: List<String>? = null
) : SearchResponse

enum class DubStatus(value: Int) {
    Dubbed(1), Subbed(2), Both(3)
}

@Serializable
data class TorrentSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = TvType.Torrent,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
    var seeders: Int? = null,
    var leechers: Int? = null,
    var size: String? = null,
    var magnet: String? = null,
    var torrent: String? = null,
    var hash: String? = null,
    var qualityString: String? = null,
    var uploader: String? = null,
    var rating: Int? = null,
    var age: String? = null,
    var type: String? = null,
    var seedersInt: Int? = null,
    var leechersInt: Int? = null
) : SearchResponse

@Serializable
data class SearchResponseList(
    val items: List<SearchResponse>,
    val hasNext: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
//  HomePage types
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class MainPageData(
    val name: String,
    val url: String,
    val horizontalImages: Boolean = false
)

@Serializable
data class MainPageRequest(
    val name: String,
    val data: MainPageData
)

@Serializable
data class HomePageList(
    var name: String,
    var items: List<SearchResponse>,
    var isHorizontalImages: Boolean = true,
    var nextHomePage: HomePageResponse? = null
)

@Serializable
data class HomePageResponse(
    var items: List<HomePageList>,
    var hasNext: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
//  LoadResponse (sealed)
// ─────────────────────────────────────────────────────────────────────────────

sealed class LoadResponse {
    abstract val name: String
    abstract val url: String
    abstract val apiName: String
    abstract var type: TvType?
    abstract var posterUrl: String?
    abstract var posterHeaders: Map<String, String>?
    abstract var backgroundPosterUrl: String?
    abstract var tags: List<String>?
    abstract var duration: String?
    abstract var rating: Int?
    abstract var score: Score?
    abstract var actors: List<ActorData>?
    abstract var recommendations: List<SearchResponse>?
    abstract var comingSoon: Boolean?
    abstract var synopsys: String?
    abstract var posterUrlWithoutAux: String?
}

@Serializable
data class MovieLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType?,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var tags: List<String>? = null,
    override var duration: String? = null,
    override var rating: Int? = null,
    override var score: Score? = null,
    override var actors: List<ActorData>? = null,
    override var recommendations: List<SearchResponse>? = null,
    override var comingSoon: Boolean? = null,
    override var synopsys: String? = null,
    override var posterUrlWithoutAux: String? = null,
    var data: String? = null,
    var year: Int? = null,
    var signLangUrl: String? = null
) : LoadResponse()

@Serializable
data class TvSeriesLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType?,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var tags: List<String>? = null,
    override var duration: String? = null,
    override var rating: Int? = null,
    override var score: Score? = null,
    override var actors: List<ActorData>? = null,
    override var recommendations: List<SearchResponse>? = null,
    override var comingSoon: Boolean? = null,
    override var synopsys: String? = null,
    override var posterUrlWithoutAux: String? = null,
    var episodes: List<Episode> = emptyList(),
    var year: Int? = null,
    var seasonNames: List<String>? = null,
    var nextEpisode: Episode? = null,
    var prevEpisode: Episode? = null
) : LoadResponse()

@Serializable
data class AnimeLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType?,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var tags: List<String>? = null,
    override var duration: String? = null,
    override var rating: Int? = null,
    override var score: Score? = null,
    override var actors: List<ActorData>? = null,
    override var recommendations: List<SearchResponse>? = null,
    override var comingSoon: Boolean? = null,
    override var synopsys: String? = null,
    override var posterUrlWithoutAux: String? = null,
    var episodes: List<Episode> = emptyList(),
    var year: Int? = null,
    var seasonNames: List<String>? = null,
    var nextEpisode: Episode? = null,
    var prevEpisode: Episode? = null,
    var dubStatus: List<DubStatus>? = null
) : LoadResponse()

@Serializable
data class TorrentLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType?,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var tags: List<String>? = null,
    override var duration: String? = null,
    override var rating: Int? = null,
    override var score: Score? = null,
    override var actors: List<ActorData>? = null,
    override var recommendations: List<SearchResponse>? = null,
    override var comingSoon: Boolean? = null,
    override var synopsys: String? = null,
    override var posterUrlWithoutAux: String? = null,
    var torrent: String? = null,
    var magnet: String? = null,
    var seeders: Int? = null,
    var leechers: Int? = null,
    var hash: String? = null,
    var size: String? = null,
    var quality: SearchQuality? = null,
    var qualityString: String? = null,
    var uploader: String? = null,
    var age: String? = null,
    var typeString: String? = null,
    var seedersInt: Int? = null,
    var leechersInt: Int? = null
) : LoadResponse()

// ─────────────────────────────────────────────────────────────────────────────
//  ExtractorLink — what providers return from loadLinks()
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class ExtractorLink(
    val source: String,
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val headers: Map<String, String> = mapOf(),
    val extractorType: ExtractorType = ExtractorType.DIRECT,
    val duration: Long? = null,
    val isM3u8: Boolean = url.contains(".m3u8", ignoreCase = true)
) {
    companion object {
        const val QUALITY_UNKNOWN = -1
        const val QUALITY_360 = 360
        const val QUALITY_480 = 480
        const val QUALITY_720 = 720
        const val QUALITY_1080 = 1080
        const val QUALITY_1440 = 1440
        const val QUALITY_2160 = 2160
    }
}

@Serializable
data class ExtractorSubtitleLink(
    val name: String,
    val url: String,
    val referer: String? = null,
    val mimeType: String? = null,
    val language: String? = null,
    val headers: Map<String, String> = mapOf()
)

// ─────────────────────────────────────────────────────────────────────────────
//  MainAPI — the abstract provider base class
// ─────────────────────────────────────────────────────────────────────────────

/** Tiny JSON shape used by MainAPI.overrideData. */
@Serializable
data class ProvidersInfoJson(
    var name: String = "",
    var url: String = "",
    var credentials: String? = null
)

@Serializable
data class SettingsJson(
    var enableForAutodownload: Boolean = false,
    var enableForDownloads: Boolean = false,
    var enableAutoDownload: AutoDownloadMode = AutoDownloadMode.Disable,
    var autoDownloadLangs: List<String> = listOf(),
    var preferredQuality: Int? = null
)

abstract class MainAPI {
    companion object {
        @Volatile var overrideData: HashMap<String, ProvidersInfoJson>? = null
        var settingsForProvider: SettingsJson = SettingsJson()
    }

    fun init() {
        overrideData?.get(this::class.simpleName)?.let { data -> overrideWithNewData(data) }
    }

    fun overrideWithNewData(data: ProvidersInfoJson) {
        if (!canBeOverridden) return
        this.name = data.name
        if (data.url.isNotBlank() && data.url != "NONE") this.mainUrl = data.url
        this.storedCredentials = data.credentials
    }

    open var name = "NONE"
    open var mainUrl = "NONE"
    open var storedCredentials: String? = null
    open var canBeOverridden: Boolean = true
    open var sequentialMainPage: Boolean = false
    open var supportedTypes: Set<TvType> = setOf()
    open var lang = "en"
    open var mainPage: List<MainPageData> = listOf()
    open var hasQuickSearch: Boolean = false
    open var hasMainPage: Boolean = false
    open var hasChromecastSupport: Boolean = false
    open var hasDownloadSupport: Boolean = false
    open var enabled: Boolean = true
    open var requiresResources: Boolean = false
    open var sourcePlugin: String? = null

    // ── Abstract provider surface ──────────────────────────────────────────

    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null

    /**
     * Browse a single main page and return only the items (no pagination
     * metadata). Some newer CS3 plugins override this instead of
     * [getMainPage] for simpler paging.
     */
    open suspend fun getMainPageItems(
        page: Int,
        request: MainPageRequest
    ): List<SearchResponse>? = null

    /**
     * Get a single sub-page (used for category browsing, e.g. when a
     * `MainPageData.url` points at a sub-listing).
     */
    open suspend fun getSub(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? = null

    open suspend fun search(query: String): List<SearchResponse>? = null

    /**
     * Newer CS3 search overload that also receives a [FilterList] (provider-
     * specific search filters such as genre / year / sort). Defaults to
     * calling the no-filter [search] overload for backward compatibility.
     */
    open suspend fun search(
        query: String,
        filter: FilterList?
    ): List<SearchResponse>? = search(query)

    open suspend fun quickSearch(query: String): List<SearchResponse>? = null

    open suspend fun load(url: String): LoadResponse? = null

    /**
     * Newer CS3 `load` overload that also receives subtitle / extractor
     * callbacks. Plugins that override this can emit subtitles and links
     * directly from `load()` without needing a separate `loadLinks()` call.
     * Defaults to the no-callback [load] for backward compatibility.
     */
    open suspend fun load(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): LoadResponse? = load(url)

    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    open suspend fun extractLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = loadLinks(data, isCasting, subtitleCallback, callback)

    // Builder helpers — CS3 plugins call these to construct responses.
    fun newMovieSearchResponse(
        name: String,
        url: String,
        fix: Boolean = true,
        initializer: MovieSearchResponse.() -> Unit = {}
    ): MovieSearchResponse = MovieSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name
    ).apply(initializer)

    fun newTvSeriesSearchResponse(
        name: String,
        url: String,
        fix: Boolean = true,
        initializer: TvSeriesSearchResponse.() -> Unit = {}
    ): TvSeriesSearchResponse = TvSeriesSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name
    ).apply(initializer)

    fun newAnimeSearchResponse(
        name: String,
        url: String,
        fix: Boolean = true,
        initializer: AnimeSearchResponse.() -> Unit = {}
    ): AnimeSearchResponse = AnimeSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name
    ).apply(initializer)

    fun newMovieLoadResponse(
        name: String,
        url: String,
        type: TvType,
        data: String,
        fix: Boolean = true,
        initializer: MovieLoadResponse.() -> Unit = {}
    ): MovieLoadResponse = MovieLoadResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
        data = if (fix) fixUrl(data) else data
    ).apply(initializer)

    fun newTvSeriesLoadResponse(
        name: String,
        url: String,
        type: TvType,
        episodes: List<Episode>,
        fix: Boolean = true,
        initializer: TvSeriesLoadResponse.() -> Unit = {}
    ): TvSeriesLoadResponse = TvSeriesLoadResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
        episodes = episodes
    ).apply(initializer)

    fun newAnimeLoadResponse(
        name: String,
        url: String,
        type: TvType,
        fix: Boolean = true,
        initializer: AnimeLoadResponse.() -> Unit = {}
    ): AnimeLoadResponse = AnimeLoadResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type
    ).apply(initializer)

    fun newHomePageResponse(
        name: String,
        items: List<SearchResponse>,
        hasNext: Boolean = false,
        initializer: HomePageResponse.() -> Unit = {}
    ): HomePageResponse = HomePageResponse(
        items = listOf(HomePageList(name = name, items = items)),
        hasNext = hasNext
    ).apply(initializer)

    fun newEpisode(
        episode: Int? = null,
        initializer: Episode.() -> Unit = {}
    ): Episode = Episode(episode = episode, apiName = this.name).apply(initializer)

    fun newMainPageData(
        name: String,
        url: String,
        horizontalImages: Boolean = false
    ): MainPageData = MainPageData(name = name, url = url, horizontalImages = horizontalImages)

    // ── Utilities ─────────────────────────────────────────────────────────

    fun newExtractorLink(
        source: String,
        name: String,
        url: String,
        initializer: ExtractorLink.() -> Unit = {}
    ): ExtractorLink = ExtractorLink(
        source = source,
        name = name,
        url = url,
        referer = mainUrl,
        quality = ExtractorLink.QUALITY_UNKNOWN
    ).apply(initializer)

    fun newSubtitleFile(
        name: String,
        url: String,
        initializer: SubtitleFile.() -> Unit = {}
    ): SubtitleFile = SubtitleFile(name = name, url = url).apply(initializer)
}

// ─────────────────────────────────────────────────────────────────────────────
//  APIHolder — registry of loaded providers
// ─────────────────────────────────────────────────────────────────────────────

object APIHolder {
    val unixTimeMS: Long get() = System.currentTimeMillis()
    val unixTime: Long get() = unixTimeMS / 1000L

    val allProviders: CopyOnWriteArrayList<MainAPI> = CopyOnWriteArrayList()
    private val pluginMapping: ConcurrentHashMap<String, MainAPI> = ConcurrentHashMap()

    fun initAll() {
        for (api in allProviders) api.init()
    }

    fun addPluginMapping(element: MainAPI) {
        pluginMapping[element.name] = element
    }

    fun getApiFromName(name: String): MainAPI? = pluginMapping[name] ?: allProviders.firstOrNull { it.name == name }
    fun getApiFromUrlFast(url: String): MainAPI? = allProviders.firstOrNull { url.startsWith(it.mainUrl) }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top-level utility functions
//  CS3 plugins call these directly (they're defined at the package level)
// ─────────────────────────────────────────────────────────────────────────────

/** Resolve a possibly-relative URL against the provider's mainUrl. */
fun MainAPI.fixUrl(url: String): String {
    if (url.isEmpty()) return url
    return try {
        when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "${mainUrl.trimEnd('/')}$url"
            else -> "${mainUrl.trimEnd('/')}/$url"
        }
    } catch (e: Throwable) {
        url
    }
}

/** Standalone fixUrl — CS3 plugins sometimes call this without a MainAPI receiver. */
fun fixUrl(url: String, baseUrl: String = ""): String {
    if (url.isEmpty()) return url
    return try {
        when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") && baseUrl.isNotEmpty() -> "${baseUrl.trimEnd('/')}$url"
            baseUrl.isNotEmpty() -> "${baseUrl.trimEnd('/')}/$url"
            else -> url
        }
    } catch (e: Throwable) {
        url
    }
}

fun base64Decode(string: String): String {
    return try {
        String(java.util.Base64.getDecoder().decode(string))
    } catch (e: Throwable) {
        string
    }
}

fun base64Encode(string: String): String {
    return try {
        java.util.Base64.getEncoder().encodeToString(string.toByteArray())
    } catch (e: Throwable) {
        string
    }
}

fun getProperJsoup() = org.jsoup.Jsoup

// ─────────────────────────────────────────────────────────────────────────────
//  Network — the `app` object CS3 plugins use for HTTP
//
//  CS3 plugins call `app.get("url").text`, `app.post("url", data=...)`, etc.
//  Routed through WaveStream's shared OkHttpClient so cookies / TLS / timeouts
//  are uniform.
// ─────────────────────────────────────────────────────────────────────────────

object app {
    private val client: OkHttpClient
        get() = com.wizdier.wavestream.data.network.NetworkModule.client

    fun get(
        url: String,
        headers: Map<String, String> = mapOf(),
        allowRedirects: Boolean = true,
        params: Map<String, String> = mapOf()
    ): ResponseWrapper = request("GET", url, headers, allowRedirects, params, null)

    fun post(
        url: String,
        headers: Map<String, String> = mapOf(),
        allowRedirects: Boolean = true,
        params: Map<String, String> = mapOf(),
        data: String? = null,
        json: String? = null
    ): ResponseWrapper = request("POST", url, headers, allowRedirects, params, data ?: json)

    fun head(
        url: String,
        headers: Map<String, String> = mapOf(),
        allowRedirects: Boolean = true
    ): ResponseWrapper = request("HEAD", url, headers, allowRedirects, mapOf(), null)

    private fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        allowRedirects: Boolean,
        params: Map<String, String>,
        body: String?
    ): ResponseWrapper {
        val actualUrl = if (params.isNotEmpty()) {
            val sep = if (url.contains("?")) "&" else "?"
            url + sep + params.entries.joinToString("&") {
                "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
            }
        } else url

        val builder = Request.Builder()
            .url(actualUrl)
            .header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (body != null) {
            builder.method(
                method,
                body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            )
        } else {
            builder.method(method, null)
        }

        val resp = client.newCall(builder.build()).execute()
        return ResponseWrapper(resp)
    }
}

/** Wraps an OkHttp Response with the convenience properties CS3 plugins expect. */
class ResponseWrapper(private val response: Response) : AutoCloseable {
    val text: String
        get() = response.body?.string() ?: ""
    val body: String get() = text
    val code: Int get() = response.code
    val isSuccessful: Boolean get() = response.isSuccessful
    val headers: Map<String, String>
        get() = response.headers.names().associateWith { response.header(it).orEmpty() }
    val url: String get() = response.request.url.toString()

    fun header(name: String): String? = response.header(name)

    override fun close() = response.close()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Annotations
// ─────────────────────────────────────────────────────────────────────────────

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SkipSerializationTest

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class Prerelease

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class InternalAPI

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class UnsafeSSL
