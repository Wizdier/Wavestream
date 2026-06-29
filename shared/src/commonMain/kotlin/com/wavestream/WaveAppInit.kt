package com.wavestream

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
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

/**
 * Wavestream app initializer.
 *
 * Boot sequence mirrors CloudStream's MainActivity.onCreate:
 *   1. Register built-in extractors
 *   2. Load Stremio addons (manifest fetch + provider registration)
 *   3. Load local plugins (.cs3/.jar files in plugins dir)
 *   4. Fetch all repositories in parallel, download new plugins
 *   5. Init all providers (touch mainPage to trigger lazy init)
 */
object WaveAppInit {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false

    private val _initState = MutableStateFlow<InitState>(InitState.Idle)
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(message: String, level: LogLevel = LogLevel.Info) {
        println("[WaveAppInit] $message")
        val entry = LogEntry(System.currentTimeMillis(), level, message)
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > 500) current.removeAt(0)
        _logs.value = current
    }

    fun initialize(pluginsDir: File, isSafeMode: Boolean = false) {
        if (initialized) return
        initialized = true

        if (isSafeMode) {
            log("Safe mode active — skipping all plugin loading")
            _initState.value = InitState.SafeMode
            return
        }

        scope.launch {
            _initState.value = InitState.Loading("Starting initialization")
            try {
                // 0. On first launch, seed default Stremio addon
                seedDefaultsIfFirstLaunch()

                // 1. Register built-in extractors
                _initState.value = InitState.Loading("Registering extractors")
                registerBuiltInExtractors()
                log("Registered ${extractorApis.size} built-in extractors")

                // 2. Init Stremio addons
                _initState.value = InitState.Loading("Loading Stremio addons")
                StremioAddonRepository.initializeAll()
                delay(500)

                // 3. Load local plugins
                _initState.value = InitState.Loading("Loading local plugins")
                runCatching {
                    if (pluginsDir.exists()) {
                        PluginManager.loadAllFromDirectory(pluginsDir)
                    } else {
                        pluginsDir.mkdirs()
                    }
                }.onFailure { log("Local plugin load failed: ${it.message}", LogLevel.Error) }

                // 4. Fetch repositories and download missing plugins
                _initState.value = InitState.Loading("Fetching repositories")
                loadRepositories(pluginsDir)

                // 5. Init all providers
                _initState.value = InitState.Loading("Initializing providers")
                APIHolder.initAll()

                val providerCount = APIHolder.allProviders.size
                val extractorCount = extractorApis.size
                log("Done — $providerCount providers, $extractorCount extractors ready")
                _initState.value = InitState.Ready(providerCount, extractorCount)
            } catch (e: Throwable) {
                log("Initialization failed: ${e.message}", LogLevel.Error)
                e.printStackTrace()
                _initState.value = InitState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** On first launch, add the default Cinemeta Stremio addon so users see content. */
    private suspend fun seedDefaultsIfFirstLaunch() {
        val alreadyDone = PlatformStorage.getString(FIRST_LAUNCH_KEY) == "true"
        if (alreadyDone) return

        log("First launch — seeding default Stremio addon")
        runCatching {
            StremioAddonRepository.addAddon("https://v3-cinemeta.strem.io/manifest.json")
            log("Added Cinemeta Stremio addon")
        }.onFailure {
            log("Failed to add Cinemeta: ${it.message}", LogLevel.Warning)
        }

        PlatformStorage.putString(FIRST_LAUNCH_KEY, "true")
    }

    fun refreshRepositories(pluginsDir: File) {
        scope.launch {
            _initState.value = InitState.Loading("Refreshing repositories")
            loadRepositories(pluginsDir)
            APIHolder.initAll()
            val providerCount = APIHolder.allProviders.size
            val extractorCount = extractorApis.size
            _initState.value = InitState.Ready(providerCount, extractorCount)
        }
    }

    private suspend fun loadRepositories(pluginsDir: File) {
        val repos = RepositoryStore.getRepositories()
        if (repos.isEmpty()) {
            log("No repositories configured")
            return
        }

        // Fetch all repos in parallel
        val allPlugins: List<Pair<String, List<com.lagradost.cloudstream3.plugins.SitePlugin>>> =
            kotlinx.coroutines.coroutineScope {
                val deferred = repos.map { repo ->
                    async {
                        try {
                            log("Fetching repository: ${repo.name.ifBlank { repo.url }}")
                            val plugins = RepositoryManager.getRepoPlugins(repo.url) ?: emptyList()
                            log("Repository ${repo.name.ifBlank { repo.url }} has ${plugins.size} plugins")
                            repo.url to plugins
                        } catch (e: Throwable) {
                            log("Repo fetch failed: ${repo.url} — ${e.message}", LogLevel.Error)
                            repo.url to emptyList()
                        }
                    }
                }
                deferred.awaitAll()
            }

        // Download missing/outdated plugins
        for ((repoUrl, plugins) in allPlugins) {
            for (plugin in plugins) {
                if (plugin.url.isBlank() && plugin.jarUrl.isNullOrBlank()) continue
                if (plugin.status == 0) {
                    log("Skipping disabled plugin: ${plugin.name}")
                    continue
                }

                val repoFolder = File(pluginsDir, RepositoryManager.getRepoFolderName(repoUrl))
                val pluginFile = File(repoFolder, RepositoryManager.getPluginFileName(plugin.internalName))

                if (pluginFile.exists()) {
                    // Already downloaded — load if not loaded
                    if (!PluginManager.plugins.containsKey(pluginFile.absolutePath)) {
                        PluginManager.loadPlugin(pluginFile, sourceUrl = repoUrl)
                    }
                    continue
                }

                log("Downloading: ${plugin.name} v${plugin.version}")
                val downloaded = RepositoryManager.downloadPluginToFile(
                    pluginUrl = plugin.bestUrlForPlatform(),
                    targetFile = pluginFile,
                    expectedFileHash = plugin.bestHashForPlatform(),
                )
                if (downloaded != null) {
                    val loaded = PluginManager.loadPlugin(downloaded, sourceUrl = repoUrl)
                    if (loaded) {
                        log("Installed: ${plugin.name}")
                    } else {
                        log("Failed to load: ${plugin.name}", LogLevel.Error)
                    }
                } else {
                    log("Failed to download: ${plugin.name}", LogLevel.Error)
                }
            }
        }
    }

    private fun registerBuiltInExtractors() {
        // The extractor list is auto-populated by the library on first access
        // Just touch it to force initialization
        val count = extractorApis.size
        println("[WaveAppInit] ExtractorApis size: $count")
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
