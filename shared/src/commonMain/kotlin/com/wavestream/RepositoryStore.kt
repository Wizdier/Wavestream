package com.wavestream

import com.lagradost.cloudstream3.plugins.RepositoryData
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object RepositoryStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = RepositoryData.serializer()
    private const val KEY = "cs_repositories_v3"
    val DEFAULT_REPOS = listOf(RepositoryData("https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json", "CloudStream Official"))

    fun getRepositories(): List<RepositoryData> {
        val raw = PlatformStorage.getString(KEY) ?: return DEFAULT_REPOS
        return runCatching { json.decodeFromString(ListSerializer(serializer), raw) }.getOrDefault(emptyList())
    }
    fun addRepository(url: String, name: String = ""): Boolean {
        val current = getRepositories().toMutableList()
        if (current.any { it.url == url }) return false
        current.add(RepositoryData(url, name)); save(current); return true
    }
    fun removeRepository(url: String) { save(getRepositories().filterNot { it.url == url }) }
    private fun save(list: List<RepositoryData>) { PlatformStorage.putString(KEY, json.encodeToString(ListSerializer(serializer), list)) }
}

expect object PlatformStorage { fun getString(key: String): String?; fun putString(key: String, value: String) }
