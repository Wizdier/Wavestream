package com.wavestream

import java.io.File

actual fun getDefaultPluginsDirPlatform(): File {
    val dir = File(System.getProperty("user.home"), ".wavestream")
    val pluginsDir = File(dir, "plugins")
    if (!pluginsDir.exists()) pluginsDir.mkdirs()
    return pluginsDir
}

actual object PlatformStorage {
    private val storageFile: File by lazy {
        val dir = File(System.getProperty("user.home"), ".wavestream")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "storage.json")
    }
    private val map: MutableMap<String, String> by lazy {
        if (storageFile.exists()) {
            runCatching {
                val text = storageFile.readText()
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                json.decodeFromString<Map<String, String>>(text).toMutableMap()
            }.getOrDefault(mutableMapOf())
        } else {
            mutableMapOf()
        }
    }

    actual fun getString(key: String): String? = map[key]

    actual fun putString(key: String, value: String) {
        map[key] = value
        runCatching {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
            storageFile.writeText(json.encodeToString(map))
        }
    }
}
