package com.wavestream.stremio

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Stremio addon models — mirror the Stremio addon protocol.
 */
@Serializable
data class StremioManifest(
    val id: String = "",
    val name: String,
    val description: String = "",
    val version: String = "",
    val logo: String? = null,
    val resources: List<StremioResource> = emptyList(),
    val types: List<String> = emptyList(),
    val idPrefixes: List<String> = emptyList(),
    val catalogs: List<StremioCatalog> = emptyList(),
    val behaviorHints: StremioBehaviorHints = StremioBehaviorHints(),
)

@Serializable
data class StremioResource(
    val name: String,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String> = emptyList(),
)

@Serializable
data class StremioCatalog(
    val type: String,
    val id: String,
    val name: String = "",
    val extra: List<StremioExtraProperty> = emptyList(),
)

@Serializable
data class StremioExtraProperty(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLimit: Int? = null,
)

@Serializable
data class StremioBehaviorHints(
    val adult: Boolean = false,
    val p2p: Boolean = false,
)

@Serializable
data class StremioMetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val posterShape: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
)

@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMetaPreview> = emptyList(),
)

@Serializable
data class StremioMetaFull(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val runtime: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val writer: List<String> = emptyList(),
    val country: String? = null,
    val language: String? = null,
    val videos: List<StremioVideo> = emptyList(),
)

@Serializable
data class StremioMetaResponse(
    val meta: StremioMetaFull,
)

@Serializable
data class StremioVideo(
    val id: String,
    val title: String = "",
    val released: String? = null,
    val thumbnail: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val runtime: Int? = null,
)

@Serializable
data class StremioStream(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val externalUrl: String? = null,
    val sources: List<String> = emptyList(),
    val behaviorHints: StremioStreamBehaviorHints? = null,
    val subtitles: List<StremioSubtitle> = emptyList(),
)

@Serializable
data class StremioStreamBehaviorHints(
    val bingeGroup: String? = null,
    val notWebReady: Boolean = false,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null,
    val proxyHeaders: StremioProxyHeaders? = null,
)

@Serializable
data class StremioProxyHeaders(
    val request: Map<String, String>? = null,
    val response: Map<String, String>? = null,
)

@Serializable
data class StremioSubtitle(
    val url: String,
    val lang: String? = null,
    val language: String? = null,
    val label: String? = null,
)

@Serializable
data class StremioStreamsResponse(
    val streams: List<StremioStream> = emptyList(),
)

@Serializable
data class StoredStremioAddon(
    val manifestUrl: String,
    val name: String = "",
    val enabled: Boolean = true,
)

/**
 * Stremio addon client — fetches manifest/catalog/meta/stream from an addon.
 */
class StremioAddonClient(
    val manifestUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    val baseUrl: String = manifestUrl.substringBefore("?").removeSuffix("/manifest.json")
    val query: String = manifestUrl.substringAfter("?", "").let { if (it.isBlank()) "" else "?$it" }

    suspend fun getManifest(): StremioManifest? {
        return try {
            val text = app.get(manifestUrl).text
            json.decodeFromString<StremioManifest>(text)
        } catch (e: Throwable) {
            println("[Stremio] Failed to fetch manifest $manifestUrl: ${e.message}")
            null
        }
    }

    fun buildResourceUrl(resource: String, type: String, id: String, extra: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder("$baseUrl/$resource/$type/${urlEncode(id)}.json$query")
        if (extra.isNotEmpty()) {
            sb.append(if (sb.contains("?")) "&" else "?")
            extra.entries.joinTo(sb, "&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }
        }
        return sb.toString()
    }

    suspend fun getCatalog(type: String, id: String, extra: Map<String, String> = emptyMap()): List<StremioMetaPreview> {
        return try {
            val url = buildResourceUrl("catalog", type, id, extra)
            val text = app.get(url).text
            json.decodeFromString<StremioCatalogResponse>(text).metas
        } catch (e: Throwable) {
            println("[Stremio] Catalog fetch failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getMeta(type: String, id: String): StremioMetaFull? {
        return try {
            val url = buildResourceUrl("meta", type, id)
            val text = app.get(url).text
            json.decodeFromString<StremioMetaResponse>(text).meta
        } catch (e: Throwable) {
            println("[Stremio] Meta fetch failed: ${e.message}")
            null
        }
    }

    suspend fun getStreams(type: String, id: String): List<StremioStream> {
        return try {
            val url = buildResourceUrl("stream", type, id)
            val text = app.get(url).text
            json.decodeFromString<StremioStreamsResponse>(text).streams
        } catch (e: Throwable) {
            println("[Stremio] Stream fetch failed: ${e.message}")
            emptyList()
        }
    }

    private fun urlEncode(s: String): String = buildString {
        for (byte in s.toByteArray()) {
            val v = byte.toInt() and 0xFF
            val c = v.toChar()
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
                append(c)
            } else {
                append('%')
                append("0123456789ABCDEF"[v shr 4])
                append("0123456789ABCDEF"[v and 0x0F])
            }
        }
    }
}

/**
 * Adapter that wraps a Stremio addon as a CloudStream MainAPI provider.
 *
 * URL scheme for items: "stremio:{type}:{id}"
 * Example: "stremio:movie:tt1234567"
 *
 * For series episodes, Episode.data is "stremio:series:{videoId}" — loadLinks
 * decodes type and id from this URL.
 */
class StremioProviderAdapter(
    val manifestUrl: String,
    val manifest: StremioManifest,
) : MainAPI() {
    private val client = StremioAddonClient(manifestUrl)

    override var name = manifest.name.ifBlank { "Stremio Addon" }
    override var mainUrl = client.baseUrl
    override var lang = "en"
    override val hasMainPage = manifest.catalogs.isNotEmpty()
    override val hasQuickSearch = false
    override val hasDownloadSupport = false
    override val hasChromecastSupport = true
    override val instantLinkLoading = true

    override val mainPage: List<MainPageData> = manifest.catalogs.map { catalog ->
        MainPageData(
            name = catalog.name.ifBlank { "${catalog.type}/${catalog.id}" },
            data = "${catalog.type}/${catalog.id}",
            horizontalImages = false,
        )
    }

    override val supportedTypes: Set<TvType> = manifest.types.mapNotNull { typeStr ->
        when (typeStr.lowercase()) {
            "movie" -> TvType.Movie
            "series" -> TvType.TvSeries
            "anime" -> TvType.Anime
            "tv" -> TvType.Live
            "channel" -> TvType.Live
            "other" -> TvType.Others
            else -> null
        }
    }.toSet().ifEmpty { setOf(TvType.Movie, TvType.TvSeries) }

    private fun encodeStremioUrl(type: String, id: String): String = "stremio:$type:$id"

    private fun decodeStremioUrl(url: String): Pair<String, String>? {
        if (!url.startsWith("stremio:")) return null
        val rest = url.removePrefix("stremio:")
        val idx = rest.indexOf(':')
        if (idx <= 0 || idx >= rest.length - 1) return null
        return rest.substring(0, idx) to rest.substring(idx + 1)
    }

    private fun mapStremioType(stremioType: String): TvType = when (stremioType.lowercase()) {
        "movie" -> TvType.Movie
        "series" -> TvType.TvSeries
        "anime" -> TvType.Anime
        "tv" -> TvType.Live
        "channel" -> TvType.Live
        "other" -> TvType.Others
        else -> TvType.Movie
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val parts = request.data.split("/", limit = 2)
        if (parts.size < 2) return null
        val type = parts[0]
        val catalogId = parts[1]

        val extra = if (page > 1) mapOf("page" to page.toString()) else emptyMap()
        val metas = client.getCatalog(type, catalogId, extra)
        val items = metas.map { meta ->
            val itemType = mapStremioType(meta.type)
            newMovieSearchResponse(
                name = meta.name,
                url = encodeStremioUrl(meta.type, meta.id),
                type = itemType,
                fix = false,
            ) {
                this.posterUrl = meta.poster
                this.year = meta.releaseInfo?.substringBefore("-")?.toIntOrNull()
            }
        }
        return newHomePageResponse(
            list = listOf(HomePageList(request.name, items, false)),
            hasNext = items.size >= 20,
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val results = mutableListOf<SearchResponse>()
        for (catalog in manifest.catalogs) {
            val searchExtra = catalog.extra.firstOrNull { it.name == "search" } ?: continue
            val type = catalog.type
            val metas = client.getCatalog(type, catalog.id, mapOf("search" to query))
            metas.forEach { meta ->
                results.add(newMovieSearchResponse(
                    name = meta.name,
                    url = encodeStremioUrl(meta.type, meta.id),
                    type = mapStremioType(meta.type),
                    fix = false,
                ) {
                    this.posterUrl = meta.poster
                    this.year = meta.releaseInfo?.substringBefore("-")?.toIntOrNull()
                })
            }
        }
        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val (type, id) = decodeStremioUrl(url) ?: return null
        val meta = client.getMeta(type, id) ?: return null
        val itemType = mapStremioType(meta.type)

        return if (itemType.isMovieType()) {
            newMovieLoadResponse(meta.name, url, itemType, url) {
                this.posterUrl = meta.poster
                this.plot = meta.description
                this.year = meta.releaseInfo?.substringBefore("-")?.toIntOrNull()
                this.tags = meta.genres
                this.duration = meta.runtime?.toIntOrNull()
            }
        } else {
            val episodes = meta.videos.map { video ->
                newEpisode("stremio:${meta.type}:${video.id}", { 
                    this.name = video.title
                    this.season = video.season
                    this.episode = video.episode
                    this.posterUrl = video.thumbnail
                    this.description = video.overview
                }, fix = false)
            }
            newTvSeriesLoadResponse(meta.name, url, itemType, episodes) {
                this.posterUrl = meta.poster
                this.plot = meta.description
                this.year = meta.releaseInfo?.substringBefore("-")?.toIntOrNull()
                this.tags = meta.genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val (type, id) = decodeStremioUrl(data) ?: return false
        val streams = client.getStreams(type, id)
        for (stream in streams) {
            if (stream.url != null) {
                val linkType = when {
                    stream.url.contains(".m3u8") -> ExtractorLinkType.M3U8
                    stream.url.contains(".mpd") -> ExtractorLinkType.DASH
                    else -> ExtractorLinkType.VIDEO
                }
                val proxyHeaders = stream.behaviorHints?.proxyHeaders?.request ?: emptyMap()
                callback(newExtractorLink(
                    source = manifest.name,
                    name = stream.name ?: stream.title ?: "Stream",
                    url = stream.url,
                    type = linkType,
                ) {
                    this.quality = Qualities.Unknown.value
                    this.headers = proxyHeaders
                    this.referer = proxyHeaders["referer"] ?: ""
                })
            } else if (stream.externalUrl != null) {
                callback(newExtractorLink(
                    source = manifest.name,
                    name = stream.name ?: stream.title ?: "External",
                    url = stream.externalUrl,
                    type = ExtractorLinkType.VIDEO,
                ) {
                    this.quality = Qualities.Unknown.value
                })
            }

            stream.subtitles.forEach { sub ->
                subtitleCallback(SubtitleFile(
                    lang = sub.lang ?: sub.language ?: sub.label ?: "en",
                    url = sub.url,
                ))
            }
        }
        return streams.isNotEmpty()
    }
}

/**
 * Repository of installed Stremio addons.
 */
object StremioAddonRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    private val _addons = MutableStateFlow<List<StoredStremioAddon>>(emptyList())
    val addons: StateFlow<List<StoredStremioAddon>> = _addons

    private val _manifests = MutableStateFlow<Map<String, StremioManifest>>(emptyMap())
    val manifests: StateFlow<Map<String, StremioManifest>> = _manifests

    private val _adapters = ConcurrentHashMap<String, StremioProviderAdapter>()
    val adapters: Map<String, StremioProviderAdapter> get() = _adapters.toMap()

    private const val ADDONS_KEY = "stremio_addons_v3"
    private val addonSerializer = StoredStremioAddon.serializer()
    private val addonListSerializer = ListSerializer(addonSerializer)

    init { loadFromStorage() }

    private fun loadFromStorage() {
        val raw = com.wavestream.PlatformStorage.getString(ADDONS_KEY) ?: return
        val stored = runCatching { json.decodeFromString(addonListSerializer, raw) }.getOrDefault(emptyList())
        _addons.value = stored
    }

    private fun persist() {
        val raw = json.encodeToString(addonListSerializer, _addons.value)
        com.wavestream.PlatformStorage.putString(ADDONS_KEY, raw)
    }

    suspend fun addAddon(manifestUrl: String): Result<StremioManifest> {
        val cleanedUrl = manifestUrl.trim().let {
            if (it.startsWith("stremio://")) it.replace("stremio://", "https://") else it
        }

        if (_addons.value.any { it.manifestUrl == cleanedUrl }) {
            return Result.failure(IllegalStateException("Addon already added"))
        }

        return try {
            val client = StremioAddonClient(cleanedUrl, json)
            val manifest = client.getManifest()
                ?: return Result.failure(IllegalStateException("Failed to fetch manifest"))

            _addons.value = _addons.value + StoredStremioAddon(cleanedUrl, manifest.name, true)
            persist()

            val currentManifests = _manifests.value.toMutableMap()
            currentManifests[cleanedUrl] = manifest
            _manifests.value = currentManifests

            registerAsProvider(cleanedUrl, manifest)
            println("[StremioAddonRepository] Added: ${manifest.name} (${manifest.catalogs.size} catalogs)")
            Result.success(manifest)
        } catch (e: Throwable) {
            println("[StremioAddonRepository] Failed to add addon $cleanedUrl: ${e.message}")
            Result.failure(e)
        }
    }

    fun removeAddon(manifestUrl: String) {
        val cleanedUrl = manifestUrl.trim().let {
            if (it.startsWith("stremio://")) it.replace("stremio://", "https://") else it
        }
        _addons.value = _addons.value.filterNot { it.manifestUrl == cleanedUrl }
        val currentManifests = _manifests.value.toMutableMap()
        currentManifests.remove(cleanedUrl)
        _manifests.value = currentManifests
        persist()

        val adapter = _adapters.remove(cleanedUrl)
        if (adapter != null) {
            removeProviderFromLists(adapter)
        }
    }

    private fun removeProviderFromLists(provider: MainAPI) {
        val apis = com.lagradost.cloudstream3.APIHolder.apis
        val providers = com.lagradost.cloudstream3.APIHolder.allProviders
        // AtomicList (parent class) doesn't expose mutation methods directly —
        // cast to AtomicMutableList which does have clear/addAll.
        @Suppress("UNCHECKED_CAST")
        val apisMutable = apis as? com.lagradost.cloudstream3.utils.AtomicMutableList<MainAPI>
        @Suppress("UNCHECKED_CAST")
        val providersMutable = providers as? com.lagradost.cloudstream3.utils.AtomicMutableList<MainAPI>

        if (apisMutable != null) {
            val toKeep = apisMutable.toList().filter { it !== provider }
            apisMutable.clear()
            apisMutable.addAll(toKeep)
        }
        if (providersMutable != null) {
            val toKeep = providersMutable.toList().filter { it !== provider }
            providersMutable.clear()
            providersMutable.addAll(toKeep)
        }
    }

    fun toggleAddon(manifestUrl: String) {
        val cleanedUrl = manifestUrl.trim().let {
            if (it.startsWith("stremio://")) it.replace("stremio://", "https://") else it
        }
        _addons.value = _addons.value.map {
            if (it.manifestUrl == cleanedUrl) it.copy(enabled = !it.enabled) else it
        }
        persist()

        val addon = _addons.value.firstOrNull { it.manifestUrl == cleanedUrl } ?: return
        if (!addon.enabled) {
            val adapter = _adapters.remove(cleanedUrl)
            if (adapter != null) {
                removeProviderFromLists(adapter)
            }
        } else {
            val manifest = _manifests.value[cleanedUrl] ?: return
            MainScope().launch { registerAsProvider(cleanedUrl, manifest) }
        }
    }

    private fun registerAsProvider(manifestUrl: String, manifest: StremioManifest) {
        val existing = _adapters.remove(manifestUrl)
        if (existing != null) {
            removeProviderFromLists(existing)
        }

        val provider = StremioProviderAdapter(manifestUrl, manifest)
        _adapters[manifestUrl] = provider
        com.lagradost.cloudstream3.APIHolder.allProviders.add(provider)
        com.lagradost.cloudstream3.APIHolder.addPluginMapping(provider)
        println("[StremioAddonRepository] Registered: ${manifest.name}")
    }

    suspend fun initializeAll() {
        for (addon in _addons.value) {
            if (!addon.enabled) continue
            try {
                val client = StremioAddonClient(addon.manifestUrl, json)
                val manifest = client.getManifest() ?: continue
                val currentManifests = _manifests.value.toMutableMap()
                currentManifests[addon.manifestUrl] = manifest
                _manifests.value = currentManifests
                registerAsProvider(addon.manifestUrl, manifest)
            } catch (e: Throwable) {
                println("[StremioAddonRepository] Failed to init ${addon.manifestUrl}: ${e.message}")
            }
        }
    }
}
