package com.wavestream

import com.lagradost.cloudstream3.plugins.RepositoryData
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Cross-platform repository storage.
 *
 * On Android: uses SharedPreferences via DataStoreHelper.
 * On Desktop: uses a JSON file in ~/.wavestream/repositories.json.
 */
object RepositoryStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = RepositoryData.serializer()
    private val listSerializer = ListSerializer(serializer)

    private const val KEY = "cs_repositories_v3"

    fun getRepositories(): List<RepositoryData> {
        val raw = PlatformStorage.getString(KEY) ?: return DEFAULT_REPOS
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    fun addRepository(url: String, name: String = ""): Boolean {
        val current = getRepositories().toMutableList()
        if (current.any { it.url == url }) return false
        current.add(RepositoryData(url, name))
        save(current)
        return true
    }

    fun removeRepository(url: String) {
        val current = getRepositories().filterNot { it.url == url }
        save(current)
    }

    fun renameRepository(url: String, newName: String) {
        val current = getRepositories().map {
            if (it.url == url) it.copy(name = newName) else it
        }
        save(current)
    }

    private fun save(list: List<RepositoryData>) {
        val raw = json.encodeToString(listSerializer, list)
        PlatformStorage.putString(KEY, raw)
    }

    /** Default repositories — pre-seeded on first launch so users see content. */
    val DEFAULT_REPOS: List<RepositoryData> = listOf(
        RepositoryData(
            url = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
            name = "CloudStream Official Extensions",
        ),
    )
}

/**
 * Platform-specific storage interface.
 */
expect object PlatformStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}
