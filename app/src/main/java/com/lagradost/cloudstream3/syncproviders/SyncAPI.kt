@file:Suppress("UNUSED", "unused")
package com.lagradost.cloudstream3.syncproviders
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SearchResponse
abstract class SyncAPI {
    abstract val name: String
    abstract val mainUrl: String
    abstract val idPrefix: String
    abstract val icon: String?
    abstract fun loginInfo(): LoginInfo?
    abstract suspend fun login(context: Any?): Boolean
    abstract suspend fun logout()
    abstract suspend fun search(query: String): List<SearchResponse>?
    abstract suspend fun getResult(id: String): SearchResponse?
    abstract suspend fun getEpisodes(id: String): List<Episode>?
    abstract suspend fun score(id: String, score: Int): Boolean
    abstract suspend fun setStatus(id: String, status: Int): Boolean
    abstract suspend fun watchStatus(id: String): Pair<Int, Int>?
    data class LoginInfo(val name: String, val account: String?, val profilePicture: String?)
}
object SyncAPIs {
    val apis: MutableList<SyncAPI> = java.util.concurrent.CopyOnWriteArrayList()
    fun register(api: SyncAPI) { apis.add(api) }
    fun byName(name: String): SyncAPI? = apis.firstOrNull { it.name.equals(name, true) }
}
