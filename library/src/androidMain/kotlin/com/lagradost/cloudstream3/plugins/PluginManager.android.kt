package com.lagradost.cloudstream3.plugins

import dalvik.system.PathClassLoader
import kotlinx.serialization.json.Json
import java.io.File

actual suspend fun loadPluginFromFile(file: File): BasePlugin? {
    val filePath = file.absolutePath
    return try {
        try { file.setReadOnly() } catch (t: Throwable) {}
        val loader = PathClassLoader(filePath, BasePlugin::class.java.classLoader)
        val manifestStream = loader.getResourceAsStream("manifest.json") ?: return null
        val manifestText = manifestStream.bufferedReader().use { it.readText() }
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString(BasePlugin.Manifest.serializer(), manifestText)
        val pluginClassName = manifest.pluginClassName ?: return null
        val pluginClass = loader.loadClass(pluginClassName)
        val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
        pluginInstance.version = manifest.version ?: PluginManager.PLUGIN_VERSION_NOT_SET
        pluginInstance
    } catch (e: Throwable) { null }
}
