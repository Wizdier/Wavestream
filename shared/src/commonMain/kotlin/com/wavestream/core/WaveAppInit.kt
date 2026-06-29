package com.wavestream.core

import com.wavestream.api.APIHolder
import com.wavestream.core.storage.DataStore
import com.wavestream.plugins.PluginManager
import com.wavestream.plugins.repository.RepositoryManager
import com.wavestream.plugins.stremio.StremioAddonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

object WaveAppInit {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false

    @Serializable
    data class StoredRepo(val url: String, val name: String = "")
    private val repoSerializer = StoredRepo.serializer()
    private val repoListSerializer = ListSerializer(repoSerializer)

    private const val REPOS_KEY = "cs_repositories_v3"

    fun initialize(pluginsDir: File, isSafeMode: Boolean = false) {
        if (initialized) return
        initialized = true

        if (isSafeMode) {
            println("[WaveAppInit] Safe mode — skipping")
            return
        }

        scope.launch {
            // 1. Register built-in extractors
            registerBuiltInExtractors()

            // 2. Load Stremio addons → register as MainAPI providers
            StremioAddonRepository.initializeAll()

            // 3. Load local plugins (.cs3 files)
            runCatching {
                if (pluginsDir.exists()) {
                    PluginManager.loadAllFromDirectory(pluginsDir)
                }
            }

            // 4. Fetch repos and download new plugins
            loadRepositories(pluginsDir)

            // 5. Init all providers
            APIHolder.initAll()

            println("[WaveAppInit] Done — ${APIHolder.allProviders.toList().size} providers, ${APIHolder.extractorApis.toList().size} extractors")
        }
    }

    private suspend fun loadRepositories(pluginsDir: File) {
        val repos = DataStore.getSerializedList(REPOS_KEY, repoSerializer) ?: emptyList()
        for (repo in repos) {
            try {
                val plugins = RepositoryManager.getRepoPlugins(repo.url) ?: continue
                for (plugin in plugins) {
                    val pluginFile = File(pluginsDir, RepositoryManager.getPluginFileName(plugin.internalName))
                    if (pluginFile.exists()) continue // already downloaded
                    println("[WaveAppInit] Downloading: ${plugin.name}")
                    val downloaded = RepositoryManager.downloadPlugin(plugin.url, pluginFile, plugin.fileHash)
                    if (downloaded != null) {
                        PluginManager.loadPlugin(downloaded, repo.url)
                        println("[WaveAppInit] Installed: ${plugin.name}")
                    }
                }
            } catch (e: Throwable) {
                println("[WaveAppInit] Repo fetch failed: ${repo.url} — ${e.message}")
            }
        }
    }

    private fun registerBuiltInExtractors() {
        val extractors = listOf(
            com.wavestream.plugins.extractors.M3u8Manifest(),
            com.wavestream.plugins.extractors.StreamTape(),
            com.wavestream.plugins.extractors.MixDrop(),
            com.wavestream.plugins.extractors.Doodstream(),
            com.wavestream.plugins.extractors.Voe(),
            com.wavestream.plugins.extractors.Filemoon(),
            com.wavestream.plugins.extractors.JWPlayer(),
            com.wavestream.plugins.extractors.Upstream(),
            com.wavestream.plugins.extractors.Sendvid(),
            com.wavestream.plugins.extractors.Mp4Upload(),
            com.wavestream.plugins.extractors.Vidoza(),
        )
        extractors.forEach { extractor ->
            if (APIHolder.extractorApis.toList().none { it.name == extractor.name }) {
                APIHolder.extractorApis.add(extractor)
            }
        }
    }

    fun getDefaultPluginsDir(): File = getDefaultPluginsDirPlatform()
}

expect fun getDefaultPluginsDirPlatform(): File
