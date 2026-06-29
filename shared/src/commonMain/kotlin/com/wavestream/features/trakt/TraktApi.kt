package com.wavestream.features.trakt

import com.wavestream.core.network.NetworkClient
import com.wavestream.core.network.app
import com.wavestream.core.storage.DataStore
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Trakt integration — mirrors NuvioMobile's TraktAuthRepository + TraktScrobbleRepository.
 *
 * Trakt uses OAuth 2.0 device flow:
 *   1. POST /oauth/device/code → get device_code + user_code + verification_url
 *   2. User visits verification_url and enters user_code
 *   3. Poll POST /oauth/device/token until access_token is returned
 *   4. Use access_token for all subsequent API calls
 *
 * API docs: https://trakt.docs.apiary.io/
 */
object TraktApi {
    private const val BASE_URL = "https://api.trakt.tv"
    private const val API_VERSION = "2"

    // These should be set via build config (TraktConfig.CLIENT_ID / CLIENT_SECRET)
    // For now, hardcoded as empty strings — user must provide their own.
    var clientId: String = ""
    var clientSecret: String = ""

    val json = Json { ignoreUnknownKeys = true }

    private const val AUTH_KEY = "trakt_auth"

    /**
     * Start the device flow — get device_code + user_code.
     */
    suspend fun startDeviceFlow(): DeviceCodeResponse? {
        if (clientId.isBlank()) return null
        return try {
            val body = buildJsonObject {
                put("client_id", clientId)
            }.toString()
            val response = NetworkClient.postText("$BASE_URL/oauth/device/code", body, mapOf("Content-Type" to "application/json"))
            json.decodeFromString<DeviceCodeResponse>(response)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Poll for the access token (after user authorizes).
     */
    suspend fun pollForToken(deviceCode: String): TraktAuth? {
        if (clientId.isBlank() || clientSecret.isBlank()) return null
        return try {
            val body = buildJsonObject {
                put("code", deviceCode)
                put("client_id", clientId)
                put("client_secret", clientSecret)
                put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            }.toString()
            val response = NetworkClient.postText("$BASE_URL/oauth/device/token", body, mapOf("Content-Type" to "application/json"))
            val token = json.decodeFromString<TraktTokenResponse>(response)
            val auth = TraktAuth(
                accessToken = token.access_token,
                refreshToken = token.refresh_token,
                expiresAt = System.currentTimeMillis() + (token.expires_in * 1000),
                scope = token.scope,
                createdAt = token.created_at,
            )
            saveAuth(auth)
            auth
        } catch (e: Throwable) {
            null
        }
    }

    private val traktAuthSerializer = TraktAuth.serializer()

    fun saveAuth(auth: TraktAuth) {
        DataStore.setSerialized(AUTH_KEY, auth, traktAuthSerializer)
    }

    fun loadAuth(): TraktAuth? {
        return DataStore.getSerialized(AUTH_KEY, traktAuthSerializer)
    }

    fun isAuthenticated(): Boolean = loadAuth() != null

    fun signOut() {
        DataStore.removeKey(AUTH_KEY)
    }

    /**
     * Get the user's watch history.
     */
    suspend fun getWatchHistory(): List<TraktHistoryItem>? {
        val auth = loadAuth() ?: return null
        return try {
            val response = app.get("$BASE_URL/sync/history", headers = mapOf(
                "Authorization" to "Bearer ${auth.accessToken}",
                "trakt-api-version" to API_VERSION,
                "trakt-api-key" to clientId,
            ))
            if (!response.status.isSuccess()) return null
            json.decodeFromString(response.bodyAsText())
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Add an item to the user's watch history.
     */
    suspend fun addToHistory(imdbId: String, type: String, watchedAt: Long = System.currentTimeMillis()): Boolean {
        val auth = loadAuth() ?: return false
        return try {
            val body = buildJsonObject {
                put(type, kotlinx.serialization.json.JsonArray(listOf(buildJsonObject {
                    put("ids", buildJsonObject { put("imdb", imdbId) })
                    put("watched_at", watchedAt / 1000)  // Trakt uses Unix seconds
                })))
            }.toString()
            val response = NetworkClient.postText("$BASE_URL/sync/history", body, mapOf(
                "Authorization" to "Bearer ${auth.accessToken}",
                "trakt-api-version" to API_VERSION,
                "trakt-api-key" to clientId,
                "Content-Type" to "application/json",
            ))
            response.isNotEmpty()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Start scrobbling (playback tracking).
     */
    suspend fun startScrobble(imdbId: String, type: String, progress: Double): Boolean {
        val auth = loadAuth() ?: return false
        return try {
            val body = buildJsonObject {
                put(type, buildJsonObject {
                    put("ids", buildJsonObject { put("imdb", imdbId) })
                    put("progress", progress)
                })
            }.toString()
            val response = NetworkClient.postText("$BASE_URL/scrobble/start", body, mapOf(
                "Authorization" to "Bearer ${auth.accessToken}",
                "trakt-api-version" to API_VERSION,
                "trakt-api-key" to clientId,
                "Content-Type" to "application/json",
            ))
            response.isNotEmpty()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Pause scrobbling.
     */
    suspend fun pauseScrobble(imdbId: String, type: String, progress: Double): Boolean {
        val auth = loadAuth() ?: return false
        return try {
            val body = buildJsonObject {
                put(type, buildJsonObject {
                    put("ids", buildJsonObject { put("imdb", imdbId) })
                    put("progress", progress)
                })
            }.toString()
            val response = NetworkClient.postText("$BASE_URL/scrobble/pause", body, mapOf(
                "Authorization" to "Bearer ${auth.accessToken}",
                "trakt-api-version" to API_VERSION,
                "trakt-api-key" to clientId,
                "Content-Type" to "application/json",
            ))
            response.isNotEmpty()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Stop scrobbling (also marks as watched if progress > 80%).
     */
    suspend fun stopScrobble(imdbId: String, type: String, progress: Double): Boolean {
        val auth = loadAuth() ?: return false
        return try {
            val body = buildJsonObject {
                put(type, buildJsonObject {
                    put("ids", buildJsonObject { put("imdb", imdbId) })
                    put("progress", progress)
                })
            }.toString()
            val response = NetworkClient.postText("$BASE_URL/scrobble/stop", body, mapOf(
                "Authorization" to "Bearer ${auth.accessToken}",
                "trakt-api-version" to API_VERSION,
                "trakt-api-key" to clientId,
                "Content-Type" to "application/json",
            ))
            response.isNotEmpty()
        } catch (e: Throwable) {
            false
        }
    }
}

@Serializable
data class DeviceCodeResponse(
    val device_code: String,
    val user_code: String,
    val verification_url: String,
    val expires_in: Int,
    val interval: Int,
)

@Serializable
data class TraktTokenResponse(
    val access_token: String,
    val refresh_token: String,
    val scope: String,
    val expires_in: Long,
    val created_at: Long,
)

@Serializable
data class TraktAuth(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val scope: String,
    val createdAt: Long,
)

@Serializable
data class TraktHistoryItem(
    val id: Long,
    val watched_at: String,
    val action: String,
    val type: String,
    val movie: TraktMovie? = null,
    val episode: TraktEpisode? = null,
    val show: TraktShow? = null,
)

@Serializable
data class TraktMovie(
    val title: String,
    val year: Int,
    val ids: TraktIds,
)

@Serializable
data class TraktShow(
    val title: String,
    val year: Int,
    val ids: TraktIds,
)

@Serializable
data class TraktEpisode(
    val season: Int,
    val number: Int,
    val title: String,
    val ids: TraktIds,
)

@Serializable
data class TraktIds(
    val trakt: Long? = null,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: Long? = null,
)
