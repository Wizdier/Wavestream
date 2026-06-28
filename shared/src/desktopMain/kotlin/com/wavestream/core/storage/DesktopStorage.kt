package com.wavestream.core.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.prefs.Preferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Desktop (JVM) implementation of PlatformStorage, backed by java.util.prefs.Preferences.
 */
class DesktopStorage private constructor(
    private val prefs: Preferences,
    private val json: Json,
) : PlatformStorage {

    companion object {
        @Volatile
        private var instance: DesktopStorage? = null

        fun init(json: Json): DesktopStorage {
            return instance ?: synchronized(this) {
                instance ?: DesktopStorage(
                    Preferences.userRoot().node("com/wavestream/app"),
                    json,
                ).also { instance = it }
            }
        }
    }

    private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

    @Synchronized
    private fun <T> flowFor(key: String, initial: T?): MutableStateFlow<T?> {
        @Suppress("UNCHECKED_CAST")
        return flows.getOrPut(key) { MutableStateFlow(initial) } as MutableStateFlow<T?>
    }

    override fun <T> put(key: String, value: T) {
        val serializer = kotlinx.serialization.serializer(value!!::class.java as java.lang.reflect.Type)
        @Suppress("UNCHECKED_CAST")
        val serialized = if (value is String) value else json.encodeToString(serializer as kotlinx.serialization.KSerializer<Any>, value)
        prefs.put(key, serialized)
        flowFor(key, value).value = value
    }

    override fun <T> put(folder: String, key: String, value: T) {
        put("$folder/$key", value)
    }

    override fun <T : Any> get(key: String, klass: Class<T>, default: T?): T? {
        val raw = prefs.get(key, null) ?: return default
        return try {
            if (klass == String::class.java) raw as T
            else {
                @Suppress("UNCHECKED_CAST")
                json.decodeFromString(deserializeSerializer(klass), raw) as T
            }
        } catch (e: Exception) {
            default
        }
    }

    private fun <T : Any> deserializeSerializer(klass: Class<T>): kotlinx.serialization.KSerializer<T> {
        @Suppress("UNCHECKED_CAST")
        // Use java.lang.reflect.Type overload of serializer()
        return kotlinx.serialization.serializer(klass as java.lang.reflect.Type) as kotlinx.serialization.KSerializer<T>
    }

    override fun <T : Any> get(folder: String, key: String, klass: Class<T>, default: T?): T? {
        return get("$folder/$key", klass, default)
    }

    override fun getKeys(folder: String): List<String> {
        val prefix = "$folder/"
        return prefs.keys().filter { it.startsWith(prefix) }
    }

    override fun remove(key: String) {
        prefs.remove(key)
        flows.remove(key)
    }

    override fun remove(folder: String, key: String) {
        remove("$folder/$key")
    }

    override fun removeFolder(folder: String): Int {
        val prefix = "$folder/"
        val keys = prefs.keys().filter { it.startsWith(prefix) }
        keys.forEach { prefs.remove(it) }
        keys.forEach { flows.remove(it) }
        return keys.size
    }

    override fun contains(key: String): Boolean = prefs.get(key, null) != null

    @Suppress("UNCHECKED_CAST")
    override fun <T> putFlow(key: String, value: T): Flow<T> {
        put(key, value)
        return flowFor(key, value) as Flow<T>
    }

    override fun keysFlow(prefix: String): Flow<List<String>> {
        return MutableStateFlow(prefs.keys().filter { it.startsWith(prefix) })
    }
}
