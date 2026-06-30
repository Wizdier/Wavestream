package com.wavestream

import java.io.File

actual fun getDefaultPluginsDirPlatform(): File {
    val dir = File(System.getProperty("user.home"), ".wavestream"); val pluginsDir = File(dir, "plugins")
    if (!pluginsDir.exists()) pluginsDir.mkdirs(); return pluginsDir
}

actual object PlatformStorage {
    private val storageFile: File by lazy { val dir = File(System.getProperty("user.home"), ".wavestream"); if (!dir.exists()) dir.mkdirs(); File(dir, "storage.json") }
    private val map: MutableMap<String, String> by lazy {
        if (storageFile.exists()) runCatching { kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<Map<String, String>>(storageFile.readText()).toMutableMap() }.getOrDefault(mutableMapOf()) else mutableMapOf()
    }
    actual fun getString(key: String): String? = map[key]
    actual fun putString(key: String, value: String) { map[key] = value; runCatching { storageFile.writeText(kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(map)) } }
}
