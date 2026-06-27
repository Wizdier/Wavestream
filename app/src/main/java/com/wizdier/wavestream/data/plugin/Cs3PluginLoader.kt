package com.wizdier.wavestream.data.plugin

import android.content.Context
import android.util.Log
import com.wizdier.wavestream.data.api.CatalogType
import com.wizdier.wavestream.data.api.LoadLinksResponse
import com.wizdier.wavestream.data.api.LoadResponse
import com.wizdier.wavestream.data.api.Provider
import com.wizdier.wavestream.data.api.Quality
import com.wizdier.wavestream.data.api.SearchFilter
import com.wizdier.wavestream.data.api.SearchResponse
import com.wizdier.wavestream.data.api.SubtitleFile
import com.wizdier.wavestream.data.api.VideoLink
import com.wizdier.wavestream.data.api.HomePageList
import com.wizdier.wavestream.data.api.HomePageResponse
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.DexClassLoader
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

/**
 * Loads CloudStream 3 `.cs3` plugin files at runtime.
 *
 * A `.cs3` file is a ZIP archive containing:
 *  - `manifest.json` — `{ pluginClassName, name, version, requiresResources }`
 *  - `classes.dex`   — compiled Kotlin code referencing `com.lagradost.cloudstream3.*`
 *
 * Loading flow:
 *  1. Open the .cs3 as a ZIP
 *  2. Extract manifest.json to read `pluginClassName`
 *  3. Extract classes.dex to a cache file
 *  4. Build a [DexClassLoader] with WaveStream's classloader as parent —
 *     this lets the plugin resolve the `com.lagradost.cloudstream3.*`
 *     stub classes shipped inside WaveStream itself
 *  5. Load the `pluginClassName` class, instantiate it (must be a [Plugin])
 *  6. Call `plugin.load(context)` — the plugin registers providers via
 *     `registerMainAPI(api)`, which adds them to [APIHolder.allProviders]
 *  7. Wrap each registered [com.lagradost.cloudstream3.MainAPI] as a
 *     WaveStream [Provider] via [Cs3ProviderAdapter]
 */
class Cs3PluginLoader(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val dexCacheDir: File by lazy {
        File(context.codeCacheDir, "cs3-plugins").apply { mkdirs() }
    }

    /**
     * Load a .cs3 file from disk. Returns the list of WaveStream Providers
     * that were registered (may be empty if the plugin failed or registered
     * nothing). Errors are logged and swallowed so one bad plugin doesn't
     * break the whole loader.
     */
    fun load(cs3File: File): List<Provider> {
        val providersBefore = APIHolder.allProviders.toSet()
        val pluginName = cs3File.nameWithoutExtension

        return runCatching {
            ZipFile(cs3File).use { zip ->
                // 1. Read manifest
                val manifestEntry = zip.getEntry("manifest.json")
                    ?: throw IllegalStateException("manifest.json not found in ${cs3File.name}")
                val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                val manifest = json.decodeFromString(
                    Manifest.serializer(), manifestJson
                )
                val pluginClassName = manifest.pluginClassName
                    ?: throw IllegalStateException("pluginClassName missing in manifest")

                // 2. Extract classes.dex to cache
                val dexEntry = zip.getEntry("classes.dex")
                    ?: throw IllegalStateException("classes.dex not found in ${cs3File.name}")
                val dexFile = File(dexCacheDir, "${cs3File.nameWithoutExtension}.dex")
                if (!dexFile.exists() || dexFile.length() == 0L) {
                    zip.getInputStream(dexEntry).use { input ->
                        dexFile.outputStream().use { input.copyTo(it) }
                    }
                }

                // 3. Build DexClassLoader — parent is the app classloader so
                //    the plugin .dex can resolve the cloudstream3 stub classes
                //    shipped inside WaveStream's own APK.
                val classLoader = DexClassLoader(
                    dexFile.absolutePath,
                    dexCacheDir.absolutePath,
                    null,
                    context.classLoader
                )

                // 4. Instantiate the Plugin subclass
                val pluginClass = classLoader.loadClass(pluginClassName)
                val plugin = pluginClass.getDeclaredConstructor().newInstance() as? Plugin
                    ?: throw IllegalStateException(
                        "$pluginClassName is not a CloudstreamPlugin.Plugin subclass"
                    )
                plugin.filename = cs3File.absolutePath

                // 5. Call load(context) — plugin registers providers
                runCatching { plugin.load(context) }
                    .onFailure { Log.e(TAG, "Plugin ${manifest.name} load() failed", it) }

                // 6. Wrap any newly-registered MainAPI instances as Providers
                val newProviders = APIHolder.allProviders.filter { it !in providersBefore }
                Log.i(
                    TAG,
                    "Loaded ${manifest.name}: ${newProviders.size} provider(s) registered"
                )
                newProviders.map { api ->
                    Cs3ProviderAdapter(api).also {
                        Log.i(TAG, "  + ${api.name} (${api.mainUrl}) — ${api.supportedTypes}")
                    }
                }
            }
        }.onFailure {
            Log.e(TAG, "Failed to load ${cs3File.name}", it)
        }.getOrDefault(emptyList())
    }

    @kotlinx.serialization.Serializable
    data class Manifest(
        val name: String? = null,
        val pluginClassName: String? = null,
        val requiresResources: Boolean = false,
        val version: Int? = null
    )

    companion object {
        private const val TAG = "Cs3PluginLoader"
    }
}

/**
 * Adapts a CS3 [com.lagradost.cloudstream3.MainAPI] instance into WaveStream's
 * [Provider] interface. Uses the same Kotlin types — CS3 responses are
 * directly mapped to WaveStream responses.
 */
class Cs3ProviderAdapter(
    private val api: com.lagradost.cloudstream3.MainAPI
) : Provider {

    override val id: String = "cs3.${api::class.simpleName ?: api.name}"
    override val name: String = api.name
    override val supportedTypes: Set<CatalogType> = api.supportedTypes.map { it.toWave() }.toSet()
    override val languages: Set<String> = if (api.lang.isNotEmpty()) setOf(api.lang) else setOf("en")

    override suspend fun getMainPage(page: Int): HomePageResponse? {
        if (!api.hasMainPage) return null
        val request = api.mainPage.getOrNull(page - 1) ?: return null
        val resp = runCatching {
            api.getMainPage(page, com.lagradost.cloudstream3.MainPageRequest(request.name, request))
        }.getOrNull() ?: return null
        return HomePageResponse(
            lists = resp.items.map { list ->
                HomePageList(
                    name = list.name,
                    items = list.items.map { it.toWave() }
                )
            }
        )
    }

    override suspend fun search(query: String, filter: SearchFilter): List<SearchResponse> {
        return runCatching { api.search(query) }
            .getOrNull()
            ?.orEmpty()
            ?.map { it.toWave() }
            .orEmpty()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        if (!api.hasQuickSearch) return emptyList()
        return runCatching { api.quickSearch(query) }
            .getOrNull()
            ?.orEmpty()
            ?.map { it.toWave() }
            .orEmpty()
    }

    override suspend fun load(url: String): LoadResponse? {
        return runCatching { api.load(url) }
            .getOrNull()
            ?.toWave()
    }

    override suspend fun loadLinks(url: String): LoadLinksResponse {
        val videos = mutableListOf<VideoLink>()
        val subtitles = mutableListOf<SubtitleFile>()

        runCatching {
            api.loadLinks(
                data = url,
                isCasting = false,
                subtitleCallback = { sub ->
                    subtitles += SubtitleFile(
                        lang = sub.lang ?: "und",
                        url = sub.url,
                        format = com.wizdier.wavestream.data.api.SubtitleFormat.VTT
                    )
                },
                callback = { link ->
                    videos += VideoLink(
                        name = link.name,
                        url = link.url,
                        quality = Quality.fromString(link.quality.toString()),
                        headers = link.headers,
                        referer = link.referer.takeIf { it.isNotBlank() }
                    )
                }
            )
        }.onFailure {
            android.util.Log.e("Cs3ProviderAdapter", "loadLinks failed for ${api.name}", it)
        }

        return LoadLinksResponse(videos = videos, subtitles = subtitles)
    }

    // ── Mapping helpers ──────────────────────────────────────────────────

    private fun com.lagradost.cloudstream3.TvType.toWave(): CatalogType = when (this) {
        com.lagradost.cloudstream3.TvType.Movie, com.lagradost.cloudstream3.TvType.AnimeMovie -> CatalogType.MOVIES
        com.lagradost.cloudstream3.TvType.TvSeries, com.lagradost.cloudstream3.TvType.AsianDrama -> CatalogType.SERIES
        com.lagradost.cloudstream3.TvType.Anime, com.lagradost.cloudstream3.TvType.OVA -> CatalogType.ANIME
        com.lagradost.cloudstream3.TvType.Documentary -> CatalogType.DOCUMENTARIES
        com.lagradost.cloudstream3.TvType.Live -> CatalogType.LIVE
        else -> CatalogType.OTHER
    }

    private fun com.lagradost.cloudstream3.SearchResponse.toWave(): SearchResponse = SearchResponse(
        id = url,
        name = name,
        url = url,
        type = (type ?: com.lagradost.cloudstream3.TvType.Others).toWave(),
        posterUrl = posterUrl,
        backdropUrl = (this as? com.lagradost.cloudstream3.MovieSearchResponse)?.backgroundPosterUrl,
        year = (this as? com.lagradost.cloudstream3.MovieSearchResponse)?.year
            ?: (this as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year
            ?: (this as? com.lagradost.cloudstream3.AnimeSearchResponse)?.year,
        rating = score?.let { (it.value ?: it.imdbRating ?: it.malRating ?: it.tmdbRating) }
            ?: (this as? com.lagradost.cloudstream3.MovieSearchResponse)?.rating?.toDouble(),
        qualityLabel = quality?.name,
        providerName = apiName,
        providerId = "cs3.${api::class.simpleName ?: api.name}"
    )

    private fun com.lagradost.cloudstream3.LoadResponse.toWave(): LoadResponse {
        val type = (type ?: com.lagradost.cloudstream3.TvType.Others).toWave()
        val seasonList: List<com.wizdier.wavestream.data.api.Season> = emptyList()
        val episodes: List<com.wizdier.wavestream.data.api.Episode> = when (this) {
            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> episodes.map { it.toWave() }
            is com.lagradost.cloudstream3.AnimeLoadResponse -> episodes.map { it.toWave() }
            else -> emptyList()
        }
        return LoadResponse(
            id = url,
            name = name,
            url = url,
            type = type,
            posterUrl = posterUrl,
            backdropUrl = backgroundPosterUrl,
            rating = score?.let { (it.value ?: it.imdbRating ?: it.malRating ?: it.tmdbRating) }
                ?: rating?.toDouble(),
            description = synopsys,
            tags = tags.orEmpty(),
            cast = actors?.mapNotNull { it.actor?.name }.orEmpty(),
            duration = duration,
            episodes = episodes,
            seasons = seasonList,
            providerName = apiName,
            providerId = "cs3.${api::class.simpleName ?: api.name}"
        )
    }

    private fun com.lagradost.cloudstream3.Episode.toWave(): com.wizdier.wavestream.data.api.Episode =
        com.wizdier.wavestream.data.api.Episode(
            id = id?.toString() ?: url ?: name ?: "",
            name = name ?: "Episode ${episode ?: 1}",
            episode = episode ?: 1,
            season = season ?: 1,
            description = description,
            posterUrl = posterUrl,
            duration = runtime?.toString(),
            airDate = date?.toString(),
            rating = score?.value
        )
}
