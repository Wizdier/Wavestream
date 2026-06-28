package com.wavestream.features.search

import com.wavestream.core.storage.DataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable

/**
 * Search history item — a previously-executed search query.
 */
@Serializable
data class SearchHistoryItem(
    val query: String,
    val timestamp: Long,
    val resultCount: Int = 0,
)

/**
 * Search history repository — persists recent searches via DataStore.
 *
 * Mirrors CloudStream's search history persistence.
 */
object SearchHistoryRepository {
    private const val KEY = "search_history"
    private const val MAX_ITEMS = 50

    fun load(): List<SearchHistoryItem> {
        @Suppress("UNCHECKED_CAST")
        return DataStore.getKey(KEY, List::class.java) as? List<SearchHistoryItem> ?: emptyList()
    }

    fun add(query: String, resultCount: Int = 0) {
        val current = load().toMutableList()
        // Remove existing entry for this query (we'll re-add it at the top)
        current.removeAll { it.query.equals(query, ignoreCase = true) }
        current.add(0, SearchHistoryItem(query.trim(), System.currentTimeMillis(), resultCount))
        // Trim to MAX_ITEMS
        while (current.size > MAX_ITEMS) current.removeAt(current.lastIndex)
        DataStore.setKey(KEY, current)
    }

    fun remove(query: String) {
        val current = load().toMutableList()
        current.removeAll { it.query.equals(query, ignoreCase = true) }
        DataStore.setKey(KEY, current)
    }

    fun clear() {
        DataStore.removeKey(KEY)
    }

    /**
     * Get suggestions based on search history (prefix match).
     */
    fun getSuggestions(prefix: String, limit: Int = 5): List<SearchHistoryItem> {
        if (prefix.isBlank()) return emptyList()
        return load().filter { it.query.contains(prefix, ignoreCase = true) }.take(limit)
    }
}

/**
 * Search suggestion API — fetches query suggestions from external sources.
 *
 * Some providers support a `quickSearch` method that returns quick results
 * as the user types. This repository aggregates them.
 */
object SearchSuggestionRepository {

    /**
     * Get search suggestions from all providers' quickSearch.
     * Mirrors CloudStream's SearchSuggestionApi.
     */
    suspend fun getSuggestions(query: String): List<String> {
        if (query.length < 2) return emptyList()
        val providers = com.wavestream.api.APIHolder.apis.toList().filter { it.hasQuickSearch }
        val suggestions = mutableSetOf<String>()

        kotlinx.coroutines.coroutineScope {
            val deferred = providers.map { api ->
                this.async {
                    try {
                        val repo = com.wavestream.api.APIRepository(api)
                        when (val res = repo.quickSearch(query)) {
                            is com.wavestream.api.Resource.Success -> res.value.items.map { it.name }
                            else -> emptyList()
                        }
                    } catch (e: Throwable) { emptyList() }
                }
            }
            deferred.awaitAll().forEach { suggestions.addAll(it) }
        }
        return suggestions.take(10)
    }
}

private suspend fun <T> List<kotlinx.coroutines.Deferred<T>>.awaitAll(): List<T> =
    kotlinx.coroutines.awaitAll(*this.toTypedArray())
