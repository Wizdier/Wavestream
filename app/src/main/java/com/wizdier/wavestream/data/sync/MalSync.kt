package com.wizdier.wavestream.data.sync

import com.wizdier.wavestream.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimal MyAnimeList sync client. Nuvio's anime-first search experience
 * pairs naturally with MAL's library API — pushes watch state up so users
 * keep their anime progress across devices.
 *
 * Implements MAL's PKCE OAuth flow (https://myanimelist.net/apiconfig).
 */
class MalSync(
    private val clientId: String
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val api = "https://api.myanimelist.net/v2"
    private val auth = "https://myanimelist.net/v1/oauth2"

    /** Build the URL the user should open in a browser. */
    fun authorizeUrl(codeChallenge: String, redirectUri: String, state: String): String {
        return "$auth/authorize?response_type=code" +
            "&client_id=$clientId" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=plain" +
            "&state=$state" +
            "&redirect_uri=$redirectUri"
    }

    suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): MalToken? = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", redirectUri)
            .build()
        val req = Request.Builder().url("$auth/token").post(form).build()
        NetworkModule.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val text = resp.body?.string().orEmpty()
            json.decodeFromString(MalToken.serializer(), text)
        }
    }

    suspend fun updateAnimeProgress(
        token: String,
        animeId: Int,
        episode: Int,
        status: String = "watching"
    ): Boolean = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("status", status)
            if (status == "watching") put("num_watched_episodes", episode)
        }.toString().toRequestBody(JSON)
        val req = Request.Builder()
            .url("$api/anime/$animeId/my_list_status")
            .put(body)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()
        NetworkModule.client.newCall(req).execute().isSuccessful
    }

    @Serializable
    data class MalToken(
        val token_type: String,
        val expires_in: Int,
        val access_token: String,
        val refresh_token: String
    )

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
