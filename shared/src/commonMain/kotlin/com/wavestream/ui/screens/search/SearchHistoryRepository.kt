package com.wavestream.ui.screens.search

import com.wavestream.PlatformStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SearchHistoryItem(
    val query: String,
    val timestamp: Long,
    val resultCount: Int,
)

object SearchHistoryRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(SearchHistoryItem.serializer())
    private const val KEY = "search_history_v2"

    fun load(): List<SearchHistoryItem> {
        val raw = PlatformStorage.getString(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    fun add(query: String, resultCount: Int) {
        val current = load().toMutableList()
        current.removeAll { it.query == query }
        current.add(0, SearchHistoryItem(query, System.currentTimeMillis(), resultCount))
        if (current.size > 20) current.subList(20, current.size).clear()
        save(current)
    }

    fun clear() {
        save(emptyList())
    }

    private fun save(list: List<SearchHistoryItem>) {
        val raw = json.encodeToString(serializer, list)
        PlatformStorage.putString(KEY, raw)
    }
}
