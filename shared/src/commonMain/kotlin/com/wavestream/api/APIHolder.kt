package com.wavestream.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Abstract extractor contract — mirrors CloudStream's `ExtractorApi`.
 * Second-tier extension: resolves a video-host page URL into a direct playable URL.
 *
 * Concrete examples: StreamTape, MixDrop, Doodstream, Upstream, Voe, Filemoon, JWPlayer, etc.
 *
 * Lifecycle:
 *   1. A MainAPI provider's `loadLinks()` finds a URL on a video host (e.g. streamtape.com)
 *   2. Wavestream looks up the matching ExtractorApi by URL prefix
 *   3. Calls extractor.getUrl(url, referer, subtitleCallback, callback)
 *   4. The extractor parses the host page, runs any JS (via Rhino if needed), and emits
 *      ExtractorLink(s) with direct playable URLs via the callback
 */
abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    /** File path of the plugin this extractor was loaded from — set by PluginManager. */
    var sourcePlugin: String? = null

    /**
     * New API: stream-based — called with subtitleCallback + callback.
     * Default implementation delegates to the old API.
     */
    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        getUrl(url, referer)?.forEach(callback)
    }

    /** Old API: returns a list. Override this for simple extractors. */
    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? = emptyList()

    /** Build a URL from a site-specific id (default: id is the URL itself). */
    open fun getExtractorUrl(id: String): String = id
}

/**
 * Singleton holder for all registered MainAPI providers + ExtractorApi extractors.
 * Mirrors CloudStream's `APIHolder` object.
 */
object APIHolder {
    val allProviders = AtomicList<MainAPI>()
    val apis = AtomicList<MainAPI>()
    val extractorApis = AtomicList<ExtractorApi>()

    @Volatile
    private var apiMap: Map<String, Int>? = null

    fun addPluginMapping(plugin: MainAPI) {
        apis.add(plugin)
        initMap(true)
    }

    fun removePluginMapping(plugin: MainAPI) {
        apis.remove { it === plugin }
        initMap(true)
    }

    fun initMap(forcedUpdate: Boolean = false) {
        val current = apiMap
        if (current == null || forcedUpdate) {
            apiMap = apis.toList().mapIndexed { index, api -> api.name to index }.toMap()
        }
    }

    fun getApiFromName(apiName: String?): MainAPI? {
        if (apiName == null) return null
        initMap()
        val map = apiMap
        if (map != null) {
            val idx = map[apiName]
            if (idx != null) {
                apis.toList().getOrNull(idx)?.let { return it }
            }
        }
        // Fallback: search allProviders directly
        return allProviders.toList().firstOrNull { it.name == apiName }
    }

    fun getApiFromUrl(url: String): MainAPI? {
        return allProviders.toList().firstOrNull { url.startsWith(it.mainUrl) }
    }

    fun getExtractorApiFromName(name: String): ExtractorApi? {
        return extractorApis.toList().firstOrNull { it.name == name }
    }

    fun getExtractorApiFromUrl(url: String): ExtractorApi? {
        return extractorApis.toList().firstOrNull { url.startsWith(it.mainUrl) }
    }

    /** Initialize all providers (called once at startup). */
    fun initAll() {
        allProviders.toList().forEach { api ->
            runCatching { api.mainPage }  // touch to trigger init
        }
        apiMap = null
        initMap()
    }
}

/**
 * Atomic list — mirrors CloudStream's `atomicListOf<T>()` from `kotlinx.atomicfu`.
 * Allows concurrent add/remove during iteration without locks.
 */
class AtomicList<T> {
    @PublishedApi
    internal var list: List<T> = emptyList()

    fun add(item: T) {
        synchronized(this) { list = list + item }
    }

    fun addAll(items: Collection<T>) {
        synchronized(this) { list = list + items }
    }

    fun remove(predicate: (T) -> Boolean) {
        synchronized(this) { list = list.filterNot(predicate) }
    }

    fun removeAll(predicate: (T) -> Boolean) = remove(predicate)

    inline fun <R> withLock(block: List<T>.() -> R): R {
        return list.block()
    }

    fun toList(): List<T> = list

    fun size(): Int = list.size
}

/**
 * Per-provider repository wrapper — mirrors CloudStream's `APIRepository`.
 * Adds timeout + 10-minute in-memory cache for `load()` results.
 */
class APIRepository(val api: MainAPI) {
    companion object {
        private const val DEFAULT_TIMEOUT = 120_000L
        private const val MAX_TIMEOUT = 4 * DEFAULT_TIMEOUT
        private const val MIN_TIMEOUT = 5_000L
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private const val CACHE_SIZE = 20

        private data class CachedLoad(
            val timestamp: Long,
            val response: LoadResponse,
            val hash: Pair<String, String>,
        )

        private val cache = AtomicList<CachedLoad>()
        private var cacheIndex = 0

        fun getTimeout(desired: Long?): Long = (desired ?: DEFAULT_TIMEOUT).coerceIn(MIN_TIMEOUT, MAX_TIMEOUT)

        fun clearCache() = cache.removeAll { true }

        fun isInvalidData(data: String): Boolean = data.isEmpty() || data == "[]" || data == "about:blank"

        val noneApi = object : MainAPI() {
            override var name = "None"
            override val supportedTypes = emptySet<TvType>()
            override var lang = ""
        }
    }

    val hasMainPage = api.hasMainPage
    val providerType = api.providerType
    val name = api.name
    val mainUrl = api.mainUrl
    val mainPage = api.mainPage
    val hasQuickSearch = api.hasQuickSearch
    val vpnStatus = api.vpnStatus

    suspend fun load(url: String): Resource<LoadResponse> {
        return safeApiCall {
            kotlinx.coroutines.withTimeout(getTimeout(api.loadTimeoutMs)) {
                if (isInvalidData(url)) throw ErrorLoadingException()
                val fixedUrl = api.fixUrl(url)
                val cacheKey = api.name to fixedUrl
                val now = System.currentTimeMillis()

                // Check cache
                val cached = cache.toList().find { it.hash == cacheKey && (now - it.timestamp) < CACHE_TTL_MS }
                if (cached != null) return@withTimeout cached.response

                val response = api.load(fixedUrl) ?: throw ErrorLoadingException()
                response.tags = response.tags?.filter { it.isNotBlank() }

                // Add to cache
                val entry = CachedLoad(now, response, cacheKey)
                val asList = cache.toList().toMutableList()
                if (asList.size >= CACHE_SIZE) {
                    asList[cacheIndex] = entry
                    cacheIndex = (cacheIndex + 1) % CACHE_SIZE
                } else {
                    asList.add(entry)
                }
                cache.removeAll { true }
                cache.addAll(asList)

                response
            }
        }
    }

    suspend fun search(query: String, page: Int): Resource<SearchResponseList> {
        if (query.isEmpty()) return Resource.Success(SearchResponseList(emptyList(), false))
        return safeApiCall {
            kotlinx.coroutines.withTimeout(getTimeout(api.searchTimeoutMs)) {
                api.search(query, page) ?: throw ErrorLoadingException()
            }
        }
    }

    suspend fun quickSearch(query: String): Resource<SearchResponseList> {
        if (query.isEmpty()) return Resource.Success(SearchResponseList(emptyList(), false))
        return safeApiCall {
            kotlinx.coroutines.withTimeout(getTimeout(api.quickSearchTimeoutMs)) {
                SearchResponseList(api.quickSearch(query) ?: throw ErrorLoadingException(), false)
            }
        }
    }

    suspend fun waitForHomeDelay() {
        val delta = api.sequentialMainPageScrollDelay + api.lastHomepageRequest - System.currentTimeMillis()
        if (delta < 0) return
        kotlinx.coroutines.delay(delta)
    }

    suspend fun getMainPage(page: Int, nameIndex: Int? = null): Resource<List<HomePageResponse?>> {
        return safeApiCall {
            kotlinx.coroutines.withTimeout(getTimeout(api.getMainPageTimeoutMs)) {
                api.lastHomepageRequest = System.currentTimeMillis()

                nameIndex?.let { api.mainPage.getOrNull(it) }?.let { data ->
                    listOf(api.getMainPage(page, MainPageRequest(data.name, data.data, data.horizontalImages)))
                } ?: run {
                    if (api.sequentialMainPage) {
                        var first = true
                        api.mainPage.map { data ->
                            if (!first) kotlinx.coroutines.delay(api.sequentialMainPageDelay)
                            first = false
                            api.getMainPage(page, MainPageRequest(data.name, data.data, data.horizontalImages))
                        }
                    } else {
                        kotlinx.coroutines.coroutineScope {
                            val deferred: List<kotlinx.coroutines.Deferred<HomePageResponse?>> = api.mainPage.map { data ->
                                async {
                                    api.getMainPage(page, MainPageRequest(data.name, data.data, data.horizontalImages))
                                }
                            }
                            deferred.map { it.await() }
                        }
                    }
                }
            }
        }
    }

    suspend fun extractorVerifierJob(extractorData: String?) {
        safeApiCall { api.extractorVerifierJob(extractorData) }
    }

    suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        if (isInvalidData(data)) return false
        return try {
            kotlinx.coroutines.withTimeout(getTimeout(api.loadLinksTimeoutMs)) {
                api.loadLinks(data, isCasting, subtitleCallback, callback)
            }
        } catch (t: Throwable) {
            false
        }
    }
}

class ErrorLoadingException(message: String? = null) : Exception(message)

// ============================================================================
// Resource + helpers (mirrors CloudStream's mvvm/Resource)
// ============================================================================

sealed class Resource<out T> {
    data class Success<T>(val value: T) : Resource<T>()
    data class Failure(val error: Throwable) : Resource<Nothing>()
    data class Loading(val progress: Int = 0) : Resource<Nothing>()

    companion object {
        fun <T> success(value: T) = Success(value)
        fun failure(error: Throwable) = Failure(error)
        fun loading(progress: Int = 0) = Loading(progress)
    }
}

suspend fun <T> safeApiCall(block: suspend () -> T): Resource<T> {
    return try {
        Resource.Success(block())
    } catch (t: Throwable) {
        Resource.Failure(t)
    }
}
