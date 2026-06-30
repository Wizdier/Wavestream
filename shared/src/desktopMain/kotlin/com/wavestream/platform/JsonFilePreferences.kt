package com.wavestream.platform

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Desktop preferences persisted to a JSON file. Thread-safe via a single
 * lock for both reads and writes — fine for the relatively low access rate
 * of UI settings.
 */
class JsonFilePreferences(private val file: File) : WavePreferences {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val lock = Any()

    private var cache: JsonObject = load()

    private fun load(): JsonObject = try {
        if (file.exists()) json.parseToJsonElement(file.readText()).jsonObject
        else buildJsonObject { }
    } catch (e: Throwable) {
        buildJsonObject { }
    }

    private fun persist() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(JsonObject.serializer(), cache))
        } catch (e: Throwable) {
            // best-effort persistence; don't crash the UI
            e.printStackTrace()
        }
    }

    override fun getString(key: String, default: String?): String? = synchronized(lock) {
        (cache[key] as? JsonPrimitive)?.contentOrNull ?: default
    }

    override fun putString(key: String, value: String?) = synchronized(lock) {
        val new = buildJsonObject {
            cache.forEach { (k, v) -> put(k, v) }
            if (value != null) put(key, value) else Unit
        }
        cache = new
        persist()
    }

    override fun getInt(key: String, default: Int): Int = synchronized(lock) {
        (cache[key] as? JsonPrimitive)?.intOrNull ?: default
    }

    override fun putInt(key: String, value: Int) = synchronized(lock) {
        val new = buildJsonObject {
            cache.forEach { (k, v) -> put(k, v) }
            put(key, value)
        }
        cache = new
        persist()
    }

    override fun getBool(key: String, default: Boolean): Boolean = synchronized(lock) {
        (cache[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: default
    }

    override fun putBool(key: String, value: Boolean) = synchronized(lock) {
        val new = buildJsonObject {
            cache.forEach { (k, v) -> put(k, v) }
            put(key, value)
        }
        cache = new
        persist()
    }

    override fun remove(key: String) = synchronized(lock) {
        val new = buildJsonObject {
            cache.forEach { (k, v) -> if (k != key) put(k, v) }
        }
        cache = new
        persist()
    }

    override fun keys(): Set<String> = synchronized(lock) { cache.keys }
}
