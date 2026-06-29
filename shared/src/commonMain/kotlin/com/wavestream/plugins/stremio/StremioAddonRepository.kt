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
    private val json = Json { ignoreUnknownKeys = true }

    private val _addons = MutableStateFlow<List<StoredStremioAddon>>(emptyList())
    val addons: StateFlow<List<StoredStremioAddon>> = _addons.asStateFlow()

    private val _manifests = MutableStateFlow<Map<String, AddonManifest>>(emptyMap())
    val manifests: StateFlow<Map<String, AddonManifest>> = _manifests.asStateFlow()

    init { loadFromStorage() }

    private fun loadFromStorage() {
        val stored = DataStore.getSerializedList(ADDONS_KEY, addonSerializer) ?: emptyList()
        _addons.value = stored
    }

    private fun persist() {
        DataStore.setSerializedList(ADDONS_KEY, _addons.value, addonSerializer)
    }

    /** Add a Stremio addon by manifest URL. Fetches the manifest to get the name. */
    suspend fun addAddon(manifestUrl: String): Boolean {
        if (_addons.value.any { it.manifestUrl == manifestUrl }) return false
        // Add immediately with URL as name, then update when manifest is fetched
        _addons.value = _addons.value + StoredStremioAddon(manifestUrl, manifestUrl, true)
        persist()

        // Fetch manifest
        scope.launch {
            try {
                val client = StremioAddonClient(manifestUrl, json)
                val manifest = client.getManifest()
                val updated = _addons.value.map {
                    if (it.manifestUrl == manifestUrl) it.copy(name = manifest.name) else it
                }
                _addons.value = updated
                persist()

                val currentManifests = _manifests.value.toMutableMap()
                currentManifests[manifestUrl] = manifest
                _manifests.value = currentManifests

                // Register as MainAPI provider
                registerAsProvider(manifestUrl, manifest)
            } catch (e: Throwable) {
                println("[StremioAddonRepository] Failed to fetch manifest: ${e.message}")
            }
        }
        return true
    }

    fun removeAddon(manifestUrl: String) {
        _addons.value = _addons.value.filterNot { it.manifestUrl == manifestUrl }
        val currentManifests = _manifests.value.toMutableMap()
        currentManifests.remove(manifestUrl)
        _manifests.value = currentManifests
        persist()

        // Remove from APIHolder
        APIHolder.allProviders.removeAll { it.name == "stremio:${manifestUrl.hashCode()}" }
        APIHolder.apis.removeAll { it.name == "stremio:${manifestUrl.hashCode()}" }
    }

    fun toggleAddon(manifestUrl: String) {
        _addons.value = _addons.value.map {
            if (it.manifestUrl == manifestUrl) it.copy(enabled = !it.enabled) else it
        }
        persist()
    }

    /** Register a Stremio addon as a MainAPI provider so it appears in Home/Search. */
    private fun registerAsProvider(manifestUrl: String, manifest: AddonManifest) {
        val providerName = "stremio:${manifestUrl.hashCode()}"
        // Remove old registration if exists
        APIHolder.allProviders.removeAll { it.name == providerName }
        APIHolder.apis.removeAll { it.name == providerName }

        val provider = StremioProviderAdapter(manifestUrl, manifest)
        APIHolder.allProviders.add(provider)
        APIHolder.addPluginMapping(provider)
        println("[StremioAddonRepository] Registered provider: $providerName (${manifest.name})")
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
 */
class StremioProviderAdapter(
    private val manifestUrl: String,
    private val manifest: AddonManifest,
) : MainAPI() {
    private val json = Json { ignoreUnknownKeys = true }

    override var name = "stremio:${manifestUrl.hashCode()}"
    override var mainUrl = manifestUrl.substringBefore("?").removeSuffix("/manifest.json")
    override var lang = "en"
    override val hasMainPage = manifest.catalogs.isNotEmpty()
    override val hasQuickSearch = false

    override val mainPage = manifest.catalogs.map { catalog ->
        MainPageData(
            name = catalog.name.ifBlank { catalog.id },
            data = "${catalog.type}/${catalog.id}",
            horizontalImages = false,
        )
    }

    override val supportedTypes = manifest.types.mapNotNull { typeStr ->
        when (typeStr.lowercase()) {
            "movie" -> TvType.Movie
            "series" -> TvType.TvSeries
            "anime" -> TvType.Anime
            else -> null
        }
    }.toSet().ifEmpty { setOf(TvType.Movie, TvType.TvSeries) }

    private fun buildResourceUrl(resource: String, type: String, id: String): String {
        val baseUrl = mainUrl
        val query = manifestUrl.substringAfter("?", "").let { if (it.isBlank()) "" else "?$it" }
        return "$baseUrl/$resource/$type/${id.encodeUrlSegment()}.$resource$query"
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val parts = request.data.split("/")
        if (parts.size < 2) return null
        val type = parts[0]
        val catalogId = parts[1]

        val url = buildResourceUrl("catalog", type, catalogId) + if (page > 1) "?page=$page" else ""
        val response = app.get(url)
        if (!response.status.isSuccess()) return null

        val catalogResponse = json.decodeFromString<StremioCatalogResponse>(response.bodyAsText())
        val items = catalogResponse.metas.map { meta ->
            newMovieSearchResponse(
                name = meta.name,
                url = "${meta.type}:${meta.id}",
                type = when (meta.type.lowercase()) {
                    "movie" -> TvType.Movie
                    "series" -> TvType.TvSeries
                    "anime" -> TvType.Anime
                    else -> TvType.Movie
                },
            ) {
                posterUrl = meta.poster
                this.year = meta.releaseInfo?.toIntOrNull()
            }
        }
        return HomePageResponse(listOf(HomePageList(request.name, items)), hasNext = items.size >= 20)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        // Search across all catalog types that support search
        val results = mutableListOf<SearchResponse>()
        for (catalog in manifest.catalogs) {
            val searchExtra = catalog.extra.firstOrNull { it.name == "search" } ?: continue
            val type = catalog.type
            val url = buildResourceUrl("catalog", type, catalog.id) + "?search=${query.encodeUrlSegment()}"
            try {
                val response = app.get(url)
                if (!response.status.isSuccess()) continue
                val catalogResponse = json.decodeFromString<StremioCatalogResponse>(response.bodyAsText())
                catalogResponse.metas.forEach { meta ->
                    results.add(newMovieSearchResponse(
                        name = meta.name,
                        url = "${meta.type}:${meta.id}",
                        type = when (meta.type.lowercase()) {
                            "movie" -> TvType.Movie
                            "series" -> TvType.TvSeries
                            "anime" -> TvType.Anime
                            else -> TvType.Movie
                        },
                    ) {
                        posterUrl = meta.poster
                    })
                }
            } catch (e: Throwable) { continue }
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        // url format: "type:id"
        val parts = url.split(":", limit = 2)
        if (parts.size < 2) return null
        val type = parts[0]
        val id = parts[1]

        val metaUrl = buildResourceUrl("meta", type, id)
        val response = app.get(metaUrl)
        if (!response.status.isSuccess()) return null

        val metaResponse = json.decodeFromString<StremioMetaResponse>(response.bodyAsText())
        val meta = metaResponse.meta

        val isMovie = type.lowercase() == "movie"
        return if (isMovie) {
            newMovieLoadResponse(meta.name, url, TvType.Movie, data = url) {
                posterUrl = meta.poster
                plot = meta.description
                year = meta.releaseInfo?.toIntOrNull()
                score = meta.imdbRating?.toFloatOrNull()?.let { it / 10f }
                tags = meta.genres
            }
        } else {
            val episodes = meta.videos.mapIndexed { idx, video ->
                Episode(
                    data = video.id,
                    name = video.title,
                    season = video.season,
                    episode = video.episode,
                    posterUrl = video.thumbnail,
                    description = video.overview,
                )
            }
            newTvSeriesLoadResponse(meta.name, url, TvType.TvSeries, episodes) {
                posterUrl = meta.poster
                plot = meta.description
                year = meta.releaseInfo?.toIntOrNull()
                tags = meta.genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        // data format: "type:id" or episode id
        val parts = data.split(":", limit = 2)
        if (parts.size < 2) return false
        val type = parts[0]
        val id = parts[1]

        val streamUrl = buildResourceUrl("stream", type, id)
        val response = app.get(streamUrl)
        if (!response.status.isSuccess()) return false

        val streamsResponse = json.decodeFromString<StremioStreamsResponse>(response.bodyAsText())
        for (stream in streamsResponse.streams) {
            // Direct URL stream
            if (stream.url != null) {
                val linkType = if (stream.url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                callback(newExtractorLink(
                    source = manifest.name,
                    name = stream.name ?: stream.title ?: "Stream",
                    url = stream.url,
                    type = linkType,
                ) {
                    this.quality = Qualities.Unknown.value
                    stream.behaviorHints?.proxyHeaders?.request?.forEach { (k, v) ->
                        // headers not directly settable in builder, but stored
                    }
                })
            }
            // Torrent stream (infoHash)
            if (stream.infoHash != null) {
                // For torrent streams, we'd need a torrent engine.
                // For now, skip — the user can add a torrent-capable extension.
            }
        }
        return true
    }
}
