package com.wavestream.plugins

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.zip.ZipFile

/**
 * Desktop (JVM) implementation of plugin loading via URLClassLoader.
 *
 * Supports two file types:
 *
 * 1. `.jar` files (CloudStream's jarUrl for Desktop):
 *    - Contains JVM .class files
 *    - Does NOT contain manifest.json
 *    - We scan all classes for ones extending BasePlugin
 *
 * 2. `.cs3` / `.ws3` files (CloudStream's url for Android):
 *    - Contains manifest.json + classes.dex (Android DEX bytecode)
 *    - DEX cannot be loaded on Desktop JVM
 *    - We read manifest.json but class loading will fail
 *    - The error message guides the user to use .jar files instead
 *
 * CloudStream's official repo provides both URLs — SitePlugin.bestUrlForPlatform()
 * returns the .jar URL on Desktop, .cs3 URL on Android.
 */
actual suspend fun loadPluginInternalPlatform(file: File): BasePlugin? {
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
        val version = manifest?.version ?: PLUGIN_VERSION_NOT_SET
        val pluginClassName = manifest?.pluginClassName

        println("[PluginManager] Loading $name v$version (class=$pluginClassName) from ${file.name}")

        // 3. Resolve the plugin class
        val pluginClass = when {
            pluginClassName != null -> {
                try {
                    loader.loadClass(pluginClassName)
                } catch (e: ClassNotFoundException) {
                    println("[PluginManager] Class '$pluginClassName' not found in ${file.name}. " +
                        "Falling back to scanning for BasePlugin subclasses.")
                    findPluginClassByScanning(loader, file)
                }
            }
            else -> {
                // No manifest pluginClassName — scan the jar for a class extending BasePlugin
                findPluginClassByScanning(loader, file)
            }
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
 *
 * This is used when the manifest.json is missing or doesn't specify pluginClassName.
 * CloudStream's .jar files don't include manifest.json, so this is the only way
 * to load them on Desktop.
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
                // Skip classes that can't be loaded (might depend on missing classes)
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
        // Read entry into a byte array and return a ByteArrayInputStream (so we can close the zip)
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        zip.close()
        ByteArrayInputStream(bytes)
    } catch (e: Throwable) {
        null
    }
}

actual fun checkSafeModeFile(): Boolean {
    // Desktop: check for a "safe" file in user home
    val safeFile = File(System.getProperty("user.home"), ".wavestream/safe")
    return safeFile.exists()
}
