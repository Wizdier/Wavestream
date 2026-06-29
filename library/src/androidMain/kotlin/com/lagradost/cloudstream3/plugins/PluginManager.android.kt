package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.utils.ExtractorApi
import dalvik.system.PathClassLoader
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Android implementation of plugin loading via PathClassLoader (Dalvik DEX).
 *
 * The .ws3/.cs3 file is a ZIP containing:
 *   - manifest.json  (plugin metadata)
 *   - classes.dex    (compiled against com.lagradost.cloudstream3)
 *   - res/           (optional resources if requiresResources: true)
 *
 * On Android 14+, the file must be set read-only before loading.
 */
actual suspend fun loadPluginFromFile(file: File): BasePlugin? {
    val filePath = file.absolutePath
    return try {
        // Set file read-only (Android 14+ requirement for DEX loading)
        try {
            if (!file.setReadOnly()) {
                println("[PluginManager] Failed to set read-only on ${file.name}")
            }
        } catch (t: Throwable) {
            println("[PluginManager] Failed to set dex as read-only: ${t.message}")
        }

        // 1. Create PathClassLoader — Dalvik/ART's DEX loader
        val loader = PathClassLoader(filePath, BasePlugin::class.java.classLoader)

        // 2. Read manifest.json from inside the .ws3/.apk
        val manifestStream = loader.getResourceAsStream("manifest.json") ?: run {
            println("[PluginManager] No manifest.json in ${file.name}")
            return null
        }
        val manifestText = manifestStream.bufferedReader().use { it.readText() }
        val manifest = Json { ignoreUnknownKeys = true }
            .decodeFromString(BasePlugin.Manifest.serializer(), manifestText)

        val name = manifest.name ?: "NO NAME"
        val version = manifest.version ?: PluginManager.PLUGIN_VERSION_NOT_SET
        val pluginClassName = manifest.pluginClassName ?: run {
            println("[PluginManager] No pluginClassName in manifest of ${file.name}")
            return null
        }

        println("[PluginManager] Manifest: name=$name, version=$version, class=$pluginClassName")

        // 3. Reflect out the plugin class named in manifest
        @Suppress("UNCHECKED_CAST")
        val pluginClass = loader.loadClass(pluginClassName)
        val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
        pluginInstance.version = version
        pluginInstance
    } catch (e: Throwable) {
        println("[PluginManager] Failed to load $file: ${e.message}")
        e.printStackTrace()
        null
    }
}
