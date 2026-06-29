package com.wavestream.core.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Cross-platform key-value storage abstraction.
 */
interface PlatformStorage {
    fun <T> put(key: String, value: T)
    fun <T> put(folder: String, key: String, value: T)
    fun <T : Any> get(key: String, klass: Class<T>, default: T? = null): T?
    fun <T : Any> get(folder: String, key: String, klass: Class<T>, default: T? = null): T?
    fun getKeys(folder: String): List<String>
    fun remove(key: String)
    fun remove(folder: String, key: String)
    fun removeFolder(folder: String): Int
    fun contains(key: String): Boolean
    fun <T> putFlow(key: String, value: T): Flow<T>
    fun keysFlow(prefix: String = ""): Flow<List<String>>
}

/**
 * Singleton storage manager with proper kotlinx.serialization support.
 */
object DataStore {
    @Volatile
    private var impl: PlatformStorage? = null

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun init(storage: PlatformStorage) {
        impl = storage
    }

    @Volatile
    private var currentAccount: Int = 1

    fun setCurrentAccount(account: Int) { currentAccount = account }
    fun getCurrentAccount(): Int = currentAccount

    private fun require(): PlatformStorage =
        impl ?: throw IllegalStateException("DataStore not initialized. Call DataStore.init() first.")

    fun <T> setKey(path: String, value: T) = require().put(path, value)
    fun <T> setKey(folder: String, path: String, value: T) = require().put(folder, path, value)

    fun <T : Any> getKey(key: String, klass: Class<T>, default: T? = null): T? = require().get(key, klass, default)
    fun <T : Any> getKey(folder: String, key: String, klass: Class<T>, default: T? = null): T? = require().get(folder, key, klass, default)

    /** Store a serializable object as JSON string. */
    fun <T> setSerialized(key: String, value: T, serializer: KSerializer<T>) {
        val jsonStr = json.encodeToString(serializer, value)
        require().put(key, jsonStr)
    }

    /** Read a serializable object from JSON string. */
    fun <T> getSerialized(key: String, serializer: KSerializer<T>): T? {
        val raw = require().get(key, String::class.java, null) ?: return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    /** Store a list of serializable objects as JSON string. */
    fun <T> setSerializedList(key: String, value: List<T>, elementSerializer: KSerializer<T>) {
        val jsonStr = json.encodeToString(ListSerializer(elementSerializer), value)
        require().put(key, jsonStr)
    }

    /** Read a list of serializable objects from JSON string. */
    fun <T> getSerializedList(key: String, elementSerializer: KSerializer<T>): List<T>? {
        val raw = require().get(key, String::class.java, null) ?: return null
        return runCatching { json.decodeFromString(ListSerializer(elementSerializer), raw) }.getOrNull()
    }

    fun getKeys(folder: String): List<String> = require().getKeys(folder)
    fun removeKey(path: String) = require().remove(path)
    fun removeKey(folder: String, path: String) = require().remove(folder, path)
    fun removeKeys(folder: String): Int = require().removeFolder(folder)
    fun containsKey(key: String): Boolean = require().contains(key)
}

object DataStoreHelper {
    private const val ACCOUNT_KEY = "current_account"
    var currentAccount: Int
        get() = DataStore.getKey(ACCOUNT_KEY, Int::class.java, 1) ?: 1
        set(value) { DataStore.setKey(ACCOUNT_KEY, value) }
}
