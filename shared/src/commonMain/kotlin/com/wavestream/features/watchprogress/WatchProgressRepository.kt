package com.wavestream.features.watchprogress

import com.wavestream.core.storage.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class WatchProgress(
    val id: String, val apiName: String, val url: String, val title: String,
    val posterUrl: String?, val episode: Int? = null, val season: Int? = null,
    val positionMs: Long, val durationMs: Long, val updatedAt: Long,
) {
    val progressPercent: Float get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val isCompleted: Boolean get() = progressPercent >= 0.9f
}

private const val KEY = "watch_progress_v2"
private val serializer = WatchProgress.serializer()
private val listSerializer = ListSerializer(serializer)

object WatchProgressRepository {
    private val _progress = MutableStateFlow<Map<String, WatchProgress>>(emptyMap())
    val progress: StateFlow<Map<String, WatchProgress>> = _progress.asStateFlow()

    init { loadAll() }

    fun loadAll() {
        val list = DataStore.getSerializedList(KEY, serializer) ?: emptyList()
        _progress.value = list.associateBy { it.id }
    }

    fun update(progress: WatchProgress) {
        val current = _progress.value.toMutableMap()
        current[progress.id] = progress
        _progress.value = current
        persist(current.values.toList())
    }

    fun get(id: String): WatchProgress? = _progress.value[id]

    fun remove(id: String) {
        val current = _progress.value.toMutableMap()
        current.remove(id)
        _progress.value = current
        persist(current.values.toList())
    }

    fun clearAll() {
        _progress.value = emptyMap()
        DataStore.removeKey(KEY)
    }

    fun getContinueWatching(limit: Int = 20): List<WatchProgress> {
        return _progress.value.values
            .filter { !it.isCompleted && it.positionMs > 10_000 }
            .sortedByDescending { it.updatedAt }
            .take(limit)
    }

    private fun persist(list: List<WatchProgress>) {
        DataStore.setSerializedList(KEY, list, serializer)
    }
}
