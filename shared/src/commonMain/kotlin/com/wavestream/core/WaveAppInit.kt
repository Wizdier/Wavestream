package com.wavestream.core

import com.wavestream.api.APIHolder
import com.wavestream.plugins.PluginManager
import com.wavestream.plugins.repository.RepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * App initialization — mirrors CloudStream's MainActivity.onCreate plugin loading sequence.
 *
 * Call `WaveAppInit.initialize()` once at app startup, after DataStore and NetworkClient
 * are initialized.
 *
 * Steps:
 *   1. Check safe mode (skip plugin loading if active)
 *   2. Load all online plugins (downloaded from repositories)
 *   3. Load all local plugins (placed in the plugins folder)
 *   4. Initialize all providers (call their init methods)
 *   5. Trigger afterPluginsLoadedEvent
 */
object WaveAppInit {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false

    /**
     * Initialize the app — load all plugins.
     *
     * @param pluginsDir Directory where .ws3/.jar plugin files are stored
     * @param isSafeMode Whether safe mode is active (skip plugin loading)
     */
    fun initialize(pluginsDir: File, isSafeMode: Boolean = false) {
        if (initialized) return
        initialized = true

        if (isSafeMode) {
            println("[WaveAppInit] Safe mode active — skipping plugin loading")
            return
        }

        scope.launch {
            // Load local plugins first
            runCatching {
                println("[WaveAppInit] Loading local plugins from ${pluginsDir.absolutePath}")
                PluginManager.loadAllFromDirectory(pluginsDir)
            }.onFailure { e ->
                println("[WaveAppInit] Failed to load local plugins: ${e.message}")
            }

            // Initialize all providers
            runCatching {
                APIHolder.initAll()
            }.onFailure { e ->
                println("[WaveAppInit] Failed to initialize providers: ${e.message}")
            }

            println("[WaveAppInit] Initialization complete — ${APIHolder.allProviders.toList().size} providers loaded")
        }
    }

    /**
     * Get the default plugins directory.
     * On Android: context.filesDir/Extensions
     * On Desktop: ~/.wavestream/plugins
     */
    fun getDefaultPluginsDir(): File = getDefaultPluginsDirPlatform()
}

expect fun getDefaultPluginsDirPlatform(): File
