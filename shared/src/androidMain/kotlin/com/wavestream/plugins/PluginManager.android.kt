package com.wavestream.plugins

import android.content.Context
import dalvik.system.PathClassLoader
import java.io.File

/**
 * Android implementation of plugin loading via PathClassLoader (Dalvik DEX).
 *
 * The .ws3 file is a ZIP containing:
 *   - manifest.json  (plugin metadata)
 *   - classes.dex    (compiled against com.wavestream.api)
 *   - res/           (optional resources if requiresResources: true)
 */

private var appContext: Context? = null

fun initPluginManager(context: Context) {
    appContext = context.applicationContext
}

actual suspend fun loadPluginInternalPlatform(file: File): BasePlugin? {
    val context = appContext ?: throw IllegalStateException("initPluginManager(context) must be called first")
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
        val loader = PathClassLoader(filePath, context.classLoader)

        // 2. Read manifest.json from inside the .ws3/.apk
        val manifestStream = loader.getResourceAsStream("manifest.json") ?: run {
            println("[PluginManager] No manifest.json in ${file.name}")
            return null
        }
        val manifestText = manifestStream.bufferedReader().use { it.readText() }
        val manifest = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<BasePlugin.Manifest>(manifestText)

        val name = manifest.name ?: "NO NAME"
        val version = manifest.version ?: PLUGIN_VERSION_NOT_SET
        val pluginClassName = manifest.pluginClassName ?: run {
            println("[PluginManager] No pluginClassName in manifest of ${file.name}")
            return null
        }

        println("[PluginManager] Manifest: name=$name, version=$version, class=$pluginClassName")

        // 3. Reflect out the plugin class named in manifest
        @Suppress("UNCHECKED_CAST")
        val pluginClass = loader.loadClass(pluginClassName)
        val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin

        pluginInstance
    } catch (e: Throwable) {
        println("[PluginManager] Failed to load $file: ${e.message}")
        e.printStackTrace()
        null
    }
}

actual fun checkSafeModeFile(): Boolean {
    val context = appContext ?: return false
    val safeFile = File(context.filesDir, "safe")
    return safeFile.exists()
}
