package com.wavestream.core.sync

import com.wavestream.api.LoadResponse
import com.wavestream.api.SearchResponse
import com.wavestream.core.storage.DataStore

/**
 * Sync provider abstraction — mirrors CloudStream's `syncproviders/SyncAPI.kt`.
 *
 * A sync provider tracks the user's watch status across an external service
 * (MAL, AniList, Kitsu, Trakt, etc.) or locally.
 *
 * Implementations:
 *   - LocalListSyncApi — always available, stores data in DataStore
 *   - MalSyncApi — MyAnimeList (OAuth device flow)
 *   - AniListSyncApi — AniList (GraphQL)
 *   - TraktSyncApi — Trakt (OAuth device flow)
 */
interface SyncAPI {
    /** Unique prefix for this sync provider (e.g. "mal", "anilist"). */
    val idPrefix: String

    /** Human-readable name. */
    val name: String

    /** Whether the user is authenticated. */
    fun isAuthenticated(): Boolean

    /** Get stored auth data. */
    fun getAuthData(): AuthData?

    /** Start the auth flow (returns a URL the user should open in a browser). */
    suspend fun authenticate(): String?

    /** Sign out and clear stored auth data. */
    fun signOut()

    /** Get the user's watch list. */
    suspend fun getWatchList(): List<WatchStatusItem>?

    /** Set watch status for an item. */
    suspend fun setWatchStatus(item: WatchStatusItem): Boolean

    /** Get stored IDs for a LoadResponse (e.g. mal_id, anilist_id). */
    fun getStoredIds(loadResponse: LoadResponse): Map<String, String>
}

data class AuthData(
    val token: String,
    val refreshToken: String? = null,
    val accountId: String? = null,
    val expiresAt: Long? = null,
)

data class WatchStatusItem(
    val id: String,
    val title: String,
    val status: WatchStatus,
    val episode: Int? = null,
    val season: Int? = null,
    val score: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class WatchStatus {
    Watching, Completed, OnHold, Dropped, PlanToWatch, ReWatching;
}

/**
 * Local list sync — always available, stores watch status in DataStore.
 *
 * Mirrors CloudStream's `LocalList.kt`.
 */
object LocalListSyncApi : SyncAPI {
    override val idPrefix: String = "local"
    override val name: String = "Local List"

    private const val STORAGE_FOLDER = "local_list"
    private const val WATCH_LIST_KEY = "watch_list"
    private const val AUTH_KEY = "local_auth"

    override fun isAuthenticated(): Boolean = true  // Always authenticated (no account needed)

    override fun getAuthData(): AuthData? {
        // Local list doesn't have real auth, but returns a placeholder for API consistency
        return AuthData(token = "local", accountId = "local")
    }

    override suspend fun authenticate(): String? = null  // No auth needed

    override fun signOut() {
        DataStore.removeKey(STORAGE_FOLDER, WATCH_LIST_KEY)
    }

    override suspend fun getWatchList(): List<WatchStatusItem>? {
        @Suppress("UNCHECKED_CAST")
        return DataStore.getKey(STORAGE_FOLDER, WATCH_LIST_KEY, List::class.java) as? List<WatchStatusItem>
    }

    override suspend fun setWatchStatus(item: WatchStatusItem): Boolean {
        val list = getWatchList()?.toMutableList() ?: mutableListOf()
        list.removeAll { it.id == item.id }
        list.add(item)
        DataStore.setKey(STORAGE_FOLDER, WATCH_LIST_KEY, list)
        return true
    }

    override fun getStoredIds(loadResponse: LoadResponse): Map<String, String> {
        // Local list uses the loadResponse's url as the ID
        return mapOf("local" to loadResponse.url)
    }
}

/**
 * Singleton manager for all sync providers — mirrors CloudStream's AccountManager.
 */
object SyncProviderManager {
    val localList = LocalListSyncApi

    // Future: add mal, anilist, trakt providers here
    val allProviders: List<SyncAPI> = listOf(
        localList,
        // MalSyncApi(),
        // AniListSyncApi(),
        // TraktSyncApi(),
    )

    fun getProvider(idPrefix: String): SyncAPI? = allProviders.firstOrNull { it.idPrefix == idPrefix }

    fun getAuthenticatedProviders(): List<SyncAPI> = allProviders.filter { it.isAuthenticated() }
}
