package com.wavestream.core.storage

import kotlinx.coroutines.flow.Flow

/**
 * Cross-platform key-value storage abstraction.
 *
 * On Android this is backed by SharedPreferences (via androidx.preference).
 * On Desktop this is backed by java.util.prefs.Preferences.
 *
 * Mirrors CloudStream's `DataStore` object + `CloudStreamApp.setKey/getKey` helpers.
 *
 * All values are serialized to JSON strings before storage.
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
 * Singleton storage manager. Initialized with platform-specific implementation on startup.
 */
object DataStore {
    @Volatile
    private var impl: PlatformStorage? = null

    fun init(storage: PlatformStorage) {
        impl = storage
    }

    @Volatile
    private var currentAccount: Int = 1

    fun setCurrentAccount(account: Int) {
        currentAccount = account
    }

    fun getCurrentAccount(): Int = currentAccount

    private fun require(): PlatformStorage =
        impl ?: throw IllegalStateException("DataStore not initialized. Call DataStore.init() first.")

    fun <T> setKey(path: String, value: T) = require().put(path, value)
    fun <T> setKey(folder: String, path: String, value: T) = require().put(folder, path, value)

    fun <T : Any> getKey(key: String, klass: Class<T>, default: T? = null): T? = require().get(key, klass, default)
    fun <T : Any> getKey(folder: String, key: String, klass: Class<T>, default: T? = null): T? = require().get(folder, key, klass, default)

    fun getKeys(folder: String): List<String> = require().getKeys(folder)
    fun removeKey(path: String) = require().remove(path)
    fun removeKey(folder: String, path: String) = require().remove(folder, path)
    fun removeKeys(folder: String): Int = require().removeFolder(folder)
    fun containsKey(key: String): Boolean = require().contains(key)
}

/**
 * High-level convenience helper — mirrors CloudStream's `DataStoreHelper`.
 */
object DataStoreHelper {
    private const val ACCOUNT_KEY = "current_account"
    private const val ACCOUNTS_KEY = "accounts"
    private const val REPOSITORIES_KEY = "repositories"
    private const val PLUGINS_KEY = "plugins_online"
    private const val PLUGINS_KEY_LOCAL = "plugins_local"
    private const val SEARCH_HISTORY_KEY = "search_history"
    private const val WATCHED_KEY = "watched"
    private const val DOWNLOAD_HEADER_CACHE = "download_header_cache"
    private const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
    private const val PROVIDER_STATUS_KEY = "provider_status"
    private const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
    private const val LAST_RESUME_KEY = "last_resume"
    private const val PREFERENCES_KEY = "user_preferences"

    var currentAccount: Int
        get() = DataStore.getKey(ACCOUNT_KEY, Int::class.java, 1) ?: 1
        set(value) {
            DataStore.setKey(ACCOUNT_KEY, value)
            DataStore.setCurrentAccount(value)
        }
}

