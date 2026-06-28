package com.wavestream.plugins

import com.wavestream.api.APIHolder
import com.wavestream.api.MainAPI
import com.wavestream.api.ExtractorApi
import com.wavestream.core.network.NetworkClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * Plugin manager — mirrors CloudStream's `com.lagradost.cloudstream3.plugins.PluginManager`.
 *
 * Loads plugins from .jar files (Desktop) or .ws3 files (Android, containing classes.dex)
 * using URLClassLoader / PathClassLoader respectively.
 *
 * On Android, the .ws3 file is set read-only before loading (Android 14+ requirement),
 * and resources (drawables, strings) can be loaded via AssetManager.addAssetPath if
 * `requiresResources: true` in the manifest.
 *
 * Plugin lifecycle:
 *   1. Download .ws3 file from a repository URL (or copy from local plugins folder)
 *   2. Verify SHA-256 hash if provided
 *   3. Set file read-only (Android 14+)
 *   4. Create classloader pointing at the file
 *   5. Read manifest.json from inside the file
 *   6. Reflect out the plugin class named in manifest.pluginClassName
 *   7. Instantiate via getDeclaredConstructor().newInstance()
 *   8. Set pluginInstance.filename
 *   9. Call pluginInstance.load() — plugin registers providers/extractors here
 */
object PluginManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val loadMutex = Mutex()

    /** filepath → plugin instance */
    val plugins: MutableMap<String, BasePlugin> = LinkedHashMap()

    /** repository url → plugin instance (for online plugins) */
    val urlPlugins: MutableMap<String, BasePlugin> = LinkedHashMap()

    /** classloader → plugin instance (for unloading) */
    private val classLoaders: MutableMap<Any, BasePlugin> = HashMap()

    @Volatile
    var currentlyLoading: String? = null
        private set

    @Volatile
    var loadedLocalPlugins: Boolean = false
        private set

    @Volatile
    var loadedOnlinePlugins: Boolean = false
        private set

    /**
     * Load a plugin from a .jar file (Desktop) or .ws3 file (Android).
     * @return true if successful
     */
    suspend fun loadPlugin(file: File, sourceUrl: String? = null): Boolean = loadMutex.withLock {
        val filePath = file.absolutePath
        val fileName = file.nameWithoutExtension
        currentlyLoading = fileName
        println("[PluginManager] Loading plugin: $fileName from $filePath")

        return@withLock try {
            val pluginInstance = loadPluginInternal(file) ?: return@withLock false
            pluginInstance.filename = filePath
            pluginInstance.sourceUrl = sourceUrl

            if (plugins.containsKey(filePath)) {
                println("[PluginManager] Plugin with path $filePath already loaded")
                return@withLock true
            }

            plugins[filePath] = pluginInstance
            if (sourceUrl != null) {
                urlPlugins[sourceUrl] = pluginInstance
            }

            // Call load() — plugin registers providers/extractors here
            pluginInstance.load()

            println("[PluginManager] Loaded plugin $fileName successfully")
            currentlyLoading = null
            true
        } catch (e: Throwable) {
            println("[PluginManager] Failed to load $file: ${e.message}")
            e.printStackTrace()
            currentlyLoading = null
            false
        }
    }

    /**
     * Platform-specific plugin loader. On Desktop uses URLClassLoader.
     * On Android uses PathClassLoader (see PluginManager.android.kt).
     */
    suspend fun loadPluginInternal(file: File): BasePlugin? = loadPluginInternalPlatform(file)

    /**
     * Unload a plugin — calls beforeUnload(), then removes all providers/extractors
     * registered by this plugin.
     */
    fun unloadPlugin(absolutePath: String) {
        println("[PluginManager] Unloading plugin: $absolutePath")
        val plugin = plugins[absolutePath] ?: return

        try {
            plugin.beforeUnload()
        } catch (e: Throwable) {
            println("[PluginManager] beforeUnload failed: ${e.message}")
        }

        // Remove all providers registered by this plugin
        APIHolder.allProviders.removeAll { it.sourcePlugin == plugin.filename }
        APIHolder.apis.removeAll { it.sourcePlugin == plugin.filename }
        APIHolder.extractorApis.removeAll { it.sourcePlugin == plugin.filename }

        plugins.remove(absolutePath)
        urlPlugins.entries.removeIf { it.value === plugin }
        classLoaders.entries.removeIf { it.value === plugin }
    }

    /**
     * Load all plugin files from a directory — mirrors CS3's loadAllLocalPlugins().
     */
    suspend fun loadAllFromDirectory(dir: File) {
        if (!dir.exists()) {
            dir.mkdirs()
            return
        }
        dir.listFiles { f -> f.extension in setOf("jar", "ws3") }
            ?.sortedBy { it.name }
            ?.forEach { loadPlugin(it) }
        loadedLocalPlugins = true
    }

    /**
     * Check if safe mode is active (no plugins should load).
     * Mirrors CS3's safe mode file check.
     */
    fun isSafeMode(): Boolean = checkSafeModeFile() || lastError != null

    @Volatile
    var lastError: String? = null
}

// Platform-specific plugin loading (expect/actual at top-level)
expect suspend fun loadPluginInternalPlatform(file: File): BasePlugin?

expect fun checkSafeModeFile(): Boolean

/**
 * Plugin data class — persisted info about an installed plugin.
 */
@Serializable
data class PluginData(
    val internalName: String,
    val url: String?,
    val isOnline: Boolean,
    val filePath: String,
    val version: Int,
)

const val PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE
const val PLUGIN_VERSION_ALWAYS_UPDATE = -1
