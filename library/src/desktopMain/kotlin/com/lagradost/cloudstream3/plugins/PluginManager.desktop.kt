package com.lagradost.cloudstream3.plugins

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.zip.ZipFile

actual suspend fun loadPluginFromFile(file: File): BasePlugin? {
    return try {
        val loader = URLClassLoader(arrayOf(URL("file:${file.absolutePath}")), BasePlugin::class.java.classLoader)
        val manifestStream = loader.getResourceAsStream("manifest.json") ?: readManifestFromZip(file)
        val manifest: BasePlugin.Manifest? = manifestStream?.use { s ->
            runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(BasePlugin.Manifest.serializer(), s.bufferedReader().use { it.readText() }) }.getOrNull()
        }
        val pluginClass = when {
            manifest?.pluginClassName != null -> { try { loader.loadClass(manifest.pluginClassName) } catch (e: ClassNotFoundException) { findPluginClassByScanning(loader, file) } }
            else -> findPluginClassByScanning(loader, file)
        } ?: return null
        val instance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
        instance.version = manifest?.version ?: PluginManager.PLUGIN_VERSION_NOT_SET
        instance
    } catch (e: Throwable) { null }
}

private fun findPluginClassByScanning(loader: URLClassLoader, file: File): Class<*>? {
    return try {
        val zip = ZipFile(file)
        val names = zip.use { zf -> zf.entries().toList().filter { it.name.endsWith(".class") && !it.name.contains("$") }.map { it.name.removeSuffix(".class").replace("/", ".") } }
        for (name in names) { try { val cls = loader.loadClass(name); if (BasePlugin::class.java.isAssignableFrom(cls) && cls != BasePlugin::class.java) return cls } catch (_: Throwable) {} }
        null
    } catch (e: Throwable) { null }
}

private fun readManifestFromZip(file: File): ByteArrayInputStream? {
    return try { val zip = ZipFile(file); val entry = zip.getEntry("manifest.json") ?: run { zip.close(); return null }; val bytes = zip.getInputStream(entry).use { it.readBytes() }; zip.close(); ByteArrayInputStream(bytes) } catch (e: Throwable) { null }
}
actual fun isDesktopPlatform(): Boolean = true
