package com.wizdier.wavestream.data.plugin
import android.content.Context
import android.util.Log
import com.wizdier.wavestream.data.api.*
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.DexClassLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

class Cs3PluginLoader(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dexCacheDir: File by lazy { File(context.codeCacheDir, "cs3-plugins").apply { mkdirs() } }

    fun load(cs3File: File): List<Provider> {
        val providersBefore = APIHolder.allProviders.toSet()
        return runCatching {
            ZipFile(cs3File).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: throw IllegalStateException("manifest.json not found")
                val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                val manifest = json.decodeFromString(Manifest.serializer(), manifestJson)
                val pluginClassName = manifest.pluginClassName ?: throw IllegalStateException("pluginClassName missing")
                val dexEntry = zip.getEntry("classes.dex") ?: throw IllegalStateException("classes.dex not found")
                val dexFile = File(dexCacheDir, "${cs3File.nameWithoutExtension}.dex")
                if (!dexFile.exists() || dexFile.length() == 0L) {
                    zip.getInputStream(dexEntry).use { input -> dexFile.outputStream().use { input.copyTo(it) } }
                }
                val classLoader = DexClassLoader(dexFile.absolutePath, dexCacheDir.absolutePath, null, context.classLoader)
                val pluginClass = classLoader.loadClass(pluginClassName)
                val plugin = pluginClass.getDeclaredConstructor().newInstance() as? Plugin ?: throw IllegalStateException("$pluginClassName is not a Plugin")
                plugin.filename = cs3File.absolutePath
                runCatching { plugin.load(context) }.onFailure { Log.e(TAG, "Plugin load() failed", it) }
                val newProviders = APIHolder.allProviders.filter { it !in providersBefore }
                Log.i(TAG, "Loaded ${manifest.name}: ${newProviders.size} provider(s)")
                newProviders.map { Cs3ProviderAdapter(it) }
            }
        }.onFailure { Log.e(TAG, "Failed to load ${cs3File.name}", it) }.getOrDefault(emptyList())
    }

    @Serializable data class Manifest(val name: String? = null, val pluginClassName: String? = null, val requiresResources: Boolean = false, val version: Int? = null)
    companion object { private const val TAG = "Cs3PluginLoader" }
}

class Cs3ProviderAdapter(private val api: com.lagradost.cloudstream3.MainAPI) : Provider {
    override val id: String = "cs3.${api::class.simpleName ?: api.name}"
    override val name: String = api.name
    override val supportedTypes: Set<CatalogType> = api.supportedTypes.map { it.toWave() }.toSet()
    override val languages: Set<String> = if (api.lang.isNotEmpty()) setOf(api.lang) else setOf("en")

    override suspend fun getMainPage(page: Int): HomePageResponse? {
        if (!api.hasMainPage) return null
        val request = api.mainPage.getOrNull(page - 1) ?: return null
        val resp = runCatching { api.getMainPage(page, com.lagradost.cloudstream3.MainPageRequest(request.name, request)) }.getOrNull() ?: return null
        return HomePageResponse(resp.items.map { HomePageList(it.name, it.items.map { it.toWave() }) })
    }
    override suspend fun search(query: String, filter: SearchFilter): List<SearchResponse> = runCatching { api.search(query) }.getOrNull()?.orEmpty()?.map { it.toWave() }.orEmpty()
    override suspend fun quickSearch(query: String): List<SearchResponse> = if (!api.hasQuickSearch) emptyList() else runCatching { api.quickSearch(query) }.getOrNull()?.orEmpty()?.map { it.toWave() }.orEmpty()
    override suspend fun load(url: String): LoadResponse? = runCatching { api.load(url) }.getOrNull()?.toWave()
    override suspend fun loadLinks(url: String): LoadLinksResponse {
        val videos = mutableListOf<VideoLink>(); val subtitles = mutableListOf<SubtitleFile>()
        runCatching {
            api.loadLinks(url, false, { sub -> subtitles.add(SubtitleFile(sub.lang ?: "und", sub.url)) }, { link ->
                videos.add(VideoLink(link.name, link.url, Quality.fromString(link.quality.toString()), link.headers, link.referer.takeIf { it.isNotBlank() }))
            })
        }
        return LoadLinksResponse(videos, subtitles)
    }

    private fun com.lagradost.cloudstream3.TvType.toWave(): CatalogType = when (this) {
        com.lagradost.cloudstream3.TvType.Movie, com.lagradost.cloudstream3.TvType.AnimeMovie -> CatalogType.MOVIES
        com.lagradost.cloudstream3.TvType.TvSeries, com.lagradost.cloudstream3.TvType.AsianDrama -> CatalogType.SERIES
        com.lagradost.cloudstream3.TvType.Anime, com.lagradost.cloudstream3.TvType.OVA -> CatalogType.ANIME
        com.lagradost.cloudstream3.TvType.Documentary -> CatalogType.DOCUMENTARIES
        com.lagradost.cloudstream3.TvType.Live -> CatalogType.LIVE
        else -> CatalogType.OTHER
    }
    private fun com.lagradost.cloudstream3.SearchResponse.toWave(): SearchResponse = SearchResponse(id = url, name = name, url = url, type = (type ?: com.lagradost.cloudstream3.TvType.Others).toWave(), posterUrl = posterUrl, backdropUrl = (this as? com.lagradost.cloudstream3.MovieSearchResponse)?.backgroundPosterUrl, year = (this as? com.lagradost.cloudstream3.MovieSearchResponse)?.year ?: (this as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year, rating = score?.value, qualityLabel = quality?.name, providerName = apiName, providerId = "cs3.${api::class.simpleName ?: api.name}")
    private fun com.lagradost.cloudstream3.LoadResponse.toWave(): LoadResponse = LoadResponse(id = url, name = name, url = url, type = (type ?: com.lagradost.cloudstream3.TvType.Others).toWave(), posterUrl = posterUrl, backdropUrl = backgroundPosterUrl, rating = score?.value, description = synopsys, tags = tags.orEmpty(), cast = actors?.mapNotNull { it.actor?.name }.orEmpty(), duration = duration, episodes = when (this) { is com.lagradost.cloudstream3.TvSeriesLoadResponse -> episodes.map { it.toWave() }; is com.lagradost.cloudstream3.AnimeLoadResponse -> episodes.map { it.toWave() }; else -> emptyList() }, seasons = emptyList(), providerName = apiName, providerId = "cs3.${api::class.simpleName ?: api.name}")
    private fun com.lagradost.cloudstream3.Episode.toWave(): Episode = Episode(id = id?.toString() ?: url ?: name ?: "", name = name ?: "Episode ${episode ?: 1}", episode = episode ?: 1, season = season ?: 1, description = description, posterUrl = posterUrl, duration = runtime?.toString(), airDate = date?.toString(), rating = score?.value)
}
