package com.wavestream

import com.lagradost.cloudstream3.plugins.RepositoryData
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.RepositoryStorage
import com.wavestream.platform.wavePlatform
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.json.Json

/**
 * Bridges the platform preferences store to the library's [RepositoryManager].
 *
 * The library's RepositoryManager remains platform-agnostic via the
 * [RepositoryStorage] interface; this class is the shared-module glue that
 * actually persists repository metadata using [wavePlatform.preferences].
 *
 * The serialized format is JSON-encoded `Array<RepositoryData>` stored under
 * a single preferences key. We serialize manually because RepositoryData
 * itself uses kotlinx.serialization and the preferences API only stores
 * strings.
 */
object RepositoryStore : RepositoryStorage {

    private const val KEY_REPOSITORIES = "wavestream.repositories"
    private const val KEY_PREBUILT = "PREBUILT_REPOSITORIES"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Hooks this store into the library's RepositoryManager. Safe to call once at boot. */
    fun install() {
        RepositoryManager.storage = this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getKey(key: String): T? {
        val raw = wavePlatform.preferences.getString(key) ?: return null
        return try {
            when (key) {
                KEY_REPOSITORIES, KEY_PREBUILT ->
                    json.decodeFromString(ArraySerializer(RepositoryData.serializer()), raw) as? T
                else -> raw as? T
            }
        } catch (e: Throwable) {
            null
        }
    }

    override fun <T> setKey(key: String, value: T?) {
        if (value == null) {
            wavePlatform.preferences.remove(key)
            return
        }
        val serialized = when (key) {
            KEY_REPOSITORIES, KEY_PREBUILT -> try {
                @Suppress("UNCHECKED_CAST")
                json.encodeToString(
                    ArraySerializer(RepositoryData.serializer()),
                    value as Array<RepositoryData>,
                )
            } catch (e: Throwable) { return }
            else -> value.toString()
        }
        wavePlatform.preferences.putString(key, serialized)
    }

    override fun removeKey(key: String) {
        wavePlatform.preferences.remove(key)
    }
}
