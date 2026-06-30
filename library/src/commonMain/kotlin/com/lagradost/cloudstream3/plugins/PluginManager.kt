package com.lagradost.cloudstream3.plugins

import kotlinx.coroutines.sync.Mutex
import java.io.File

object PluginManager {
    const val TAG = "PluginManager"
    val lock = Mutex()
    const val PLUGINS_KEY = "PLUGINS_KEY"
    const val PLUGINS_KEY_LOCAL = "PLUGINS_KEY_LOCAL"
    const val PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE
    const val PLUGIN_VERSION_ALWAYS_UPDATE = -1

    val plugins: MutableMap<String, BasePlugin> = LinkedHashMap()
    val urlPlugins: MutableMap<String, BasePlugin> = LinkedHashMap()

    @Volatile var currentlyLoading: String? = null
    @Volatile var loadedLocalPlugins: Boolean = false
    @Volatile var loadedOnlinePlugins: Boolean = false
    @Volatile var lastError: String? = null

    suspend fun loadPlugin(file: File, sourceUrl: String? = null): Boolean {
        val filePath = file.absolutePath
        currentlyLoading = file.nameWithoutExtension
        return try {
            if (plugins.containsKey(filePath)) return true
            val instance = loadPluginFromFile(file) ?: return false
            instance.filename = filePath
            instance.sourceUrl = sourceUrl
            plugins[filePath] = instance
            if (sourceUrl != null) urlPlugins[sourceUrl] = instance
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

    fun unloadPlugin(absolutePath: String) {
        val plugin = plugins[absolutePath] ?: return
        try { plugin.beforeUnload() } catch (e: Throwable) {}
        plugins.remove(absolutePath)
        urlPlugins.entries.removeIf { it.value === plugin }
    }

    suspend fun loadAllFromDirectory(dir: File) {
        if (!dir.exists()) { dir.mkdirs(); return }
        dir.listFiles { f -> f.extension in setOf("jar", "ws3", "cs3") }
            ?.sortedBy { it.name }
            ?.forEach { loadPlugin(it) }
        loadedLocalPlugins = true
    }

    fun getPluginSanitizedFileName(name: String): String {
        val sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "unnamed" }.take(120)
        return "$sanitized.${name.hashCode()}"
    }

    fun isSafeMode(): Boolean = lastError != null
}

expect suspend fun loadPluginFromFile(file: File): BasePlugin?
