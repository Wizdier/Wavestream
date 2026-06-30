package com.wavestream

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryData
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.utils.extractorApis
import com.wavestream.stremio.StremioAddonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val FIRST_LAUNCH_KEY = "wavestream_first_launch_done_v3"

object WaveAppInit {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false

    private val _initState = MutableStateFlow<InitState>(InitState.Idle)
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(message: String, level: LogLevel = LogLevel.Info) {
        println("[WaveAppInit] $message")
        val current = _logs.value.toMutableList()
        current.add(LogEntry(System.currentTimeMillis(), level, message))
        if (current.size > 500) current.removeAt(0)
        _logs.value = current
    }

    fun initialize(pluginsDir: File, isSafeMode: Boolean = false) {
        if (initialized) return
        initialized = true

        if (isSafeMode) {
            log("Safe mode active")
            _initState.value = InitState.SafeMode
            return
        }

        scope.launch {
            _initState.value = InitState.Loading("Starting")
            try {
                seedDefaultsIfFirstLaunch()

                _initState.value = InitState.Loading("Loading Stremio addons")
                StremioAddonRepository.initializeAll()
                delay(500)

                _initState.value = InitState.Loading("Loading plugins")
                if (pluginsDir.exists()) {
                    PluginManager.loadAllFromDirectory(pluginsDir)
                } else {
                    pluginsDir.mkdirs()
                }

                _initState.value = InitState.Loading("Fetching repositories")
                loadRepositories(pluginsDir)

                APIHolder.initAll()
                val providerCount = APIHolder.allProviders.size
                val extractorCount = extractorApis.size
                log("Done: $providerCount providers, $extractorCount extractors")
                _initState.value = InitState.Ready(providerCount, extractorCount)
            } catch (e: Throwable) {
                log("Init failed: ${e.message}", LogLevel.Error)
                _initState.value = InitState.Error(e.message ?: "Unknown")
            }
        }
    }

    private suspend fun seedDefaultsIfFirstLaunch() {
        if (PlatformStorage.getString(FIRST_LAUNCH_KEY) == "true") return
        log("First launch: seeding defaults")
        runCatching {
            StremioAddonRepository.addAddon("https://v3-cinemeta.strem.io/manifest.json")
            log("Added Cinemeta Stremio addon")
        }.onFailure {
            log("Cinemeta failed: ${it.message}", LogLevel.Warning)
        }
        PlatformStorage.putString(FIRST_LAUNCH_KEY, "true")
    }

    private suspend fun loadRepositories(pluginsDir: File) {
        val repos = RepositoryStore.getRepositories()
        if (repos.isEmpty()) return

        val allPlugins: List<Pair<String, List<com.lagradost.cloudstream3.plugins.SitePlugin>>> =
            coroutineScope {
                repos.map { repo ->
                    async {
                        try {
                            log("Fetching: ${repo.name.ifBlank { repo.url }}")
                            val plugins = RepositoryManager.getRepoPlugins(repo.url) ?: emptyList()
                            log("Repository has ${plugins.size} plugins")
                            repo.url to plugins
                        } catch (e: Throwable) {
                            log("Failed: ${repo.url}", LogLevel.Error)
                            repo.url to emptyList()
                        }
                    }
                }.awaitAll()
            }

        for ((repoUrl, plugins) in allPlugins) {
            for (plugin in plugins) {
                if (plugin.url.isBlank() && plugin.jarUrl.isNullOrBlank()) continue
                if (plugin.status == 0) continue

                val repoFolder = File(pluginsDir, RepositoryManager.getRepoFolderName(repoUrl))
                val pluginFile = File(repoFolder, RepositoryManager.getPluginFileName(plugin.internalName))

                if (pluginFile.exists()) {
                    if (!PluginManager.plugins.containsKey(pluginFile.absolutePath)) {
                        PluginManager.loadPlugin(pluginFile, repoUrl)
                    }
                    continue
                }

                log("Downloading: ${plugin.name}")
                val downloaded = RepositoryManager.downloadPluginToFile(
                    plugin.bestUrlForPlatform(),
                    pluginFile,
                    plugin.bestHashForPlatform(),
                )
                if (downloaded != null) {
                    if (PluginManager.loadPlugin(downloaded, repoUrl)) {
                        log("Installed: ${plugin.name}")
                    } else {
                        log("Load failed: ${plugin.name}", LogLevel.Error)
                    }
                } else {
                    log("Download failed: ${plugin.name}", LogLevel.Error)
                }
            }
        }
    }

    fun refreshRepositories(pluginsDir: File) {
        scope.launch {
            _initState.value = InitState.Loading("Refreshing")
            loadRepositories(pluginsDir)
            APIHolder.initAll()
            _initState.value = InitState.Ready(
                APIHolder.allProviders.size,
                extractorApis.size,
            )
        }
    }

    fun getDefaultPluginsDir(): File = getDefaultPluginsDirPlatform()
}

sealed class InitState {
    object Idle : InitState()
    data class Loading(val message: String) : InitState()
    data class Ready(val providerCount: Int, val extractorCount: Int) : InitState()
    data class Error(val message: String) : InitState()
    object SafeMode : InitState()
}

enum class LogLevel { Info, Warning, Error }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String,
)

expect fun getDefaultPluginsDirPlatform(): File
