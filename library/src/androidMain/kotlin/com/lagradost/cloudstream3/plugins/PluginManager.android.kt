package com.lagradost.cloudstream3.plugins

import dalvik.system.PathClassLoader
import kotlinx.serialization.json.Json
import java.io.File

actual suspend fun loadPluginFromFile(file: File): BasePlugin? {
    return try {
        try { file.setReadOnly() } catch (t: Throwable) {}
        val loader = PathClassLoader(file.absolutePath, BasePlugin::class.java.classLoader)
        val manifestStream = loader.getResourceAsStream("manifest.json") ?: return null
        val manifest = Json { ignoreUnknownKeys = true }.decodeFromString(BasePlugin.Manifest.serializer(), manifestStream.bufferedReader().use { it.readText() })
        val pluginClass = loader.loadClass(manifest.pluginClassName ?: return null)
        val instance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
        instance.version = manifest.version ?: PluginManager.PLUGIN_VERSION_NOT_SET
        instance
    } catch (e: Throwable) { null }
}
actual fun isDesktopPlatform(): Boolean = false
