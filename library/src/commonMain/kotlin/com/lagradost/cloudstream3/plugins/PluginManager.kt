package com.lagradost.cloudstream3.plugins

import kotlinx.coroutines.sync.Mutex
import java.io.File

/**
 * Common PluginManager contract — both Android (PathClassLoader) and Desktop (URLClassLoader)
 * implementations live in their respective source sets.
 *
 * This object holds the runtime state (loaded plugins map, mutex) but delegates the actual
 * plugin file loading to the platform-specific [loadPluginFromFile] function.
 */
object PluginManager {
    const val TAG = "PluginManager"

    /** Prevent multiple writes at once */
    val lock = Mutex()

    const val PLUGINS_KEY = "PLUGINS_KEY"
    const val PLUGINS_KEY_LOCAL = "PLUGINS_KEY_LOCAL"
    const val PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE
    const val PLUGIN_VERSION_ALWAYS_UPDATE = -1

    /** filepath → plugin instance */
    val plugins: MutableMap<String, BasePlugin> = LinkedHashMap()

    /** repository url → plugin instance (for online plugins) */
    val urlPlugins: MutableMap<String, BasePlugin> = LinkedHashMap()

    @Volatile
    var currentlyLoading: String? = null
        internal set

    @Volatile
    var loadedLocalPlugins: Boolean = false
        internal set

    @Volatile
    var loadedOnlinePlugins: Boolean = false
        internal set

    @Volatile
    var lastError: String? = null
        internal set

    /**
     * Load a plugin file. Idempotent — if already loaded, returns true.
     */
    suspend fun loadPlugin(file: File, sourceUrl: String? = null): Boolean {
        val filePath = file.absolutePath
        currentlyLoading = file.nameWithoutExtension
        return try {
            val existing = plugins[filePath]
            if (existing != null) {
                return true
            }
            val instance = loadPluginFromFile(file) ?: return false
            instance.filename = filePath
            instance.sourceUrl = sourceUrl
            plugins[filePath] = instance
            if (sourceUrl != null) {
                urlPlugins[sourceUrl] = instance
            }
            // Call load() — plugin registers providers/extractors here
            instance.load()
            println("[PluginManager] Loaded: ${file.name}")
            true
        } catch (e: Throwable) {
            lastError = e.message
            println("[PluginManager] Failed to load $file: ${e.message}")
            e.printStackTrace()
            false
        } finally {
            currentlyLoading = null
        }
    }

    /**
     * Unload a plugin by file path. Calls beforeUnload() and removes registered providers.
     */
    fun unloadPlugin(absolutePath: String) {
        val plugin = plugins[absolutePath] ?: return
        try {
            plugin.beforeUnload()
        } catch (e: Throwable) {
            println("[PluginManager] beforeUnload failed: ${e.message}")
        }
        plugins.remove(absolutePath)
        urlPlugins.entries.removeIf { it.value === plugin }
    }

    /**
     * Load all plugins from a directory.
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
     * Sanitized file name for a plugin (internalName + hash). Mirrors CloudStream.
     */
    fun getPluginSanitizedFileName(name: String): String {
        val sanitized = sanitizeFilename(name)
        return "$sanitized.${name.hashCode()}"
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "unnamed" }.take(120)
    }

    fun isSafeMode(): Boolean = lastError != null
}

/**
 * Platform-specific loader. Reads manifest.json from the file, reflects out the plugin class,
 * instantiates it, and returns it. Returns null on failure.
 */
expect suspend fun loadPluginFromFile(file: File): BasePlugin?
