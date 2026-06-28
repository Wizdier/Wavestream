package com.wavestream.features.bookmarks

import com.wavestream.api.SearchResponse
import com.wavestream.api.TvType
import com.wavestream.core.storage.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Bookmark — a saved show/movie the user wants to watch later.
 */
@Serializable
data class Bookmark(
    val id: String,              // apiName + url
    val apiName: String,
    val url: String,
    val name: String,
    val posterUrl: String?,
    val type: TvType,
    val addedAt: Long,
)

/**
 * Bookmark repository — persists bookmarks via DataStore.
 *
 * Mirrors CloudStream's bookmark system.
 */
object BookmarkRepository {
    private const val KEY = "bookmarks"

    private val _bookmarks = MutableStateFlow<Map<String, Bookmark>>(emptyMap())
    val bookmarks: StateFlow<Map<String, Bookmark>> = _bookmarks.asStateFlow()

    init { loadAll() }

    fun loadAll() {
        @Suppress("UNCHECKED_CAST")
        val list = DataStore.getKey(KEY, List::class.java) as? List<Bookmark> ?: emptyList()
        _bookmarks.value = list.associateBy { it.id }
    }

    fun add(item: SearchResponse) {
        val id = makeId(item.apiName, item.url)
        val bookmark = Bookmark(
            id = id,
            apiName = item.apiName,
            url = item.url,
            name = item.name,
            posterUrl = item.posterUrl,
            type = item.type ?: TvType.Movie,
            addedAt = System.currentTimeMillis(),
        )
        val current = _bookmarks.value.toMutableMap()
        current[id] = bookmark
        _bookmarks.value = current
        persist(current.values.toList())
    }

    fun remove(apiName: String, url: String) {
        val id = makeId(apiName, url)
        val current = _bookmarks.value.toMutableMap()
        current.remove(id)
        _bookmarks.value = current
        persist(current.values.toList())
    }

    fun isBookmarked(apiName: String, url: String): Boolean {
        return _bookmarks.value.containsKey(makeId(apiName, url))
    }

    fun toggle(item: SearchResponse): Boolean {
        val wasBookmarked = isBookmarked(item.apiName, item.url)
        if (wasBookmarked) {
            remove(item.apiName, item.url)
        } else {
            add(item)
        }
        return !wasBookmarked
    }

    fun getAll(): List<Bookmark> = _bookmarks.value.values.sortedByDescending { it.addedAt }

    fun clearAll() {
        _bookmarks.value = emptyMap()
        DataStore.removeKey(KEY)
    }

    private fun makeId(apiName: String, url: String): String = "${apiName}_$url"

    private fun persist(list: List<Bookmark>) {
        DataStore.setKey(KEY, list)
    }
}
