package com.wavestream.features.player.skip

import com.wavestream.core.network.app
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Skip timestamp — a time range during which the user can skip a portion of the video.
 *
 * Used for anime openings, endings, recaps, and previews.
 */
@Serializable
data class SkipStamp(
    val start: Double,        // start time in seconds
    val end: Double,          // end time in seconds
    val skipType: String,     // "op", "ed", "recap", "preview", "mixed-skips"
    val skipName: String? = null,
    val episodeLength: Double? = null,
)

/**
 * AniSkip API — https://api.aniskip.com
 *
 * Returns skip timestamps for anime episodes based on MAL ID.
 */
object AniSkipApi {
    private const val BASE_URL = "https://api.aniskip.com/v2/skip-times"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSkipTimes(malId: Int, episodeNumber: Int, episodeLength: Int? = null): List<SkipStamp> {
        return try {
            val url = "$BASE_URL/$malId/$episodeNumber?types[]=op&types[]=ed&types[]=recap&types[]=preview&types[]=mixed-skips"
            val response = app.get(url, headers = mapOf("Accept" to "application/json"))
            if (!response.status.isSuccess()) return emptyList()

            val body = response.bodyAsText()
            val parsed = json.decodeFromString<AniSkipResponse>(body)
            parsed.result?.filter { stamp ->
                stamp.start < stamp.end && (episodeLength == null || stamp.end <= episodeLength)
            } ?: emptyList()
        } catch (e: Throwable) {
            emptyList()
        }
    }
}

@Serializable
private data class AniSkipResponse(
    val found: Boolean? = false,
    val result: List<SkipStamp>? = null,
    val message: String? = null,
)

/**
 * IntroDB API — alternative skip timestamp source.
 */
object IntroDbApi {
    private const val BASE_URL = "https://introdb.com/api/v1"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSkipTimes(imdbId: String, season: Int?, episode: Int?): List<SkipStamp> {
        return try {
            val episodePart = if (season != null && episode != null) "?season=$season&episode=$episode" else ""
            val url = "$BASE_URL/skip-times/$imdbId$episodePart"
            val response = app.get(url)
            if (!response.status.isSuccess()) return emptyList()
            val body = response.bodyAsText()
            json.decodeFromString<List<SkipStamp>>(body)
        } catch (e: Throwable) {
            emptyList()
        }
    }
}

/**
 * Combined skip timestamp resolver — tries AniSkip first, then IntroDB.
 */
object SkipTimestampResolver {
    suspend fun getSkipTimes(
        malId: Int? = null,
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        episodeLength: Int? = null,
    ): List<SkipStamp> {
        val stamps = mutableListOf<SkipStamp>()

        if (malId != null && episode != null) {
            stamps.addAll(AniSkipApi.getSkipTimes(malId, episode, episodeLength))
        }

        if (imdbId != null && stamps.isEmpty()) {
            stamps.addAll(IntroDbApi.getSkipTimes(imdbId, season, episode))
        }

        return stamps.distinctBy { it.skipType }
    }
}
