package com.wavestream.features.subscriptions

import com.wavestream.api.SearchResponse
import com.wavestream.core.storage.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String, val apiName: String, val url: String, val name: String,
    val posterUrl: String?, val lastCheckedAt: Long, val lastEpisodeCount: Int,
    val nextAirDate: Long? = null,
)

object SubscriptionRepository {
    private const val KEY = "subscriptions"
    private val _subscriptions = MutableStateFlow<Map<String, Subscription>>(emptyMap())
    val subscriptions: StateFlow<Map<String, Subscription>> = _subscriptions.asStateFlow()

    init { loadAll() }

    fun loadAll() {
        @Suppress("UNCHECKED_CAST")
        val list = DataStore.getKey(KEY, List::class.java) as? List<Subscription> ?: emptyList()
        _subscriptions.value = list.associateBy { it.id }
    }

    fun subscribe(item: SearchResponse, episodeCount: Int = 0) {
        val id = makeId(item.apiName, item.url)
        val sub = Subscription(id, item.apiName, item.url, item.name, item.posterUrl, System.currentTimeMillis(), episodeCount)
        val current = _subscriptions.value.toMutableMap()
        current[id] = sub
        _subscriptions.value = current
        persist(current.values.toList())
    }

    fun unsubscribe(apiName: String, url: String) {
        val id = makeId(apiName, url)
        val current = _subscriptions.value.toMutableMap()
        current.remove(id)
        _subscriptions.value = current
        persist(current.values.toList())
    }

    fun isSubscribed(apiName: String, url: String): Boolean = _subscriptions.value.containsKey(makeId(apiName, url))

    fun toggle(item: SearchResponse, episodeCount: Int = 0): Boolean {
        val was = isSubscribed(item.apiName, item.url)
        if (was) unsubscribe(item.apiName, item.url) else subscribe(item, episodeCount)
        return !was
    }

    fun getAll(): List<Subscription> = _subscriptions.value.values.sortedByDescending { it.lastCheckedAt }

    fun clearAll() { _subscriptions.value = emptyMap(); DataStore.removeKey(KEY) }

    private fun makeId(apiName: String, url: String): String = "${apiName}_$url"
    private fun persist(list: List<Subscription>) { DataStore.setKey(KEY, list) }
}
