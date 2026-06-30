package com.lagradost.cloudstream3.plugins

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.zip.ZipFile

actual suspend fun loadPluginFromFile(file: File): BasePlugin? {
    val filePath = file.absolutePath
    return try {
        val loader = URLClassLoader(arrayOf(URL("file:$filePath")), BasePlugin::class.java.classLoader)
        val manifestStream = loader.getResourceAsStream("manifest.json") ?: readManifestFromZip(file)
        val manifest: BasePlugin.Manifest? = manifestStream?.use { stream ->
            val text = stream.bufferedReader().use { it.readText() }
            runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(BasePlugin.Manifest.serializer(), text) }.getOrNull()
        }
        val pluginClassName = manifest?.pluginClassName
        val pluginClass = when {
            pluginClassName != null -> {
                try { loader.loadClass(pluginClassName) }
                catch (e: ClassNotFoundException) { findPluginClassByScanning(loader, file) }
            }
            else -> findPluginClassByScanning(loader, file)
        } ?: return null
        val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
        pluginInstance.version = manifest?.version ?: PluginManager.PLUGIN_VERSION_NOT_SET
        pluginInstance
    } catch (e: Throwable) { null }
}

private fun findPluginClassByScanning(loader: URLClassLoader, file: File): Class<*>? {
    return try {
        val zip = ZipFile(file)
        val candidateClassNames = zip.use { zf ->
            zf.entries().toList()
                .filter { it.name.endsWith(".class") && !it.name.contains("$") }
                .map { it.name.removeSuffix(".class").replace('/', '.') }
        }
        for (className in candidateClassNames) {
            try {
                val cls = loader.loadClass(className)
                if (BasePlugin::class.java.isAssignableFrom(cls) && cls != BasePlugin::class.java) return cls
            } catch (_: Throwable) { continue }
        }
        null
    } catch (e: Throwable) { null }
}

private fun readManifestFromZip(file: File): ByteArrayInputStream? {
    return try {
        val zip = ZipFile(file)
        val entry = zip.getEntry("manifest.json") ?: run { zip.close(); return null }
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        zip.close()
        ByteArrayInputStream(bytes)
    } catch (e: Throwable) { null }
}
