package com.wizdier.wavestream.data.sync

import com.wizdier.wavestream.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimal Trakt sync client. Nuvio-style: pushes watch progress and
 * favourites up to Trakt so users can keep their history cross-device.
 *
 * Implements the device-code OAuth flow (https://trakt.tv/oauth) which is
 * the only flow Trakt allows for mobile apps. Tokens are persisted by the
 * caller via DataStore.
 */
class TraktSync(
    private val clientId: String,
    private val clientSecret: String
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val api = "https://api.trakt.tv"

    suspend fun beginDeviceCode(): DeviceCodeResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("client_id", clientId)
        }.toString().toRequestBody(JSON)
        val req = Request.Builder()
            .url("$api/oauth/device/code")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
        NetworkModule.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            json.decodeFromString(DeviceCodeResponse.serializer(), text)
        }
    }

    suspend fun pollForToken(deviceCode: String): TraktToken? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("code", deviceCode)
            put("client_id", clientId)
            put("client_secret", clientSecret)
            put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
        }.toString().toRequestBody(JSON)
        val req = Request.Builder().url("$api/oauth/device/token").post(body).build()
        NetworkModule.client.newCall(req).execute().use { resp ->
            if (resp.code == 400) return@withContext null // pending
            if (!resp.isSuccessful) return@withContext null
            val text = resp.body?.string().orEmpty()
            json.decodeFromString(TraktToken.serializer(), text)
        }
    }

    suspend fun scrobble(
        token: String,
        title: String,
        year: Int?,
        progress: Float, // 0..1
        type: String // "movie" or "episode"
    ): Boolean = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("progress", (progress * 100).coerceIn(0f, 100f))
            put(type, buildJsonObject {
                put("title", title)
                if (year != null) put("year", year)
            })
        }.toString().toRequestBody(JSON)
        val req = Request.Builder()
            .url("$api/scrobble/start")
            .post(body)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("trakt-api-version", "2")
            .addHeader("trakt-api-key", clientId)
            .build()
        NetworkModule.client.newCall(req).execute().isSuccessful
    }

    @Serializable
    data class DeviceCodeResponse(
        val device_code: String,
        val user_code: String,
        val verification_url: String,
        val expires_in: Int,
        val interval: Int
    )

    @Serializable
    data class TraktToken(
        val access_token: String,
        val refresh_token: String,
        val expires_in: Int,
        val created_at: Long
    )

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
