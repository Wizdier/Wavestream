package com.wavestream.features.bookmarks

import com.wavestream.api.SearchResponse
import com.wavestream.api.TvType
import com.wavestream.core.storage.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class Bookmark(
    val id: String, val apiName: String, val url: String, val name: String,
    val posterUrl: String?, val typeName: String, val addedAt: Long,
)

private const val KEY = "bookmarks_v2"
private val serializer = Bookmark.serializer()
private val listSerializer = ListSerializer(serializer)

object BookmarkRepository {
    private val _bookmarks = MutableStateFlow<Map<String, Bookmark>>(emptyMap())
    val bookmarks: StateFlow<Map<String, Bookmark>> = _bookmarks.asStateFlow()

    init { loadAll() }

    fun loadAll() {
        val list = DataStore.getSerializedList(KEY, serializer) ?: emptyList()
        _bookmarks.value = list.associateBy { it.id }
    }

    fun add(item: SearchResponse) {
        val id = "${item.apiName}_${item.url}"
        val bookmark = Bookmark(id, item.apiName, item.url, item.name, item.posterUrl, (item.type ?: TvType.Movie).name, System.currentTimeMillis())
        val current = _bookmarks.value.toMutableMap()
        current[id] = bookmark
        _bookmarks.value = current
        persist(current.values.toList())
    }

    fun remove(apiName: String, url: String) {
        val id = "${apiName}_$url"
        val current = _bookmarks.value.toMutableMap()
        current.remove(id)
        _bookmarks.value = current
        persist(current.values.toList())
    }

    fun isBookmarked(apiName: String, url: String): Boolean = _bookmarks.value.containsKey("${apiName}_$url")

    fun toggle(item: SearchResponse): Boolean {
        val was = isBookmarked(item.apiName, item.url)
        if (was) remove(item.apiName, item.url) else add(item)
        return !was
    }

    fun getAll(): List<Bookmark> = _bookmarks.value.values.sortedByDescending { it.addedAt }

    private fun persist(list: List<Bookmark>) { DataStore.setSerializedList(KEY, list, serializer) }
}
