package com.wavestream.plugins

import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * Desktop (JVM) implementation of plugin loading via URLClassLoader.
 */
actual suspend fun loadPluginInternalPlatform(file: File): BasePlugin? {
    val filePath = file.absolutePath

    return try {
        // 1. Create URLClassLoader — JVM equivalent of Android's PathClassLoader
        val loader = URLClassLoader(arrayOf(URL("file:$filePath")), BasePlugin::class.java.classLoader)

        // 2. Read manifest.json from inside the jar
        val manifestStream = loader.getResourceAsStream("manifest.json") ?: run {
            println("[PluginManager] No manifest.json in ${file.name}")
            return null
        }
        val manifestText = manifestStream.bufferedReader().use { it.readText() }
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<BasePlugin.Manifest>(manifestText)

        val name = manifest.name ?: "NO NAME"
        val version = manifest.version ?: PLUGIN_VERSION_NOT_SET
        val pluginClassName = manifest.pluginClassName ?: run {
            println("[PluginManager] No pluginClassName in manifest of ${file.name}")
            return null
        }

        println("[PluginManager] Manifest: name=$name, version=$version, class=$pluginClassName")

        // 3. Reflect out the plugin class named in manifest
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
    // Desktop: check for a "safe" file in user home
    val safeFile = File(System.getProperty("user.home"), ".wavestream/safe")
    return safeFile.exists()
}
