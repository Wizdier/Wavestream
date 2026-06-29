package com.wavestream.plugins

import com.wavestream.api.APIHolder
import com.wavestream.api.MainAPI
import com.wavestream.api.ExtractorApi
import com.wavestream.core.storage.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Plugin manager — mirrors CloudStream's `com.lagradost.cloudstream3.plugins.PluginManager`.
 *
 * Loads plugins from .jar files (Desktop) or .ws3/.cs3 files (Android, containing classes.dex)
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
    val json = Json { ignoreUnknownKeys = true }
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

    @Volatile
    var lastError: String? = null

    /** Live status of plugin loading for UI feedback. */
    private val _pluginEvents = MutableStateFlow<List<PluginEvent>>(emptyList())
    val pluginEvents: StateFlow<List<PluginEvent>> = _pluginEvents.asStateFlow()

    /** Persisted plugin metadata. */
    private val _pluginData = MutableStateFlow<List<PluginData>>(emptyList())
    val pluginData: StateFlow<List<PluginData>> = _pluginData.asStateFlow()

    init {
        _pluginData.value = DataStore.getSerializedList(PLUGINS_KEY, PluginData.serializer()) ?: emptyList()
    }

    fun emitEvent(event: PluginEvent) {
        val current = _pluginEvents.value.toMutableList()
        current.add(event)
        // Keep last 100 events
        if (current.size > 100) current.removeAt(0)
        _pluginEvents.value = current
    }

    fun clearEvents() {
        _pluginEvents.value = emptyList()
    }

    /**
     * Load a plugin from a .jar file (Desktop) or .ws3/.cs3 file (Android).
     * @return true if successful
     */
    suspend fun loadPlugin(file: File, sourceUrl: String? = null): Boolean = loadMutex.withLock {
        val filePath = file.absolutePath
        val fileName = file.nameWithoutExtension
        currentlyLoading = fileName
        emitEvent(PluginEvent.Loading(fileName, filePath))

        return@withLock try {
            val pluginInstance = loadPluginInternalPlatform(file) ?: run {
                emitEvent(PluginEvent.Failed(fileName, "Could not instantiate plugin"))
                return@withLock false
            }
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

            // Persist plugin data
            val internalName = fileName
            val existing = _pluginData.value.firstOrNull { it.filePath == filePath }
            val updated = (existing?.copy(version = pluginInstance.version) ?: PluginData(
                internalName = internalName,
                url = sourceUrl,
                isOnline = sourceUrl != null,
                filePath = filePath,
                version = pluginInstance.version,
            ))
            val newData = (_pluginData.value.filterNot { it.filePath == filePath } + updated)
            _pluginData.value = newData
            DataStore.setSerializedList(PLUGINS_KEY, newData, PluginData.serializer())

            emitEvent(PluginEvent.Loaded(fileName, pluginInstance.version))
            println("[PluginManager] Loaded plugin $fileName v${pluginInstance.version} successfully")
            currentlyLoading = null
            true
        } catch (e: Throwable) {
            lastError = "${e.message}"
            emitEvent(PluginEvent.Failed(fileName, e.message ?: "Unknown error"))
            println("[PluginManager] Failed to load $file: ${e.message}")
            e.printStackTrace()
            currentlyLoading = null
            false
        }
    }

    /**
     * Download a plugin from a URL and load it immediately.
     * @return true if download + load succeeded
     */
    suspend fun downloadAndLoad(
        pluginUrl: String,
        targetFile: File,
        expectedHash: String? = null,
        repositoryUrl: String? = null,
    ): Boolean {
        val downloaded = com.wavestream.plugins.repository.RepositoryManager.downloadPlugin(pluginUrl, targetFile, expectedHash)
            ?: run {
                emitEvent(PluginEvent.Failed(targetFile.name, "Download failed"))
                return false
            }
        return loadPlugin(downloaded, sourceUrl = repositoryUrl)
    }

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

        // Remove from persisted data
        val newData = _pluginData.value.filterNot { it.filePath == absolutePath }
        _pluginData.value = newData
        DataStore.setSerializedList(PLUGINS_KEY, newData, PluginData.serializer())
    }

    /**
     * Delete a plugin file and unload it.
     */
    fun deletePlugin(file: File): Boolean {
        return try {
            unloadPlugin(file.absolutePath)
            file.delete()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Load all plugin files from a directory — mirrors CS3's loadAllLocalPlugins().
     */
    suspend fun loadAllFromDirectory(dir: File) {
        if (!dir.exists()) {
            dir.mkdirs()
            return
        }
        dir.listFiles { f -> f.extension in setOf("jar", "ws3", "cs3") }
            ?.sortedBy { it.name }
            ?.forEach { loadPlugin(it) }
        loadedLocalPlugins = true
    }

    /**
     * Check if safe mode is active (no plugins should load).
     */
    fun isSafeMode(): Boolean = checkSafeModeFile() || lastError != null
}

/** Live events emitted during plugin loading — surfaced in the UI for visibility. */
sealed class PluginEvent {
    abstract val pluginName: String
    abstract val timestamp: Long

    data class Loading(override val pluginName: String, val path: String) : PluginEvent() {
        override val timestamp: Long = System.currentTimeMillis()
    }
    data class Loaded(override val pluginName: String, val version: Int) : PluginEvent() {
        override val timestamp: Long = System.currentTimeMillis()
    }
    data class Failed(override val pluginName: String, val reason: String) : PluginEvent() {
        override val timestamp: Long = System.currentTimeMillis()
    }
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

const val PLUGINS_KEY = "wavestream_plugins_v3"
const val PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE
const val PLUGIN_VERSION_ALWAYS_UPDATE = -1

/** List serializer for PluginData (used by DataStore). */
fun pluginDataListSerializer() = ListSerializer(PluginData.serializer())
