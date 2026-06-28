@file:Suppress("UNUSED", "unused", "MemberVisibilityCanBePrivate", "DeprecatedCallableReplaceWith")
package com.lagradost.cloudstream3
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.CopyOnWriteArrayList

const val AllLanguagesName = "universal"
const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
object Mapper { fun writeValueAsString(obj: Any?): String = obj?.toString() ?: "null"; fun <T> readValue(value: String, clazz: Class<T>): T? = null; inline fun <reified T> readValue(value: String): T? = try { json.decodeFromString(value) } catch (t: Throwable) { null } }
val mapper: Mapper = Mapper
class ErrorLoadingException(message: String? = null) : Exception(message)

enum class TvType(val value: Int?) { Movie(1), AnimeMovie(2), TvSeries(3), Cartoon(4), Anime(5), OVA(6), Torrent(7), Documentary(8), AsianDrama(9), Live(10), NSFW(11), Others(12), Music(13), AudioBook(14), CustomMedia(15), Audio(16), Podcast(17), Video(18) }
enum class SearchQuality(val value: Int?) { Cam(1), CamRip(2), HdCam(3), Telesync(4), WorkPrint(5), Telecine(6), HQ(7), HD(8), HDR(9), HDRiso(10), DolbyVision(11), Q4K(12), Q8K(13), BluRay(14), DVD(15), Remux(16), SD(17), Unknown(null) }
enum class ExtractorType { DIRECT, M3U8, DASH, TORRENT, HTTP }

@Serializable data class Score(val value: Double? = null, val votes: Int? = null, val starRating: Double? = null, val tmdbRating: Double? = null, val malRating: Double? = null, val imdbRating: Double? = null)
@Serializable open class Actor(open val name: String? = null, open val image: String? = null)
@Serializable data class Episode(var id: Int? = null, var name: String? = null, var episode: Int? = null, var season: Int? = null, var data: String? = null, var apiName: String? = null, var url: String? = null, var posterUrl: String? = null, var rating: Int? = null, var description: String? = null, var date: Long? = null, var score: Score? = null, var runtime: Int? = null)
@Serializable data class SubtitleFile(val name: String, val url: String, val lang: String? = null, val mimeType: String? = null)

interface SearchResponse { val name: String; val url: String; val apiName: String; var type: TvType?; var posterUrl: String?; var posterHeaders: Map<String, String>?; var id: Int?; var quality: SearchQuality?; var score: Score? }

@Serializable data class MovieSearchResponse(override val name: String, override val url: String, override val apiName: String, override var type: TvType? = null, override var posterUrl: String? = null, override var posterHeaders: Map<String, String>? = null, override var id: Int? = null, override var quality: SearchQuality? = null, override var score: Score? = null, var year: Int? = null, var tags: List<String>? = null, var rating: Int? = null, var duration: String? = null, var recommendations: List<SearchResponse>? = null, var actors: List<ActorData>? = null, var comingSoon: Boolean = false, var backgroundPosterUrl: String? = null) : SearchResponse
@Serializable data class TvSeriesSearchResponse(override val name: String, override val url: String, override val apiName: String, override var type: TvType? = null, override var posterUrl: String? = null, override var posterHeaders: Map<String, String>? = null, override var id: Int? = null, override var quality: SearchQuality? = null, override var score: Score? = null, var year: Int? = null, var episodes: Int? = null, var tags: List<String>? = null, var rating: Int? = null, var duration: String? = null, var recommendations: List<SearchResponse>? = null, var actors: List<ActorData>? = null, var comingSoon: Boolean = false, var backgroundPosterUrl: String? = null, var nextEpisode: Episode? = null, var prevEpisode: Episode? = null, var seasonNames: List<String>? = null) : SearchResponse
@Serializable data class AnimeSearchResponse(override val name: String, override val url: String, override val apiName: String, override var type: TvType? = null, override var posterUrl: String? = null, override var posterHeaders: Map<String, String>? = null, override var id: Int? = null, override var quality: SearchQuality? = null, override var score: Score? = null, var year: Int? = null, var episodes: Int? = null, var tags: List<String>? = null, var rating: Int? = null, var duration: String? = null, var recommendations: List<SearchResponse>? = null, var actors: List<ActorData>? = null, var comingSoon: Boolean = false, var backgroundPosterUrl: String? = null, var nextEpisode: Episode? = null, var prevEpisode: Episode? = null, var seasonNames: List<String>? = null) : SearchResponse
@Serializable data class TorrentSearchResponse(override val name: String, override val url: String, override val apiName: String, override var type: TvType? = TvType.Torrent, override var posterUrl: String? = null, override var posterHeaders: Map<String, String>? = null, override var id: Int? = null, override var quality: SearchQuality? = null, override var score: Score? = null, var seeders: Int? = null, var leechers: Int? = null, var size: String? = null, var magnet: String? = null, var torrent: String? = null, var hash: String? = null, var uploader: String? = null) : SearchResponse

@Serializable data class ActorData(open val actor: Actor? = null, open val role: String? = null)
@Serializable data class HomePageList(var name: String, var items: List<SearchResponse>, var isHorizontalImages: Boolean = true)
@Serializable data class HomePageResponse(var items: List<HomePageList>, var hasNext: Boolean = false)
@Serializable data class MainPageData(val name: String, val url: String, val horizontalImages: Boolean = false)
@Serializable data class MainPageRequest(val name: String, val data: MainPageData)

sealed class LoadResponse { abstract val name: String; abstract val url: String; abstract val apiName: String; abstract var type: TvType?; abstract var posterUrl: String?; abstract var posterHeaders: Map<String, String>?; abstract var backgroundPosterUrl: String?; abstract var tags: List<String>?; abstract var duration: String?; abstract var rating: Int?; abstract var score: Score?; abstract var actors: List<ActorData>?; abstract var recommendations: List<SearchResponse>?; abstract var comingSoon: Boolean?; abstract var synopsys: String? }
@Serializable data class MovieLoadResponse(override val name: String, override val url: String, override val apiName: String, override var type: TvType?, override var posterUrl: String? = null, override var posterHeaders: Map<String, String>? = null, override var backgroundPosterUrl: String? = null, override var tags: List<String>? = null, override var duration: String? = null, override var rating: Int? = null, override var score: Score? = null, override var actors: List<ActorData>? = null, override var recommendations: List<SearchResponse>? = null, override var comingSoon: Boolean? = null, override var synopsys: String? = null, var data: String? = null, var year: Int? = null) : LoadResponse()
@Serializable data class TvSeriesLoadResponse(override val name: String, override val url: String, override val apiName: String, override var type: TvType?, override var posterUrl: String? = null, override var posterHeaders: Map<String, String>? = null, override var backgroundPosterUrl: String? = null, override var tags: List<String>? = null, override var duration: String? = null, override var rating: Int? = null, override var score: Score? = null, override var actors: List<ActorData>? = null, override var recommendations: List<SearchResponse>? = null, override var comingSoon: Boolean? = null, override var synopsys: String? = null, var episodes: List<Episode> = emptyList(), var year: Int? = null, var seasonNames: List<String>? = null, var nextEpisode: Episode? = null, var prevEpisode: Episode? = null) : LoadResponse()
@Serializable data class AnimeLoadResponse(override val name: String, override val url: String, override val apiName: String, override var type: TvType?, override var posterUrl: String? = null, override var posterHeaders: Map<String, String>? = null, override var backgroundPosterUrl: String? = null, override var tags: List<String>? = null, override var duration: String? = null, override var rating: Int? = null, override var score: Score? = null, override var actors: List<ActorData>? = null, override var recommendations: List<SearchResponse>? = null, override var comingSoon: Boolean? = null, override var synopsys: String? = null, var episodes: List<Episode> = emptyList(), var year: Int? = null, var seasonNames: List<String>? = null, var nextEpisode: Episode? = null, var prevEpisode: Episode? = null) : LoadResponse()

@Serializable data class ExtractorLink(val source: String, val name: String, val url: String, val referer: String, val quality: Int, val headers: Map<String, String> = mapOf(), val extractorType: ExtractorType = ExtractorType.DIRECT, val duration: Long? = null) { companion object { const val QUALITY_UNKNOWN = -1; const val QUALITY_360 = 360; const val QUALITY_480 = 480; const val QUALITY_720 = 720; const val QUALITY_1080 = 1080; const val QUALITY_2160 = 2160 } }

@Serializable data class ProvidersInfoJson(var name: String = "", var url: String = "", var credentials: String? = null, var enabled: Boolean = true)
@Serializable data class SettingsJson(var enableForAutodownload: Boolean = false)

abstract class MainAPI {
    open var name = "NONE"; open var mainUrl = "NONE"; open var canBeOverridden: Boolean = true
    open var sequentialMainPage: Boolean = false; open var supportedTypes: Set<TvType> = setOf()
    open var lang = "en"; open var mainPage: List<MainPageData> = listOf()
    open var hasQuickSearch: Boolean = false; open var hasMainPage: Boolean = false
    open var enabled: Boolean = true; open var sourcePlugin: String? = null
    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    open suspend fun search(query: String): List<SearchResponse>? = null
    open suspend fun quickSearch(query: String): List<SearchResponse>? = null
    open suspend fun load(url: String): LoadResponse? = null
    open suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean = false
    fun newMovieSearchResponse(name: String, url: String, fix: Boolean = true, i: MovieSearchResponse.() -> Unit = {}) = MovieSearchResponse(name, if (fix) fixUrl(url) else url, this.name).apply(i)
    fun newTvSeriesSearchResponse(name: String, url: String, fix: Boolean = true, i: TvSeriesSearchResponse.() -> Unit = {}) = TvSeriesSearchResponse(name, if (fix) fixUrl(url) else url, this.name).apply(i)
    fun newAnimeSearchResponse(name: String, url: String, fix: Boolean = true, i: AnimeSearchResponse.() -> Unit = {}) = AnimeSearchResponse(name, if (fix) fixUrl(url) else url, this.name).apply(i)
    fun newMovieLoadResponse(name: String, url: String, type: TvType, data: String, fix: Boolean = true, i: MovieLoadResponse.() -> Unit = {}) = MovieLoadResponse(name, if (fix) fixUrl(url) else url, this.name, type, if (fix) fixUrl(data) else data).apply(i)
    fun newTvSeriesLoadResponse(name: String, url: String, type: TvType, episodes: List<Episode>, fix: Boolean = true, i: TvSeriesLoadResponse.() -> Unit = {}) = TvSeriesLoadResponse(name = name, url = if (fix) fixUrl(url) else url, apiName = this.name, type = type, episodes = episodes).apply(i)
    fun newAnimeLoadResponse(name: String, url: String, type: TvType, fix: Boolean = true, i: AnimeLoadResponse.() -> Unit = {}) = AnimeLoadResponse(name, if (fix) fixUrl(url) else url, this.name, type).apply(i)
    fun newHomePageResponse(name: String, items: List<SearchResponse>, hasNext: Boolean = false) = HomePageResponse(listOf(HomePageList(name, items)), hasNext)
    fun newEpisode(episode: Int? = null) = Episode(episode = episode, apiName = this.name)
    fun newExtractorLink(source: String, name: String, url: String, i: ExtractorLink.() -> Unit = {}) = ExtractorLink(source, name, url, mainUrl, ExtractorLink.QUALITY_UNKNOWN).apply(i)
    fun newSubtitleFile(name: String, url: String) = SubtitleFile(name, url)
}

object APIHolder {
    val unixTimeMS: Long get() = System.currentTimeMillis()
    val allProviders: CopyOnWriteArrayList<MainAPI> = CopyOnWriteArrayList()
    private val pluginMapping = java.util.concurrent.ConcurrentHashMap<String, MainAPI>()
    fun initAll() { for (api in allProviders) { /* api.init() */ } }
    fun addPluginMapping(element: MainAPI) { pluginMapping[element.name] = element }
    fun getApiFromName(name: String): MainAPI? = pluginMapping[name] ?: allProviders.firstOrNull { it.name == name }
}

fun MainAPI.fixUrl(url: String): String = when { url.startsWith("http") -> url; url.startsWith("//") -> "https:$url"; url.startsWith("/") -> "${mainUrl.trimEnd('/')}$url"; else -> "${mainUrl.trimEnd('/')}/$url" }
fun fixUrl(url: String, baseUrl: String = ""): String = when { url.startsWith("http") -> url; url.startsWith("//") -> "https:$url"; url.startsWith("/") && baseUrl.isNotEmpty() -> "${baseUrl.trimEnd('/')}$url"; baseUrl.isNotEmpty() -> "${baseUrl.trimEnd('/')}/$url"; else -> url }
fun base64Decode(string: String): String = try { String(java.util.Base64.getDecoder().decode(string)) } catch (e: Throwable) { string }
fun base64Encode(string: String): String = try { java.util.Base64.getEncoder().encodeToString(string.toByteArray()) } catch (e: Throwable) { string }

object JsoupWrapper { fun parse(html: String, baseUri: String = ""): org.jsoup.nodes.Document = org.jsoup.Jsoup.parse(html, baseUri); fun connect(url: String): org.jsoup.Connection = org.jsoup.Jsoup.connect(url) }
fun getProperJsoup(): JsoupWrapper = JsoupWrapper

object app {
    private val client: OkHttpClient get() = com.wizdier.wavestream.data.network.NetworkModule.client
    fun get(url: String, headers: Map<String, String> = mapOf(), allowRedirects: Boolean = true, params: Map<String, String> = mapOf()): ResponseWrapper = request("GET", url, headers, params, null)
    fun post(url: String, headers: Map<String, String> = mapOf(), params: Map<String, String> = mapOf(), data: String? = null, json: String? = null): ResponseWrapper = request("POST", url, headers, params, data ?: json)
    private fun request(method: String, url: String, headers: Map<String, String>, params: Map<String, String>, body: String?): ResponseWrapper {
        val actualUrl = if (params.isNotEmpty()) url + (if (url.contains("?")) "&" else "?") + params.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" } else url
        val builder = Request.Builder().url(actualUrl).header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (body != null) builder.method(method, body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())) else builder.method(method, null)
        return ResponseWrapper(client.newCall(builder.build()).execute())
    }
}
class ResponseWrapper(private val response: Response) : AutoCloseable {
    val text: String get() = response.body?.string() ?: ""
    val body: String get() = text
    val code: Int get() = response.code
    val isSuccessful: Boolean get() = response.isSuccessful
    val headers: Map<String, String> get() = response.headers.names().associateWith { response.header(it).orEmpty() }
    val url: String get() = response.request.url.toString()
    fun header(name: String): String? = response.header(name)
    override fun close() = response.close()
}

@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.RUNTIME) annotation class SkipSerializationTest
@RequiresOptIn(level = RequiresOptIn.Level.ERROR) annotation class Prerelease
@RequiresOptIn(level = RequiresOptIn.Level.ERROR) annotation class InternalAPI
@RequiresOptIn(level = RequiresOptIn.Level.WARNING) annotation class UnsafeSSL
