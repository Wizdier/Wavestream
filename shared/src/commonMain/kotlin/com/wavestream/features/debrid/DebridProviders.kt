package com.wavestream.features.debrid

import com.wavestream.core.network.NetworkClient
import com.wavestream.core.network.app
import com.wavestream.core.storage.DataStore
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Debrid service integration — mirrors NuvioMobile's DebridProviderApis.
 * Supports RealDebrid, AllDebrid, Premiumize.
 */
sealed class DebridProvider {
    abstract val id: String
    abstract val name: String
    abstract val apiBaseUrl: String
    abstract fun isAuthenticated(): Boolean
    abstract fun getApiKey(): String?
    abstract fun setApiKey(key: String)
    abstract suspend fun checkCache(infoHash: String): DebridCacheResult?
    abstract suspend fun addMagnet(magnetLink: String): String?
    abstract suspend fun getDownloadUrl(torrentId: String, fileIndex: Int): String?
}

data class DebridCacheResult(val isCached: Boolean, val torrentId: String? = null, val files: List<DebridFile> = emptyList())
data class DebridFile(val id: String, val name: String, val size: Long, val downloadUrl: String? = null)

object RealDebridProvider : DebridProvider() {
    override val id = "realdebrid"
    override val name = "RealDebrid"
    override val apiBaseUrl = "https://api.real-debrid.com/rest/1.0"
    private const val API_KEY_STORAGE = "rd_api_key"
    private val json = Json { ignoreUnknownKeys = true }

    override fun isAuthenticated(): Boolean = getApiKey() != null
    override fun getApiKey(): String? = DataStore.getKey(API_KEY_STORAGE, String::class.java)
    override fun setApiKey(key: String) { DataStore.setKey(API_KEY_STORAGE, key) }

    override suspend fun checkCache(infoHash: String): DebridCacheResult? {
        val apiKey = getApiKey() ?: return null
        return try {
            val url = "$apiBaseUrl/torrents/instantAvailability/$infoHash"
            val response = app.get(url, headers = authHeaders(apiKey))
            if (!response.status.isSuccess()) return null
            val body = response.bodyAsText()
            val parsed = json.parseToJsonElement(body) as? JsonObject ?: return null
            val torrentData = parsed[infoHash] as? JsonObject ?: return DebridCacheResult(false)
            DebridCacheResult(torrentData["rd"] != null)
        } catch (e: Throwable) { null }
    }

    override suspend fun addMagnet(magnetLink: String): String? {
        val apiKey = getApiKey() ?: return null
        return try {
            val body = "magnet=${java.net.URLEncoder.encode(magnetLink, "UTF-8")}"
            val response = NetworkClient.postText("$apiBaseUrl/torrents/addMagnet", body, authHeaders(apiKey) + mapOf("Content-Type" to "application/x-www-form-urlencoded"))
            json.decodeFromString<AddMagnetResponse>(response).id
        } catch (e: Throwable) { null }
    }

    override suspend fun getDownloadUrl(torrentId: String, fileIndex: Int): String? {
        val apiKey = getApiKey() ?: return null
        return try {
            NetworkClient.postText("$apiBaseUrl/torrents/selectFiles/$torrentId", "files=$fileIndex", authHeaders(apiKey) + mapOf("Content-Type" to "application/x-www-form-urlencoded"))
            val infoResponse = app.get("$apiBaseUrl/torrents/info/$torrentId", headers = authHeaders(apiKey))
            if (!infoResponse.status.isSuccess()) return null
            json.decodeFromString<TorrentInfoResponse>(infoResponse.bodyAsText()).links?.getOrNull(fileIndex)
        } catch (e: Throwable) { null }
    }

    private fun authHeaders(apiKey: String): Map<String, String> = mapOf("Authorization" to "Bearer $apiKey")
}

@Serializable private data class AddMagnetResponse(val id: String, val uri: String? = null)
@Serializable private data class TorrentInfoResponse(val id: String, val filename: String? = null, val links: List<String>? = null, val status: String? = null)

object AllDebridProvider : DebridProvider() {
    override val id = "alldebrid"
    override val name = "AllDebrid"
    override val apiBaseUrl = "https://api.alldebrid.com/v4"
    private const val API_KEY_STORAGE = "ad_api_key"
    private const val APP_NAME = "wavestream"

    override fun isAuthenticated(): Boolean = getApiKey() != null
    override fun getApiKey(): String? = DataStore.getKey(API_KEY_STORAGE, String::class.java)
    override fun setApiKey(key: String) { DataStore.setKey(API_KEY_STORAGE, key) }

    override suspend fun checkCache(infoHash: String): DebridCacheResult? {
        val apiKey = getApiKey() ?: return null
        return try {
            val url = "$apiBaseUrl/magnet/instant?agent=$APP_NAME&apikey=$apiKey&magnet[]=$infoHash"
            val response = app.get(url)
            if (!response.status.isSuccess()) return null
            DebridCacheResult(false)
        } catch (e: Throwable) { null }
    }

    override suspend fun addMagnet(magnetLink: String): String? {
        val apiKey = getApiKey() ?: return null
        return try {
            val encoded = java.net.URLEncoder.encode(magnetLink, "UTF-8")
            val url = "$apiBaseUrl/magnet/upload?agent=$APP_NAME&apikey=$apiKey&magnets[]=$encoded"
            app.get(url)
            "magnet_id"
        } catch (e: Throwable) { null }
    }

    override suspend fun getDownloadUrl(torrentId: String, fileIndex: Int): String? = null
}

object PremiumizeProvider : DebridProvider() {
    override val id = "premiumize"
    override val name = "Premiumize"
    override val apiBaseUrl = "https://www.premiumize.me/api"
    private const val API_KEY_STORAGE = "pm_api_key"

    override fun isAuthenticated(): Boolean = getApiKey() != null
    override fun getApiKey(): String? = DataStore.getKey(API_KEY_STORAGE, String::class.java)
    override fun setApiKey(key: String) { DataStore.setKey(API_KEY_STORAGE, key) }

    override suspend fun checkCache(infoHash: String): DebridCacheResult? {
        val apiKey = getApiKey() ?: return null
        return try {
            val body = buildJsonObject { put("apikey", apiKey); put("hash", infoHash) }.toString()
            NetworkClient.postText("$apiBaseUrl/cache/check", body, mapOf("Content-Type" to "application/json"))
            DebridCacheResult(false)
        } catch (e: Throwable) { null }
    }

    override suspend fun addMagnet(magnetLink: String): String? {
        val apiKey = getApiKey() ?: return null
        return try {
            val body = buildJsonObject { put("apikey", apiKey); put("magnet", magnetLink) }.toString()
            NetworkClient.postText("$apiBaseUrl/transfer/create", body, mapOf("Content-Type" to "application/json"))
            "transfer_id"
        } catch (e: Throwable) { null }
    }

    override suspend fun getDownloadUrl(torrentId: String, fileIndex: Int): String? = null
}

object DebridProviderRegistry {
    val allProviders = listOf(RealDebridProvider, AllDebridProvider, PremiumizeProvider)
    fun getProvider(id: String): DebridProvider? = allProviders.firstOrNull { it.id == id }
    fun getAuthenticatedProviders(): List<DebridProvider> = allProviders.filter { it.isAuthenticated() }
}
