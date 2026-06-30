package com.wavestream.ui.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of loading links for a video. Contains the extracted stream
 * links (sorted by quality) and any subtitles discovered.
 *
 * The Player screen uses this to pick the best playable URL. On Android,
 * ExoPlayer handles M3U8, DASH, and direct video URLs natively.
 */
data class LoadedLinks(
    val links: List<ExtractorLink>,
    val subtitles: List<SubtitleFile>,
    val source: String,
) {
    /** Returns the best (highest-quality) link, or null if none. */
    val bestLink: ExtractorLink? get() = links.maxByOrNull { it.quality }

    /** Returns the best link URL, or empty string if none. */
    val bestUrl: String get() = bestLink?.url ?: ""
}

/**
 * Calls [com.lagradost.cloudstream3.MainAPI.loadLinks] on the provider
 * identified by [apiName], collecting all [ExtractorLink]s and
 * [SubtitleFile]s into a [LoadedLinks] result.
 *
 * This is the critical step between `load()` (which returns metadata +
 * a `dataUrl` parameter) and actual playback. The `dataUrl` is NOT a
 * direct video URL — it's passed to `loadLinks` which extracts the real
 * stream URL(s) by running site-specific scrapers / extractors.
 *
 * @param apiName The provider name (from SearchResponse.apiName or LoadResponse.apiName)
 * @param data The data string from LoadResponse (movie.dataUrl or Episode.data)
 * @return LoadedLinks with at least one link, or null if loading failed
 */
suspend fun loadVideoLinks(apiName: String, data: String): LoadedLinks? {
    if (data.isBlank()) return null
    return withContext(Dispatchers.Default) {
        try {
            val api = APIHolder.getApiFromNameNull(apiName)
                ?: APIHolder.getApiFromUrlNull(data)
            if (api == null) {
                println("[LinkLoader] No provider found for '$apiName'")
                return@withContext null
            }

            val links = mutableListOf<ExtractorLink>()
            val subtitles = mutableListOf<SubtitleFile>()

            val success = try {
                api.loadLinks(
                    data = data,
                    isCasting = false,
                    subtitleCallback = { sub ->
                        runCatching { subtitles.add(sub) }
                    },
                    callback = { link ->
                        runCatching { links.add(link) }
                    },
                )
            } catch (e: Throwable) {
                logError(e)
                false
            }

            if (links.isEmpty()) {
                println("[LinkLoader] loadLinks returned no links for '$apiName' (success=$success)")
                return@withContext null
            }

            // Sort by quality descending — quality is an int where higher = better.
            // Common values: 360=360p, 480=480p, 720=720p, 1080=1080p, 2160=4K.
            links.sortByDescending { it.quality }

            println("[LinkLoader] Loaded ${links.size} links + ${subtitles.size} subtitles from '$apiName'")
            LoadedLinks(
                links = links,
                subtitles = subtitles,
                source = apiName,
            )
        } catch (e: Throwable) {
            logError(e)
            null
        }
    }
}
