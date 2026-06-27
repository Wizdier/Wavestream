@file:Suppress("UNUSED", "unused", "MemberVisibilityCanBePrivate")

package com.lagradost.cloudstream3

// AudioFile, IDownloadableMinimum, ProvidersInfoJson, SettingsJson are all
// defined in MainAPI.kt — this file only adds the Filter framework and
// ProviderInfoStore on top.

// ─────────────────────────────────────────────────────────────────────────────
//  Filter framework — CS3 plugins use these for provider-specific search filters
// ─────────────────────────────────────────────────────────────────────────────

abstract class Filter<T>(val name: String) {
    var values: Array<T> = arrayOf()
    var selected: T? = null
    open fun getValue(): T? = selected
}

class TextFilter(name: String) : Filter<String>(name)
class SortFilter(name: String) : Filter<String>(name)
class CheckBoxFilter(name: String) : Filter<Boolean>(name)
class DropdownFilter(name: String, val options: List<String> = emptyList()) : Filter<String>(name)
class HeaderFilter(name: String) : Filter<String>(name)
class SeparatorFilter : Filter<String>("")

class FilterList(val filters: List<Filter<*>> = listOf()) {
    fun getFilter(name: String): Filter<*>? = filters.firstOrNull { it.name == name }
    fun <T> getFilterValue(name: String): T? {
        val filter = filters.firstOrNull { it.name == name } ?: return null
        @Suppress("UNCHECKED_CAST")
        return filter.selected as? T
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ProviderInfoStore — per-provider settings storage (CS3 plugins read this)
// ─────────────────────────────────────────────────────────────────────────────

object ProviderInfoStore {
    private val store = java.util.concurrent.ConcurrentHashMap<String, ProvidersInfoJson>()

    fun get(providerName: String): ProvidersInfoJson? = store[providerName]
    fun set(providerName: String, info: ProvidersInfoJson) { store[providerName] = info }
    fun all(): Map<String, ProvidersInfoJson> = store.toMap()
    fun clear() = store.clear()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Extensions on MainAPI for the new-style API surface
//  (added as extension functions to avoid modifying the abstract class)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Get a single main page's items (some plugins override this instead of getMainPage).
 * Default delegates to getMainPage and returns the first list's items.
 */
suspend fun MainAPI.getMainPageItems(page: Int, request: MainPageRequest): List<SearchResponse>? {
    return getMainPage(page, request)?.items?.firstOrNull()?.items
}

/**
 * Get a sub-page (used for category browsing — e.g. clicking "Action" on a provider).
 * Default returns null — provider must override to support category browsing.
 */
suspend fun MainAPI.getSub(page: Int, request: MainPageRequest): HomePageResponse? = null

/**
 * New-style loadLinks — CS3 3.x renamed version. Default delegates to loadLinks.
 */
suspend fun MainAPI.extractLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean = loadLinks(data, isCasting, subtitleCallback, callback)

/**
 * Search with filter (newer CS3 API). Default ignores filter and calls search(query).
 */
suspend fun MainAPI.searchFiltered(query: String, filter: FilterList?): List<SearchResponse>? {
    return search(query)
}

/**
 * Load with newer signature that takes callbacks for streaming extraction.
 * Default delegates to load(url) and ignores the callbacks — provider must
 * override to extract links inline.
 */
suspend fun MainAPI.loadWithCallbacks(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): LoadResponse? = load(url)
