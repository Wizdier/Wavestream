@file:Suppress("UNUSED", "unused", "MemberVisibilityCanBePrivate")

package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

/**
 * CloudStream 3's SyncAPI base class. Sync providers (Trakt, MAL, AniList,
 * Simkl, Kitsu) extend this and implement the abstract methods. WaveStream's
 * shim accepts registrations so CS3 plugins that bundle their own sync
 * providers can load — but the sync UI itself is implemented separately in
 * WaveStream's Settings → Sync screen.
 */
abstract class SyncAPI {
    abstract val name: String
    abstract val mainUrl: String
    abstract val idPrefix: String
    abstract val icon: String?

    /** Returns login info if the user is authenticated, else null. */
    abstract fun loginInfo(): LoginInfo?

    /** Initiate the OAuth/login flow. Returns true on success. */
    abstract suspend fun login(context: Any?): Boolean

    /** Log the user out and clear stored credentials. */
    abstract suspend fun logout()

    /** Search the sync service for [query]. */
    abstract suspend fun search(query: String): List<SearchResponse>?

    /** Get a single result by sync service id. */
    abstract suspend fun getResult(id: String): SearchResponse?

    /** Get all episodes for a show (used for completion tracking). */
    abstract suspend fun getEpisodes(id: String): List<Episode>?

    /** Set the user's score (0-10) for a show. */
    abstract suspend fun score(id: String, score: Int): Boolean

    /** Set the watch status (Watching=0, Completed=1, OnHold=2, Dropped=3, PlanToWatch=4). */
    abstract suspend fun setStatus(id: String, status: Int): Boolean

    /** Get the user's current watch status for a show. Returns (status, watchedEpisodes). */
    abstract suspend fun watchStatus(id: String): Pair<Int, Int>?

    /** Mark an episode as watched. */
    abstract suspend fun markEpisodeWatched(id: String, episode: Int): Boolean

    data class LoginInfo(
        val name: String,
        val account: String?,
        val profilePicture: String?
    )

    companion object {
        const val STATUS_WATCHING = 0
        const val STATUS_COMPLETED = 1
        const val STATUS_ON_HOLD = 2
        const val STATUS_DROPPED = 3
        const val STATUS_PLAN_TO_WATCH = 4
    }
}

/**
 * Registry of loaded sync APIs. CS3 plugins register their sync providers
 * here via [registerSyncAPI]. WaveStream's Settings → Sync screen can then
 * query this list to show available providers.
 */
object SyncAPIs {
    val apis: MutableList<SyncAPI> = java.util.concurrent.CopyOnWriteArrayList()

    fun register(api: SyncAPI) {
        apis.add(api)
    }

    fun byName(name: String): SyncAPI? = apis.firstOrNull { it.name.equals(name, true) }
    fun byIdPrefix(prefix: String): SyncAPI? = apis.firstOrNull { it.idPrefix.equals(prefix, true) }
}
