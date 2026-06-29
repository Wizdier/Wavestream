package com.wavestream.features.search

import com.wavestream.core.storage.DataStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class SearchHistoryItem(
    val query: String,
    val timestamp: Long,
    val resultCount: Int = 0,
)

private const val KEY = "search_history_v2"
private const val MAX_ITEMS = 50
private val serializer = SearchHistoryItem.serializer()
private val listSerializer = ListSerializer(serializer)

object SearchHistoryRepository {
    fun load(): List<SearchHistoryItem> {
        return DataStore.getSerializedList(KEY, serializer) ?: emptyList()
    }

    fun add(query: String, resultCount: Int = 0) {
        val current = load().toMutableList()
        current.removeAll { it.query.equals(query, ignoreCase = true) }
        current.add(0, SearchHistoryItem(query.trim(), System.currentTimeMillis(), resultCount))
        while (current.size > MAX_ITEMS) current.removeAt(current.lastIndex)
        DataStore.setSerializedList(KEY, current, serializer)
    }

    fun remove(query: String) {
        val current = load().toMutableList()
        current.removeAll { it.query.equals(query, ignoreCase = true) }
        DataStore.setSerializedList(KEY, current, serializer)
    }

    fun clear() {
        DataStore.removeKey(KEY)
    }

    fun getSuggestions(prefix: String, limit: Int = 5): List<SearchHistoryItem> {
        if (prefix.isBlank()) return emptyList()
        return load().filter { it.query.contains(prefix, ignoreCase = true) }.take(limit)
    }
}
