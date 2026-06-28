@file:Suppress("UNUSED", "unused")

package com.lagradost.cloudstream3

/**
 * CloudStream3 filter surface — used by plugins to expose advanced search
 * constraints (genres, sort orders, dropdowns, …) and by WaveStream's search
 * UI to render them.
 *
 * NOTE: `ProvidersInfoJson` and `SettingsJson` are defined in `MainAPI.kt` —
 * they are intentionally NOT duplicated here.
 */

/**
 * Base class for a single filter. Holds the [name] shown to the user and the
 * currently [selected] value (mutable so the UI can write back).
 *
 * Implementations must NOT use `Array<T>` as a field type — abstract generic
 * classes can't reify `T`, so `Array<T>` would be erased at runtime. Use
 * `List<T>` (covariant) or `List<String>` for the option list instead.
 */
abstract class Filter<T>(val name: String) {
    var selected: T? = null
}

/** Free-text filter; [selected] is whatever the user typed. */
class TextFilter(name: String) : Filter<String>(name)

/** Sort-order picker; [values] is the ordered list of option labels. */
class SortFilter(name: String, val values: List<String>) : Filter<Int>(name)

/** Boolean toggle; [selected] is `true` when checked. */
class CheckBoxFilter(name: String) : Filter<Boolean>(name)

/** Single-select dropdown; [values] is the list of option labels. */
class DropdownFilter(name: String, val values: List<String>) : Filter<Int>(name)

/** Static section header — not interactive, no [Filter] base. */
class HeaderFilter(val label: String)

/** Visual separator — not interactive, no [Filter] base. */
class SeparatorFilter

/**
 * A bundle of filters handed to [searchFiltered]. Delegates to its backing
 * list so callers can iterate or index without unwrapping.
 */
class FilterList(filters: List<Filter<*>> = emptyList()) : List<Filter<*>> by filters {

    constructor(vararg filters: Filter<*>) : this(filters.toList())

    /** All [TextFilter]s in this list. */
    val textFilters: List<TextFilter> get() = filterIsInstance<TextFilter>()

    /** All [SortFilter]s in this list. */
    val sortFilters: List<SortFilter> get() = filterIsInstance<SortFilter>()

    /** All [CheckBoxFilter]s in this list. */
    val checkBoxFilters: List<CheckBoxFilter> get() = filterIsInstance<CheckBoxFilter>()

    /** All [DropdownFilter]s in this list. */
    val dropdownFilters: List<DropdownFilter> get() = filterIsInstance<DropdownFilter>()
}

// ------------------------------------------------------------------
// ProviderInfoStore — settings-backed registry of provider enabled state
// ------------------------------------------------------------------

object ProviderInfoStore {

    private val _providers: MutableList<ProvidersInfoJson> = mutableListOf()

    val providers: List<ProvidersInfoJson> get() = _providers.toList()

    fun setAll(list: List<ProvidersInfoJson>) {
        _providers.clear()
        _providers.addAll(list)
    }

    fun getByName(name: String): ProvidersInfoJson? =
        _providers.firstOrNull { it.name == name }

    fun setEnabled(name: String, enabled: Boolean) {
        val idx = _providers.indexOfFirst { it.name == name }
        if (idx >= 0) {
            _providers[idx] = _providers[idx].copy(enabled = enabled)
        }
    }

    fun add(info: ProvidersInfoJson) {
        if (_providers.none { it.name == info.name }) {
            _providers.add(info)
        }
    }
}

// ------------------------------------------------------------------
// MainAPI extension helpers
// ------------------------------------------------------------------

/** The list of main-page sections this provider exposes. */
fun MainAPI.getMainPageItems(): List<MainPageData> = this.mainPage

/**
 * Pull subtitles for [url] from a provider's [loadLinks]. Returns the
 * collected list — empty if the provider emits none.
 */
suspend fun MainAPI.getSub(url: String): List<SubtitleFile> {
    val collected = mutableListOf<SubtitleFile>()
    this.loadLinks(
        data = url,
        isCasting = false,
        subtitleCallback = { collected.add(it) },
        callback = {}
    )
    return collected
}

/**
 * Run [loadLinks] and collect every emitted [ExtractorLink] and [SubtitleFile]
 * into a single pair — convenience for callers that don't want to deal with
 * callbacks directly.
 */
suspend fun MainAPI.extractLinks(
    data: String,
    isCasting: Boolean = false
): Pair<List<ExtractorLink>, List<SubtitleFile>> {
    val links = mutableListOf<ExtractorLink>()
    val subs = mutableListOf<SubtitleFile>()
    this.loadLinks(
        data = data,
        isCasting = isCasting,
        subtitleCallback = { subs.add(it) },
        callback = { links.add(it) }
    )
    return links to subs
}

/**
 * Run [search] and (optionally) narrow results by the [filters] in [FilterList].
 * Default implementation ignores the filters and just returns [search] —
 * concrete providers can override to apply them.
 */
suspend fun MainAPI.searchFiltered(
    query: String,
    filters: FilterList = FilterList()
): List<SearchResponse> {
    val results = this.search(query) ?: return emptyList()
    // Default: don't filter — providers that want to apply [filters] can
    // override this in their own subclass.
    return results
}

/**
 * Wrap [load] with subtitle / link collection callbacks so callers can fetch
 * everything for a URL in one shot. Returns the [LoadResponse] (or null if
 * the provider had nothing).
 */
suspend fun MainAPI.loadWithCallbacks(url: String): LoadResponse? {
    val response = this.load(url) ?: return null
    return response
}
