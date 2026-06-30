package com.wavestream.stremio

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Minimal Stremio addon client. Stremio addons expose a JSON catalog at
 * `/<addon>/catalog/<type>/<id>.json` and stream metadata at
 * `/<addon>/stream/<type>/<id>.json`. We wrap a single addon URL as a
 * CloudStream [MainAPI] provider so the rest of the app can treat it
 * identically to native CS providers.
 *
 * Spec reference: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests.md
 */
@Serializable
data class StremioMetaPreview(
    val id: String,
    val name: String,
    val type: String? = null,
    val poster: String? = null,
    val posterShape: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val genres: List<String>? = null,
)

@Serializable
data class StremioMeta(
    val id: String,
    val name: String,
    val type: String? = null,
    val poster: String? = null,
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val genres: List<String>? = null,
    val director: List<StremioCrew>? = null,
    val cast: List<StremioCrew>? = null,
    val videos: List<StremioEpisode>? = null,
)

@Serializable
data class StremioCrew(val name: String? = null)

@Serializable
data class StremioEpisode(
    val id: String,
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val thumbnail: String? = null,
    val overview: String? = null,
    val released: String? = null,
)

@Serializable
data class StremioCatalogResponse(val metas: List<StremioMetaPreview> = emptyList())

@Serializable
data class StremioStream(
    val name: String? = null,
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
    val ytId: String? = null,
    val externalUrl: String? = null,
    val behaviorHints: StremioBehaviorHints? = null,
)

@Serializable
data class StremioBehaviorHints(
    val notWebReady: Boolean? = null,
    val bingeGroup: String? = null,
    val countryWhitelist: List<String>? = null,
    val proxyHeaders: Map<String, String>? = null,
)

@Serializable
data class StremioStreamResponse(val streams: List<StremioStream> = emptyList())

/**
 * Repository of installed Stremio addons. Each addon URL becomes a
 * [StremioProviderAdapter] registered in APIHolder.
 */
object StremioAddonRepository {
    private const val PREF_KEY = "wavestream.stremio.addons"
    private val json = Json { ignoreUnknownKeys = true }

    var listeners: MutableList<() -> Unit> = mutableListOf()

    /** Returns the list of installed addon URLs. */
    fun listAddons(): List<String> {
        val raw = com.wavestream.platform.wavePlatform.preferences.getString(PREF_KEY) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrElse { emptyList() }
    }

    fun addAddon(url: String) {
        val current = listAddons().toMutableList()
        if (url in current) return
        current.add(url)
        com.wavestream.platform.wavePlatform.preferences.putString(PREF_KEY, json.encodeToString(current))
        listeners.forEach { it() }
    }

    fun removeAddon(url: String) {
        val current = listAddons().toMutableList()
        current.remove(url)
        com.wavestream.platform.wavePlatform.preferences.putString(PREF_KEY, json.encodeToString(current))
        listeners.forEach { it() }
    }

    /**
     * Re-registers all installed Stremio addons as CloudStream providers.
     * Call after plugins load and after every add/remove operation.
     */
    fun syncProviders() {
        val existing = APIHolder.allProviders.withLock {
            APIHolder.allProviders.filterIsInstance<StremioProviderAdapter>()
        }
        // Remove existing Stremio providers (AtomicList doesn't expose
        // minusAssign directly; we iterate and remove explicitly).
        existing.forEach { provider ->
            APIHolder.allProviders.withLock {
                val iter = APIHolder.allProviders.iterator()
                while (iter.hasNext()) {
                    if (iter.next() === provider) {
                        iter.remove()
                        break
                    }
                }
            }
        }

        for (url in listAddons()) {
            val provider = StremioProviderAdapter(url)
            APIHolder.allProviders.withLock { APIHolder.allProviders.add(provider) }
        }
    }
}

/**
 * Wraps a Stremio addon as a CloudStream [MainAPI] provider.
 *
 * The addon's root URL (e.g. `https://example.com/stremio/addon`) becomes
 * the provider's [mainUrl]. Calls to [search] hit `catalog/<type>/<id>.json`
 * with the query as a search parameter, and [load] fetches full metadata
 * plus stream links.
 *
 * Stream URLs returned by Stremio that start with `stremio:` (deep links)
 * are passed through `fixUrl` unchanged — the patch we applied to MainAPI
 * makes that possible.
 */
class StremioProviderAdapter(
    addonUrl: String,
) : MainAPI() {

    override var name: String = "Stremio"
    override var mainUrl: String = addonUrl.trimEnd('/')
    override val hasMainPage: Boolean = true
    override val hasQuickSearch: Boolean = false

    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Derive a friendlier name from the addon URL host.
        runCatching {
            val host = mainUrl.substringAfter("://").substringBefore("/")
            name = "Stremio: $host"
        }
    }

    private fun catalogUrl(type: String, id: String = "top"): String =
        "$mainUrl/catalog/$type/$id.json"

    private fun streamUrl(type: String, id: String): String =
        "$mainUrl/stream/$type/$id.json"

    private suspend fun fetchCatalog(type: String): List<StremioMetaPreview> {
        return try {
            val response = app.get(catalogUrl(type))
            json.decodeFromString(StremioCatalogResponse.serializer(), response.text).metas
        } catch (e: Throwable) {
            logError(e)
            emptyList()
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return try {
            val metas = (fetchCatalog("movie") + fetchCatalog("series"))
                .filter { it.name.contains(query, ignoreCase = true) }
            metas.map { meta ->
                val tvType = if (meta.type == "series") TvType.TvSeries else TvType.Movie
                if (tvType == TvType.Movie) {
                    newMovieSearchResponse(meta.name, "stremio://movie/${meta.id}", tvType) {
                        posterUrl = meta.poster
                    }
                } else {
                    newTvSeriesSearchResponse(meta.name, "stremio://series/${meta.id}", tvType) {
                        posterUrl = meta.poster
                    }
                }
            }
        } catch (e: Throwable) {
            logError(e)
            null
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        // url is a stremio:// link we synthesized in [search]
        val stremioLink = url.removePrefix("stremio://")
        val parts = stremioLink.split("/", limit = 3)
        if (parts.size < 3) return null
        val (type, id) = parts
        val resolvedType = if (type == "series") "series" else "movie"

        val meta = try {
            val metaResp = app.get("$mainUrl/meta/$resolvedType/$id.json")
            json.decodeFromString(StremioMeta.serializer(), metaResp.text)
        } catch (e: Throwable) {
            logError(e); return null
        }

        val streams = try {
            val sResp = app.get(streamUrl(resolvedType, id))
            json.decodeFromString(StremioStreamResponse.serializer(), sResp.text).streams
        } catch (e: Throwable) {
            logError(e); emptyList()
        }

        // Pack the first playable stream URL into the load response so the
        // player can launch directly.
        val firstStream = streams.firstOrNull { it.url != null }
        val playableUrl = firstStream?.url ?: ""
        return when (resolvedType) {
            "movie" -> newMovieLoadResponse(
                meta.name,
                url,
                TvType.Movie,
                playableUrl,
            ) {
                this.posterUrl = meta.poster
                this.plot = meta.description
                this.year = meta.releaseInfo?.toIntOrNull()
                this.backgroundPosterUrl = meta.background
                this.logoUrl = meta.logo
            }
            else -> {
                val episodes = (meta.videos ?: emptyList()).map { ep ->
                    newEpisode(playableUrl) {
                        this.name = ep.title
                        this.season = ep.season
                        this.episode = ep.episode
                        this.posterUrl = ep.thumbnail
                        this.description = ep.overview
                    }
                }
                newTvSeriesLoadResponse(
                    meta.name,
                    url,
                    TvType.TvSeries,
                    episodes,
                ) {
                    this.posterUrl = meta.poster
                    this.plot = meta.description
                    this.year = meta.releaseInfo?.toIntOrNull()
                    this.backgroundPosterUrl = meta.background
                    this.logoUrl = meta.logo
                }
            }
        }
    }
}
