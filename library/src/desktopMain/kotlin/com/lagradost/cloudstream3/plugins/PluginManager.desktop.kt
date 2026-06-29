package com.lagradost.cloudstream3.plugins

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.zip.ZipFile

/**
 * Desktop (JVM) implementation of plugin loading via URLClassLoader.
 *
 * Supports:
 *   - Plain .jar files (CloudStream's jarUrl for Desktop — contains JVM .class files)
 *     CloudStream's .jar files don't include manifest.json — we scan for BasePlugin subclasses.
 *   - .ws3 / .cs3 files (ZIPs containing manifest.json + classes — but on JVM, the DEX bytecode
 *     cannot be loaded, so this will only work if the .cs3 file actually contains .class files).
 */
actual suspend fun loadPluginFromFile(file: File): BasePlugin? {
    val filePath = file.absolutePath

    return try {
        // 1. Create URLClassLoader pointing at the file
        val url = URL("file:$filePath")
        val loader = URLClassLoader(arrayOf(url), BasePlugin::class.java.classLoader)

        // 2. Try to read manifest.json from the file
        val manifestStream = loader.getResourceAsStream("manifest.json")
            ?: readManifestFromZip(file)

        val manifest: BasePlugin.Manifest? = manifestStream?.use { stream ->
            val text = stream.bufferedReader().use { it.readText() }
            runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(BasePlugin.Manifest.serializer(), text) }.getOrNull()
        }

        val name = manifest?.name ?: file.nameWithoutExtension
        val version = manifest?.version ?: PluginManager.PLUGIN_VERSION_NOT_SET
        val pluginClassName = manifest?.pluginClassName

        println("[PluginManager] Loading $name v$version (class=$pluginClassName) from ${file.name}")

        // 3. Resolve the plugin class
        val pluginClass = when {
            pluginClassName != null -> {
                try {
                    loader.loadClass(pluginClassName)
                } catch (e: ClassNotFoundException) {
                    println("[PluginManager] Class '$pluginClassName' not found in ${file.name}, scanning...")
                    findPluginClassByScanning(loader, file)
                }
            }
            else -> findPluginClassByScanning(loader, file)
        } ?: run {
            println("[PluginManager] Could not find any plugin class in ${file.name}")
            return null
        }

        val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
        pluginInstance.version = version
        pluginInstance
    } catch (e: Throwable) {
        println("[PluginManager] Failed to load $file: ${e.message}")
        e.printStackTrace()
        null
    }
}

/**
 * Scan a jar file's entries for a class that extends BasePlugin.
 * Returns the first match, or null if none found.
 */
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
                if (BasePlugin::class.java.isAssignableFrom(cls) && cls != BasePlugin::class.java) {
                    println("[PluginManager] Found plugin class by scanning: $className")
                    return cls
                }
            } catch (_: Throwable) {
                continue
            }
        }
        null
    } catch (e: Throwable) {
        println("[PluginManager] Failed to scan ${file.name}: ${e.message}")
        null
    }
}

private fun readManifestFromZip(file: File): ByteArrayInputStream? {
    return try {
        val zip = ZipFile(file)
        val entry = zip.getEntry("manifest.json") ?: run {
            zip.close()
            return null
        }
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        zip.close()
        ByteArrayInputStream(bytes)
    } catch (e: Throwable) {
        null
    }
}
