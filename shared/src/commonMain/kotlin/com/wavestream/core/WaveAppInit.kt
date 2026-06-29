package com.wavestream.core

import com.wavestream.api.APIHolder
import com.wavestream.core.storage.DataStore
import com.wavestream.plugins.PluginManager
import com.wavestream.plugins.repository.DefaultRepositories
import com.wavestream.plugins.repository.RepositoryManager
import com.wavestream.plugins.stremio.StremioAddonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val FIRST_LAUNCH_KEY = "wavestream_first_launch_done_v3"

/**
 * App initialization orchestrator.
 *
 * Boot sequence:
 *   1. Register built-in extractors (StreamTape, MixDrop, Doodstream, etc.)
 *   2. Initialize Stremio addons (load manifests, register as providers)
 *   3. Load local plugins (.cs3/.jar files in plugins dir)
 *   4. Fetch all repositories in parallel, download new plugins
 *   5. Init all providers (touch mainPage to trigger lazy init)
 *
 * The whole sequence runs on Dispatchers.Default and reports progress via [initState].
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
                // 0. On first launch, seed default repositories and Stremio addons
                seedDefaultsIfFirstLaunch()

                // 1. Register built-in extractors
                _initState.value = InitState.Loading("Registering extractors")
                registerBuiltInExtractors()
                log("Registered ${APIHolder.extractorApis.toList().size} built-in extractors")

                // 2. Init Stremio addons (parallel with local plugin loading)
                _initState.value = InitState.Loading("Loading Stremio addons")
                StremioAddonRepository.initializeAll()
                // Give Stremio a moment to register providers
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

                // 4. Fetch repositories in parallel and download new plugins
                _initState.value = InitState.Loading("Fetching repositories")
                loadRepositories(pluginsDir)

                // 5. Init all providers
                _initState.value = InitState.Loading("Initializing providers")
                APIHolder.initAll()

                val providerCount = APIHolder.allProviders.toList().size
                val extractorCount = APIHolder.extractorApis.toList().size
                log("Done — $providerCount providers, $extractorCount extractors ready")
                _initState.value = InitState.Ready(providerCount, extractorCount)
            } catch (e: Throwable) {
                log("Initialization failed: ${e.message}", LogLevel.Error)
                e.printStackTrace()
                _initState.value = InitState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** Force re-fetch of all repositories and download missing plugins. */
    fun refreshRepositories(pluginsDir: File) {
        scope.launch {
            _initState.value = InitState.Loading("Refreshing repositories")
            loadRepositories(pluginsDir)
            APIHolder.initAll()
            val providerCount = APIHolder.allProviders.toList().size
            val extractorCount = APIHolder.extractorApis.toList().size
            _initState.value = InitState.Ready(providerCount, extractorCount)
        }
    }

    private suspend fun loadRepositories(pluginsDir: File) {
        val repos = RepositoryManager.getRepositories()
        if (repos.isEmpty()) {
            log("No repositories configured")
            return
        }

        // Fetch all repos in parallel
        val allPlugins = kotlinx.coroutines.coroutineScope {
            repos.map { repo ->
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
            }.awaitAll()
        }

        // Download missing/outdated plugins sequentially (to avoid hammering repos)
        for ((repoUrl, plugins) in allPlugins) {
            for (plugin in plugins) {
                if (plugin.url.isBlank()) continue
                // Skip disabled plugins
                if (plugin.status == 0) {
                    log("Skipping disabled plugin: ${plugin.name}")
                    continue
                }

                val repoFolder = File(pluginsDir, RepositoryManager.getRepoFolderName(repoUrl))
                val pluginFile = File(repoFolder, RepositoryManager.getPluginFileName(plugin.internalName))

                // Check version — skip if already up-to-date
                val existing = PluginManager.pluginData.value.firstOrNull {
                    it.filePath == pluginFile.absolutePath
                }
                if (existing != null && existing.version >= plugin.version && pluginFile.exists()) {
                    // Already have it — ensure it's loaded
                    if (!PluginManager.plugins.containsKey(pluginFile.absolutePath)) {
                        PluginManager.loadPlugin(pluginFile, sourceUrl = repoUrl)
                    }
                    continue
                }

                log("Downloading: ${plugin.name} v${plugin.version} (${plugin.fileSize ?: "?"} bytes)")
                val success = PluginManager.downloadAndLoad(
                    pluginUrl = plugin.bestUrlForPlatform(),
                    targetFile = pluginFile,
                    expectedHash = plugin.bestHashForPlatform(),
                    repositoryUrl = repoUrl,
                )
                if (success) {
                    log("Installed: ${plugin.name}")
                } else {
                    log("Failed to install: ${plugin.name}", LogLevel.Error)
                }
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

    /** On first launch, add the default CloudStream repos + a couple of demo Stremio addons. */
    private suspend fun seedDefaultsIfFirstLaunch() {
        val alreadyDone = DataStore.getKey(FIRST_LAUNCH_KEY, Boolean::class.java, false) ?: false
        if (alreadyDone) return

        log("First launch — seeding default repositories and Stremio addons")
        for (repo in DefaultRepositories.ALL) {
            RepositoryManager.addRepository(repo.url, repo.name)
        }
        log("Added ${DefaultRepositories.ALL.size} default CloudStream repositories")

        // Add a couple of demo Stremio addons (Cinemeta + PublicDomainMovies)
        for (addonUrl in DefaultRepositories.DEFAULT_STREMIO_ADDONS.take(1)) {
            runCatching {
                StremioAddonRepository.addAddon(addonUrl)
                log("Added default Stremio addon: $addonUrl")
            }.onFailure {
                log("Failed to add default Stremio addon $addonUrl: ${it.message}", LogLevel.Warning)
            }
        }

        DataStore.setKey(FIRST_LAUNCH_KEY, true)
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
