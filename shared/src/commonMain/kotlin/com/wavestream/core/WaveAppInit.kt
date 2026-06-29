package com.wavestream.core

import com.wavestream.api.APIHolder
import com.wavestream.plugins.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * App initialization — mirrors CloudStream's MainActivity.onCreate plugin loading sequence.
 */
object WaveAppInit {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false

    fun initialize(pluginsDir: File, isSafeMode: Boolean = false) {
        if (initialized) return
        initialized = true

        if (isSafeMode) {
            println("[WaveAppInit] Safe mode active — skipping plugin loading")
            return
        }

        scope.launch {
            // Register built-in extractors so they're available immediately
            registerBuiltInExtractors()

            // Load local plugins
            runCatching {
                if (pluginsDir.exists()) {
                    println("[WaveAppInit] Loading plugins from ${pluginsDir.absolutePath}")
                    PluginManager.loadAllFromDirectory(pluginsDir)
                }
            }.onFailure { e ->
                println("[WaveAppInit] Failed to load plugins: ${e.message}")
            }

            // Initialize all providers
            runCatching {
                APIHolder.initAll()
            }.onFailure { e ->
                println("[WaveAppInit] Failed to init providers: ${e.message}")
            }

            println("[WaveAppInit] Init complete — ${APIHolder.allProviders.toList().size} providers, ${APIHolder.extractorApis.toList().size} extractors")
        }
    }

    /**
     * Register built-in extractors that ship with the app.
     * These are always available — no plugin installation needed.
     */
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
                println("[WaveAppInit] Registered extractor: ${extractor.name}")
            }
        }
    }

    fun getDefaultPluginsDir(): File = getDefaultPluginsDirPlatform()
}

expect fun getDefaultPluginsDirPlatform(): File
