package com.wavestream.plugins.m3u8

import com.wavestream.api.ExtractorLink
import com.wavestream.api.ExtractorLinkType
import com.wavestream.api.Qualities
import com.wavestream.api.newExtractorLink
import com.wavestream.core.network.app
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * M3U8 helper — mirrors CloudStream's `M3u8Helper2` (358 lines).
 *
 * Parses an HLS master playlist, returns the list of variant streams with their
 * resolutions/qualities. Handles encrypted segments (AES-128-CBC) for download.
 *
 * Usage:
 *   val streams = M3u8Helper.generateM3u8("Source", url, referer, quality, headers)
 *   // streams is List<ExtractorLink> — one per variant
 */
object M3u8Helper {

    data class M3u8Stream(
        val streamUrl: String,
        val quality: Int? = null,
        val headers: Map<String, String> = mapOf(),
    )

    /**
     * Fetch and parse an m3u8 playlist, returning one ExtractorLink per variant.
     */
    suspend fun generateM3u8(
        source: String,
        streamUrl: String,
        referer: String,
        quality: Int? = null,
        headers: Map<String, String> = emptyMap(),
        name: String = source,
    ): List<ExtractorLink> {
        return m3u8Generation(M3u8Stream(streamUrl, quality, headers)).map { stream ->
            newExtractorLink(
                source = source,
                name = name,
                url = stream.streamUrl,
                type = ExtractorLinkType.M3U8,
            ) {
                this.referer = referer
                this.quality = stream.quality ?: Qualities.Unknown.value
                this.headers = stream.headers
            }
        }
    }

    /**
     * Fetch and parse an m3u8 playlist, returning the list of variant streams.
     *
     * If the playlist is a master playlist (contains #EXT-X-STREAM-INF), returns
     * the variant streams. If it's a media playlist (contains .ts segments), returns
     * the original stream as-is.
     */
    suspend fun m3u8Generation(m3u8: M3u8Stream, returnThis: Boolean = true): List<M3u8Stream> {
        val list = mutableListOf<M3u8Stream>()
        val response = app.get(m3u8.streamUrl, headers = m3u8.headers)
        if (!response.status.isSuccess()) return if (returnThis) listOf(m3u8) else emptyList()

        val body = response.bodyAsText()
        val lines = body.lines().map { it.trim() }

        var anyFound = false
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue

            val nextLine = lines.getOrNull(i + 1) ?: continue
            if (nextLine.startsWith("#")) continue

            // Parse resolution from the STREAM-INF line
            val resolution = Regex("RESOLUTION=\\d+x(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
            val quality = resolution ?: qualityFromString(line)

            val variantUrl = absolutizeUrl(m3u8.streamUrl, nextLine)
            list.add(M3u8Stream(variantUrl, quality, m3u8.headers))
            anyFound = true
        }

        // If no variants found, treat the URL itself as a playable stream
        if (!anyFound || returnThis) {
            // Check if it looks like a media playlist (has .ts segments)
            if (body.contains(".ts") || body.contains("#EXTINF") || anyFound) {
                if (returnThis) list.add(m3u8)
            }
        }

        return list
    }

    /**
     * Quality guess from codec/BANDWIDTH in STREAM-INF.
     */
    private fun qualityFromString(line: String): Int? {
        val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
        // Very rough mapping: bandwidth in bits/sec → quality in pixels height
        return when {
            bandwidth == null -> null
            bandwidth > 8_000_000 -> Qualities.P2160.value
            bandwidth > 4_000_000 -> Qualities.P1080.value
            bandwidth > 2_000_000 -> Qualities.P720.value
            bandwidth > 800_000 -> Qualities.P480.value
            else -> Qualities.P360.value
        }
    }

    /**
     * Convert a relative URL to absolute, against a base URL.
     */
    private fun absolutizeUrl(baseUrl: String, maybeRelative: String): String {
        if (maybeRelative.startsWith("http://") || maybeRelative.startsWith("https://")) {
            return maybeRelative
        }
        if (maybeRelative.startsWith("//")) {
            return "https:$maybeRelative"
        }
        if (maybeRelative.startsWith("/")) {
            val scheme = baseUrl.substringBefore("://", "https")
            val host = baseUrl.substringAfter("://", "").substringBefore("/")
            return "$scheme://$host$maybeRelative"
        }
        val baseDir = baseUrl.substringBeforeLast("/", missingDelimiterValue = baseUrl)
        return "$baseDir/$maybeRelative"
    }

    /**
     * Decrypt an AES-128-CBC encrypted HLS segment.
     * Used when downloading HLS playlists with encrypted segments.
     */
    fun decryptSegment(
        encrypted: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(encrypted)
    }

    /**
     * Default IV for HLS segments (segment index + 1 as 16-byte big-endian).
     */
    fun defaultIv(index: Int): ByteArray = ByteArray(16) { i ->
        val shift = (15 - i) * 8
        ((index + 1) shr shift and 0xFF).toByte()
    }

    /**
     * Parse #EXT-X-KEY:METHOD=AES-128,URI="...",IV=0x... from a playlist.
     */
    data class HlsKey(
        val method: String,
        val uri: String?,
        val iv: ByteArray?,
    )

    fun parseKey(line: String): HlsKey? {
        if (!line.startsWith("#EXT-X-KEY:")) return null
        val method = Regex("METHOD=([^,]+)").find(line)?.groupValues?.get(1) ?: return null
        val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
        val iv = Regex("IV=0x([0-9a-fA-F]+)").find(line)?.groupValues?.get(1)?.let { hex ->
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        return HlsKey(method, uri, iv)
    }
}
