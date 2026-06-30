package com.wavestream.ui.screens.search

import com.wavestream.PlatformStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable data class SearchHistoryItem(val query: String, val timestamp: Long, val resultCount: Int)

object SearchHistoryRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ser = ListSerializer(SearchHistoryItem.serializer())
    private const val KEY = "search_history_v2"
    fun load(): List<SearchHistoryItem> { val raw = PlatformStorage.getString(KEY) ?: return emptyList(); return runCatching { json.decodeFromString(ser, raw) }.getOrDefault(emptyList()) }
    fun add(query: String, resultCount: Int) { val c = load().toMutableList(); c.removeAll { it.query == query }; c.add(0, SearchHistoryItem(query, System.currentTimeMillis(), resultCount)); if (c.size > 20) c.subList(20, c.size).clear(); save(c) }
    fun clear() { save(emptyList()) }
    private fun save(list: List<SearchHistoryItem>) { PlatformStorage.putString(KEY, json.encodeToString(ser, list)) }
}
