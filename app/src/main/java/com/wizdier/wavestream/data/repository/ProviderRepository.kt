package com.wizdier.wavestream.data.repository

import com.wizdier.wavestream.data.api.HomePageResponse
import com.wizdier.wavestream.data.api.LoadLinksResponse
import com.wizdier.wavestream.data.api.LoadResponse
import com.wizdier.wavestream.data.api.Provider
import com.wizdier.wavestream.data.api.SearchFilter
import com.wizdier.wavestream.data.api.SearchResponse
import com.wizdier.wavestream.data.plugin.PluginLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Single entry point used by ViewModels for all provider interactions.
 * Aggregates results across every installed provider — WaveStream's
 * multi-source picker is built on top of this aggregated stream.
 */
class ProviderRepository(private val pluginLoader: PluginLoader) {

    val providers get() = pluginLoader.providers

    suspend fun initialize() = pluginLoader.initialize()

    suspend fun reload() = pluginLoader.reload()

    fun byId(id: String): Provider? = pluginLoader.byId(id)

    /**
     * Merge the home pages of every installed provider into a single
     * [HomePageResponse]. Failures in individual providers are swallowed
     * so one broken extension won't blank out the whole Home tab.
     */
    suspend fun aggregateHomePage(): HomePageResponse = coroutineScope {
        val lists = providers.value.map { p ->
            async {
                runCatching { p.getMainPage() }.getOrNull()?.lists.orEmpty()
            }
        }.awaitAll().flatten()
        HomePageResponse(lists)
    }

    /**
     * Search every installed provider in parallel and merge results.
     * The [filter.providerIds] set, when non-empty, restricts the search to
     * those providers only — this is what powers the per-source filter chip
     * on the Search screen.
     */
    suspend fun search(query: String, filter: SearchFilter = SearchFilter()): List<SearchResponse> = coroutineScope {
        val pool = providers.value.filter { filter.providerIds.isEmpty() || it.id in filter.providerIds }
        pool.map { p ->
            async {
                runCatching { p.search(query, filter) }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().distinctBy { it.url }
    }

    suspend fun load(providerId: String, url: String): LoadResponse? {
        val p = byId(providerId) ?: return null
        return runCatching { p.load(url) }.getOrNull()
    }

    suspend fun loadLinks(providerId: String, url: String): LoadLinksResponse {
        val p = byId(providerId) ?: return LoadLinksResponse(emptyList())
        return runCatching { p.loadLinks(url) }.getOrDefault(LoadLinksResponse(emptyList()))
    }
}
