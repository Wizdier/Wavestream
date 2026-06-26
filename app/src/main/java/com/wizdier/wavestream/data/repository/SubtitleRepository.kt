package com.wizdier.wavestream.data.repository

import com.wizdier.wavestream.data.api.SubtitleFile
import com.wizdier.wavestream.data.api.SubtitleFormat
import com.wizdier.wavestream.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Subtitle download / cache helper. CloudStream-compatible: providers can
 * return subtitle URLs alongside the video, and the player asks this
 * repository to materialize them as local files (ExoPlayer needs a URI for
 * side-loaded subtitles).
 *
 * Files are cached under [cacheDir] using a stable hash of the URL so
 * repeated playbacks don't re-download.
 */
class SubtitleRepository(private val cacheDir: File) {

    suspend fun fetch(subtitle: SubtitleFile): File = withContext(Dispatchers.IO) {
        val ext = when (subtitle.format) {
            SubtitleFormat.VTT -> "vtt"
            SubtitleFormat.SRT -> "srt"
            SubtitleFormat.ASS -> "ass"
        }
        // Stable cache key — same URL+format always reuses the same file.
        val target = File(cacheDir, "subs_${subtitle.url.hashCode()}_${subtitle.lang.hashCode()}.$ext")
        if (target.exists() && target.length() > 0) return@withContext target

        val req = Request.Builder().url(subtitle.url).build()
        NetworkModule.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Subtitle fetch failed: ${resp.code}")
            val body = resp.body ?: throw IOException("Empty subtitle response")
            target.outputStream().use { sink -> body.byteStream().copyTo(sink) }
        }
        target
    }

    suspend fun fetchAll(subtitles: List<SubtitleFile>): List<Pair<SubtitleFile, File>> =
        subtitles.mapNotNull { sub ->
            runCatching { sub to fetch(sub) }.getOrNull()
        }
}
