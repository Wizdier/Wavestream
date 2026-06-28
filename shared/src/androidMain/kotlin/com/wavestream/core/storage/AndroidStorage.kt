package com.wavestream.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

class AndroidStorage private constructor(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val json: Json,
) : PlatformStorage {

    companion object {
        @Volatile
        private var instance: AndroidStorage? = null

        fun init(context: Context, json: Json): AndroidStorage {
            return instance ?: synchronized(this) {
                instance ?: AndroidStorage(
                    context.applicationContext,
                    context.applicationContext.getSharedPreferences("wavestream_prefs", Context.MODE_PRIVATE),
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
        prefs.edit { putString(key, serialized) }
        flowFor(key, value).value = value
    }

    override fun <T> put(folder: String, key: String, value: T) {
        put("$folder/$key", value)
    }

    override fun <T : Any> get(key: String, klass: Class<T>, default: T?): T? {
        val raw = prefs.getString(key, null) ?: return default
        return try {
            if (klass == String::class.java) raw as T
            else {
                @Suppress("UNCHECKED_CAST")
                val ser = kotlinx.serialization.serializer(klass as java.lang.reflect.Type) as kotlinx.serialization.KSerializer<T>
                json.decodeFromString(ser, raw)
            }
        } catch (e: Exception) { default }
    }

    override fun <T : Any> get(folder: String, key: String, klass: Class<T>, default: T?): T? {
        return get("$folder/$key", klass, default)
    }

    override fun getKeys(folder: String): List<String> {
        return prefs.all.keys.filter { it.startsWith("$folder/") }
    }

    override fun remove(key: String) {
        prefs.edit { remove(key) }
        flows.remove(key)
    }

    override fun remove(folder: String, key: String) {
        remove("$folder/$key")
    }

    override fun removeFolder(folder: String): Int {
        val keys = prefs.all.keys.filter { it.startsWith("$folder/") }
        prefs.edit { keys.forEach { remove(it) } }
        keys.forEach { flows.remove(it) }
        return keys.size
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    @Suppress("UNCHECKED_CAST")
    override fun <T> putFlow(key: String, value: T): Flow<T> {
        put(key, value)
        return flowFor(key, value) as Flow<T>
    }

    override fun keysFlow(prefix: String): Flow<List<String>> {
        val flow = MutableStateFlow(prefs.all.keys.filter { it.startsWith(prefix) })
        prefs.registerOnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey.startsWith(prefix)) {
                flow.value = prefs.all.keys.filter { it.startsWith(prefix) }
            }
        }
        return flow
    }
}
