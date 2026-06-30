package com.wavestream.stremio

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AtomicMutableList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class StremioManifest(
    val id: String = "",
    val name: String,
    val description: String = "",
    val version: String = "",
    val logo: String? = null,
    val resources: List<StremioResource> = emptyList(),
    val types: List<String> = emptyList(),
    val catalogs: List<StremioCatalog> = emptyList(),
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
    val extra: List<StremioExtra> = emptyList(),
)

@Serializable
data class StremioExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
)

@Serializable
data class StremioMetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
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
    val description: String? = null,
    val releaseInfo: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
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
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class StremioStream(
    val name: String? = null,
    val title: String? = null,
    val url: String? = null,
    val externalUrl: String? = null,
    val behaviorHints: StremioStreamBehaviorHints? = null,
    val subtitles: List<StremioSubtitle> = emptyList(),
)

@Serializable
data class StremioStreamBehaviorHints(
    val proxyHeaders: StremioProxyHeaders? = null,
)

@Serializable
data class StremioProxyHeaders(
    val request: Map<String, String>? = null,
)

@Serializable
data class StremioSubtitle(
    val url: String,
    val lang: String? = null,
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

class StremioAddonClient(
    val manifestUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    val baseUrl: String = manifestUrl.substringBefore("?").removeSuffix("/manifest.json")
    val query: String = manifestUrl.substringAfter("?", "").let { if (it.isBlank()) "" else "?$it" }

    suspend fun getManifest(): StremioManifest? {
        return try {
            json.decodeFromString<StremioManifest>(app.get(manifestUrl).text)
        } catch (e: Throwable) {
            println("[Stremio] Manifest fetch failed: ${e.message}")
            null
        }
    }

    fun buildUrl(resource: String, type: String, id: String, extra: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder("$baseUrl/$resource/$type/${encode(id)}.json$query")
        if (extra.isNotEmpty()) {
            sb.append(if (sb.contains("?")) "&" else "?")
            extra.entries.joinTo(sb, "&") { "${encode(it.key)}=${encode(it.value)}" }
        }
        return sb.toString()
    }

    suspend fun getCatalog(type: String, id: String, extra: Map<String, String> = emptyMap()): List<StremioMetaPreview> {
        return try {
            json.decodeFromString<StremioCatalogResponse>(app.get(buildUrl("catalog", type, id, extra)).text).metas
        } catch (e: Throwable) {
            emptyList()
        }
    }

    suspend fun getMeta(type: String, id: String): StremioMetaFull? {
        return try {
            json.decodeFromString<StremioMetaResponse>(app.get(buildUrl("meta", type, id)).text).meta
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun getStreams(type: String, id: String): List<StremioStream> {
        return try {
            json.decodeFromString<StremioStreamsResponse>(app.get(buildUrl("stream", type, id)).text).streams
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun encode(s: String): String = buildString {
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

class StremioProviderAdapter(
    val manifestUrl: String,
    val manifest: StremioManifest,
) : MainAPI() {
    private val client = StremioAddonClient(manifestUrl)

    override var name = manifest.name.ifBlank { "Stremio" }
    override var mainUrl = client.baseUrl
    override val hasMainPage = manifest.catalogs.isNotEmpty()
    override val hasDownloadSupport = false

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
            else -> null
        }
    }.toSet().ifEmpty { setOf(TvType.Movie, TvType.TvSeries) }

    private fun encodeUrl(type: String, id: String): String = "stremio:$type:$id"

    private fun decodeUrl(url: String): Pair<String, String>? {
        if (!url.startsWith("stremio:")) return null
        val rest = url.removePrefix("stremio:")
        val idx = rest.indexOf(':')
        if (idx <= 0 || idx >= rest.length - 1) return null
        return rest.substring(0, idx) to rest.substring(idx + 1)
    }

    private fun mapType(type: String): TvType = when (type.lowercase()) {
        "movie" -> TvType.Movie
        "series" -> TvType.TvSeries
        "anime" -> TvType.Anime
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
            newMovieSearchResponse(
                name = meta.name,
                url = encodeUrl(meta.type, meta.id),
                type = mapType(meta.type),
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
            if (catalog.extra.none { it.name == "search" }) continue
            val metas = client.getCatalog(catalog.type, catalog.id, mapOf("search" to query))
            metas.forEach { meta ->
                results.add(newMovieSearchResponse(
                    name = meta.name,
                    url = encodeUrl(meta.type, meta.id),
                    type = mapType(meta.type),
                    fix = false,
                ) {
                    this.posterUrl = meta.poster
                })
            }
        }
        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val (type, id) = decodeUrl(url) ?: return null
        val meta = client.getMeta(type, id) ?: return null
        val itemType = mapType(meta.type)

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
        val (type, id) = decodeUrl(data) ?: return false
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
                    name = stream.name ?: "External",
                    url = stream.externalUrl,
                    type = ExtractorLinkType.VIDEO,
                ) {
                    this.quality = Qualities.Unknown.value
                })
            }
            stream.subtitles.forEach { sub ->
                subtitleCallback(SubtitleFile(sub.lang ?: sub.label ?: "en", sub.url))
            }
        }
        return streams.isNotEmpty()
    }
}

object StremioAddonRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val _addons = MutableStateFlow<List<StoredStremioAddon>>(emptyList())
    val addons: StateFlow<List<StoredStremioAddon>> = _addons

    private val _manifests = MutableStateFlow<Map<String, StremioManifest>>(emptyMap())
    val manifests: StateFlow<Map<String, StremioManifest>> = _manifests

    private val _adapters = ConcurrentHashMap<String, StremioProviderAdapter>()

    private const val ADDONS_KEY = "stremio_addons_v3"
    private val serializer = ListSerializer(StoredStremioAddon.serializer())

    init {
        loadFromStorage()
    }

    private fun loadFromStorage() {
        val raw = com.wavestream.PlatformStorage.getString(ADDONS_KEY) ?: return
        _addons.value = runCatching {
            json.decodeFromString(serializer, raw)
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        com.wavestream.PlatformStorage.putString(ADDONS_KEY, json.encodeToString(serializer, _addons.value))
    }

    suspend fun addAddon(manifestUrl: String): Result<StremioManifest> {
        val url = manifestUrl.trim().let {
            if (it.startsWith("stremio://")) it.replace("stremio://", "https://") else it
        }
        if (_addons.value.any { it.manifestUrl == url }) {
            return Result.failure(IllegalStateException("Already added"))
        }
        return try {
            val manifest = StremioAddonClient(url, json).getManifest()
                ?: return Result.failure(IllegalStateException("Fetch failed"))

            _addons.value = _addons.value + StoredStremioAddon(url, manifest.name, true)
            persist()

            _manifests.value = _manifests.value.toMutableMap().also { it[url] = manifest }
            register(url, manifest)
            Result.success(manifest)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun removeAddon(manifestUrl: String) {
        val url = manifestUrl.trim().let {
            if (it.startsWith("stremio://")) it.replace("stremio://", "https://") else it
        }
        _addons.value = _addons.value.filterNot { it.manifestUrl == url }
        _manifests.value = _manifests.value.toMutableMap().also { it.remove(url) }
        persist()
        _adapters.remove(url)?.let { removeFromLists(it) }
    }

    fun toggleAddon(manifestUrl: String) {
        val url = manifestUrl.trim().let {
            if (it.startsWith("stremio://")) it.replace("stremio://", "https://") else it
        }
        _addons.value = _addons.value.map {
            if (it.manifestUrl == url) it.copy(enabled = !it.enabled) else it
        }
        persist()

        val addon = _addons.value.firstOrNull { it.manifestUrl == url } ?: return
        if (!addon.enabled) {
            _adapters.remove(url)?.let { removeFromLists(it) }
        } else {
            _manifests.value[url]?.let { manifest ->
                MainScope().launch { register(url, manifest) }
            }
        }
    }

    private fun register(url: String, manifest: StremioManifest) {
        _adapters.remove(url)?.let { removeFromLists(it) }
        val provider = StremioProviderAdapter(url, manifest)
        _adapters[url] = provider
        APIHolder.allProviders.add(provider)
        APIHolder.addPluginMapping(provider)
    }

    private fun removeFromLists(provider: MainAPI) {
        @Suppress("UNCHECKED_CAST")
        val apis = APIHolder.apis as? AtomicMutableList<MainAPI> ?: return
        @Suppress("UNCHECKED_CAST")
        val provs = APIHolder.allProviders as? AtomicMutableList<MainAPI> ?: return

        val apisToKeep = apis.toList().filter { it !== provider }
        apis.clear()
        apis.addAll(apisToKeep)

        val provsToKeep = provs.toList().filter { it !== provider }
        provs.clear()
        provs.addAll(provsToKeep)
    }

    suspend fun initializeAll() {
        for (addon in _addons.value) {
            if (!addon.enabled) continue
            try {
                val manifest = StremioAddonClient(addon.manifestUrl, json).getManifest() ?: continue
                _manifests.value = _manifests.value.toMutableMap().also {
                    it[addon.manifestUrl] = manifest
                }
                register(addon.manifestUrl, manifest)
            } catch (e: Throwable) {
                println("[Stremio] Init failed: ${addon.manifestUrl}")
            }
        }
    }
}
