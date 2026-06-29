package com.wavestream.plugins.stremio

import com.wavestream.api.*
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * StremioAddonRepository — manages installed Stremio addons.
 *
 * Each addon is fetched at /manifest.json, then its catalogs and streams are
 * fetched at /catalog/{type}/{id}.json and /stream/{type}/{id}.json.
 *
 * Stremio addons are wrapped as MainAPI providers so they integrate seamlessly
 * with the existing Home/Search/Details screens.
 */
@Serializable
data class StoredStremioAddon(
    val manifestUrl: String,
    val name: String = "",
    val enabled: Boolean = true,
)

private const val ADDONS_KEY = "stremio_addons_v3"
private val addonSerializer = StoredStremioAddon.serializer()
private val addonListSerializer = ListSerializer(addonSerializer)

object StremioAddonRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    private val _addons = MutableStateFlow<List<StoredStremioAddon>>(emptyList())
    val addons: StateFlow<List<StoredStremioAddon>> = _addons.asStateFlow()

    private val _manifests = MutableStateFlow<Map<String, AddonManifest>>(emptyMap())
    val manifests: StateFlow<Map<String, AddonManifest>> = _manifests.asStateFlow()

    /** Live registry of provider adapters so the UI can reach them. */
    private val _adapters = ConcurrentHashMap<String, StremioProviderAdapter>()
    val adapters: Map<String, StremioProviderAdapter> get() = _adapters.toMap()

    init { loadFromStorage() }

    private fun loadFromStorage() {
        val stored = DataStore.getSerializedList(ADDONS_KEY, addonSerializer) ?: emptyList()
        _addons.value = stored
    }

    private fun persist() {
        DataStore.setSerializedList(ADDONS_KEY, _addons.value, addonSerializer)
    }

    /** Add a Stremio addon by manifest URL. Fetches the manifest synchronously. */
    suspend fun addAddon(manifestUrl: String): Result<AddonManifest> {
        val cleanedUrl = manifestUrl.trim().let {
            if (it.startsWith("stremio://")) {
                it.replace("stremio://", "https://")
            } else it
        }

        if (_addons.value.any { it.manifestUrl == cleanedUrl }) {
            return Result.failure(IllegalStateException("Addon already added"))
        }

        return try {
            val client = StremioAddonClient(cleanedUrl, json)
            val manifest = client.getManifest()

            // Persist stored entry with proper name
            _addons.value = _addons.value + StoredStremioAddon(cleanedUrl, manifest.name, true)
            persist()

            // Cache manifest
            val currentManifests = _manifests.value.toMutableMap()
            currentManifests[cleanedUrl] = manifest
            _manifests.value = currentManifests

            // Register as MainAPI provider immediately
            registerAsProvider(cleanedUrl, manifest)

            println("[StremioAddonRepository] Added addon: ${manifest.name} (catalogs=${manifest.catalogs.size})")
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

        // Remove provider by adapter identity, not name (more reliable)
        val adapter = _adapters.remove(cleanedUrl)
        if (adapter != null) {
            APIHolder.allProviders.removeAll { it === adapter }
            APIHolder.apis.removeAll { it === adapter }
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

        // Update provider registration to match enabled state
        val addon = _addons.value.firstOrNull { it.manifestUrl == cleanedUrl } ?: return
        if (!addon.enabled) {
            val adapter = _adapters.remove(cleanedUrl)
            if (adapter != null) {
                APIHolder.allProviders.removeAll { it === adapter }
                APIHolder.apis.removeAll { it === adapter }
            }
        } else {
            val manifest = _manifests.value[cleanedUrl] ?: return
            registerAsProvider(cleanedUrl, manifest)
        }
    }

    /** Register a Stremio addon as a MainAPI provider so it appears in Home/Search. */
    private fun registerAsProvider(manifestUrl: String, manifest: AddonManifest) {
        // Remove old registration if exists
        val existing = _adapters.remove(manifestUrl)
        if (existing != null) {
            APIHolder.allProviders.removeAll { it === existing }
            APIHolder.apis.removeAll { it === existing }
        }

        val provider = StremioProviderAdapter(manifestUrl, manifest)
        _adapters[manifestUrl] = provider
        APIHolder.allProviders.add(provider)
        APIHolder.addPluginMapping(provider)
        println("[StremioAddonRepository] Registered provider: ${manifest.name} (mainPage items=${provider.mainPage.size})")
    }

    /** Load all enabled addon manifests at startup. */
    fun initializeAll() {
        scope.launch {
            for (addon in _addons.value) {
                if (!addon.enabled) continue
                try {
                    val client = StremioAddonClient(addon.manifestUrl, json)
                    val manifest = client.getManifest()
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
}

/**
 * Adapter that wraps a Stremio addon as a MainAPI provider.
 * This allows Stremio addons to appear in Home/Search/Details.
 *
 * URL scheme used for items: "stremio:{type}:{id}"
 * Example: "stremio:movie:tt1234567"
 *
 * For series episodes, the Episode.data is the Stremio video id (e.g. "tt1234567:1:1").
 */
class StremioProviderAdapter(
    val manifestUrl: String,
    val manifest: AddonManifest,
) : MainAPI() {
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    override var name = manifest.name.ifBlank { "Stremio Addon" }
    override var mainUrl = manifestUrl.substringBefore("?").removeSuffix("/manifest.json")
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
            else -> null
        }
    }.toSet().ifEmpty { setOf(TvType.Movie, TvType.TvSeries) }

    /**
     * Build a Stremio resource URL: ${baseUrl}/${resource}/${type}/${id}.json
     * Preserves the api_key query param from the manifest URL.
     */
    private fun buildResourceUrl(resource: String, type: String, id: String, extra: Map<String, String> = emptyMap()): String {
        val baseUrl = mainUrl
        val query = manifestUrl.substringAfter("?", "").let { if (it.isBlank()) "" else "?$it" }
        val encodedId = id.encodeUrlSegment()
        val sb = StringBuilder("$baseUrl/$resource/$type/$encodedId.json$query")
        if (extra.isNotEmpty()) {
            sb.append(if (sb.contains("?")) "&" else "?")
            extra.entries.joinTo(sb, "&") { (k, v) -> "${k.encodeUrlSegment()}=${v.encodeUrlSegment()}" }
        }
        return sb.toString()
    }

    private fun String.encodeUrlSegment(): String = buildString {
        for (byte in toByteArray()) {
            val v = byte.toInt() and 0xFF
            val c = v.toChar()
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
                append(c)
            } else {
                append('%'); append("0123456789ABCDEF"[v shr 4]); append("0123456789ABCDEF"[v and 0x0F])
            }
        }
    }

    /** Decode the "stremio:{type}:{id}" URL back to (type, id). */
    private fun decodeStremioUrl(url: String): Pair<String, String>? {
        if (!url.startsWith("stremio:")) return null
        val rest = url.removePrefix("stremio:")
        val idx = rest.indexOf(':')
        if (idx <= 0 || idx >= rest.length - 1) return null
        return rest.substring(0, idx) to rest.substring(idx + 1)
    }

    /** Encode (type, id) into a "stremio:{type}:{id}" URL. */
    private fun encodeStremioUrl(type: String, id: String): String = "stremio:$type:$id"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val parts = request.data.split("/", limit = 2)
        if (parts.size < 2) return null
        val type = parts[0]
        val catalogId = parts[1]

        val extra = if (page > 1) mapOf("page" to page.toString()) else emptyMap()
        val url = buildResourceUrl("catalog", type, catalogId, extra)
        val response = app.get(url)
        if (!response.status.isSuccess()) return null

        val catalogResponse = json.decodeFromString<StremioCatalogResponse>(response.bodyAsText())
        val items = catalogResponse.metas.map { meta ->
            val itemType = mapStremioType(meta.type)
            newMovieSearchResponse(
                name = meta.name,
                url = encodeStremioUrl(meta.type, meta.id),
                type = itemType,
                fix = false,
            ) {
                posterUrl = meta.poster
                this.year = meta.releaseInfo?.substringBefore("-")?.toIntOrNull()
            }
        }
        // Stremio catalogs return ~20-100 items per page; assume next page exists if we got 20+
        return HomePageResponse(
            items = listOf(HomePageList(request.name, items, isHorizontalImages = false)),
            hasNext = items.size >= 20,
        )
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

    override suspend fun search(query: String): List<SearchResponse>? {
        // Search across all catalogs that declare an "search" extra property
        val results = mutableListOf<SearchResponse>()
        for (catalog in manifest.catalogs) {
            val searchExtra = catalog.extra.firstOrNull { it.name == "search" } ?: continue
            if (searchExtra.isRequired) {
                // required search — pass query as search param
            }
            val type = catalog.type
            val url = buildResourceUrl("catalog", type, catalog.id, mapOf("search" to query))
            try {
                val response = app.get(url)
                if (!response.status.isSuccess()) continue
                val catalogResponse = json.decodeFromString<StremioCatalogResponse>(response.bodyAsText())
                catalogResponse.metas.forEach { meta ->
                    results.add(newMovieSearchResponse(
                        name = meta.name,
                        url = encodeStremioUrl(meta.type, meta.id),
                        type = mapStremioType(meta.type),
                        fix = false,
                    ) {
                        posterUrl = meta.poster
                        this.year = meta.releaseInfo?.substringBefore("-")?.toIntOrNull()
                    })
                }
            } catch (_: Throwable) { continue }
        }
        // Deduplicate by id (multiple catalogs may return same meta)
        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val (type, id) = decodeStremioUrl(url) ?: return null

        val metaUrl = buildResourceUrl("meta", type, id)
        val response = app.get(metaUrl)
        if (!response.status.isSuccess()) return null

        val metaResponse = json.decodeFromString<StremioMetaResponse>(response.bodyAsText())
        val meta = metaResponse.meta
        val itemType = mapStremioType(meta.type)

        return if (itemType.isMovieType()) {
            newMovieLoadResponse(meta.name, url, itemType, data = url) {
                posterUrl = meta.poster
                backgroundPosterUrl = meta.background
                plot = meta.description
                year = meta.releaseInfo?.substringBefore("-")?.toIntOrNull()
                score = meta.imdbRating?.toFloatOrNull()?.let { it / 10f }
                tags = meta.genres
                actors = meta.cast.map { ActorData(Actor(it)) }
                duration = meta.runtime?.toIntOrNull()
            }
        } else {
            // Encode the Stremio type INTO episode data so loadLinks can route correctly.
            // Episode.data = "stremio:series:${videoId}" — loadLinks decodes (type=series, id=videoId).
            val episodes = meta.videos.map { video ->
                Episode(
                    data = "stremio:${meta.type}:${video.id}",
                    name = video.title,
                    season = video.season,
                    episode = video.episode,
                    posterUrl = video.thumbnail,
                    description = video.overview,
                )
            }
            newTvSeriesLoadResponse(meta.name, url, itemType, episodes) {
                posterUrl = meta.poster
                backgroundPosterUrl = meta.background
                plot = meta.description
                year = meta.releaseInfo?.substringBefore("-")?.toIntOrNull()
                tags = meta.genres
                actors = meta.cast.map { ActorData(Actor(it)) }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        // data is "stremio:{type}:{id}" — extract type and id
        val (type, id) = decodeStremioUrl(data) ?: return false

        val streamUrl = buildResourceUrl("stream", type, id)
        val response = app.get(streamUrl)
        if (!response.status.isSuccess()) return false

        val streamsResponse = json.decodeFromString<StremioStreamsResponse>(response.bodyAsText())
        for (stream in streamsResponse.streams) {
            // Direct URL stream
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
            }
            // External URL stream
            else if (stream.externalUrl != null) {
                callback(newExtractorLink(
                    source = manifest.name,
                    name = stream.name ?: stream.title ?: "External",
                    url = stream.externalUrl,
                    type = ExtractorLinkType.VIDEO,
                ) {
                    this.quality = Qualities.Unknown.value
                })
            }
            // Torrent stream (infoHash) — needs a torrent engine, not supported yet
            // else if (stream.infoHash != null) { ... }

            // Subtitles attached to streams
            stream.subtitles.forEach { sub ->
                subtitleCallback(SubtitleFile(
                    lang = sub.lang ?: sub.language ?: sub.label ?: "en",
                    url = sub.url,
                ))
            }
        }
        return streamsResponse.streams.isNotEmpty()
    }
}
