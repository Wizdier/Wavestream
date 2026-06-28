package com.wavestream.plugins.extractors

import com.wavestream.api.ExtractorApi
import com.wavestream.api.ExtractorLink
import com.wavestream.api.ExtractorLinkType
import com.wavestream.api.Qualities
import com.wavestream.api.newExtractorLink
import com.wavestream.core.network.app
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Voe extractor — mirrors CloudStream's Voe.kt.
 *
 * Voe uses a JavaScript-encoded redirect to hide the direct video URL.
 */
open class Voe : ExtractorApi() {
    override var name: String = "Voe"
    override var mainUrl: String = "https://voe.sx"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()

        // Look for the redirect URL in the script
        val redirectMatch = Regex("location\\.href\\s*=\\s*\"([^\"]+)\"").find(body)
            ?: Regex("window\\.location\\.replace\\(\"([^\"]+)\"\\)").find(body)
        if (redirectMatch != null) {
            val redirectUrl = redirectMatch.groupValues[1]
            if (redirectUrl != url) {
                return getUrl(redirectUrl, referer)
            }
        }

        // Look for direct video URL
        val videoUrl = Regex("https://[^\"']+\\.(?:mp4|m3u8)[^\"']*").find(body)?.value ?: return null
        val type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

        return listOf(
            newExtractorLink(source = name, name = name, url = videoUrl, type = type) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

class VoeLink : Voe() { override var mainUrl = "https://voe.link" }
class VoeUnblock : Voe() { override var mainUrl = "https://voeunblock.com" }
class VoeOnline : Voe() { override var mainUrl = "https://voe-online.com" }

/**
 * Filemoon extractor — mirrors CloudStream's Filemoon.kt.
 *
 * Filemoon uses an eval'd JS payload to construct the video URL.
 */
open class Filemoon : ExtractorApi() {
    override var name: String = "Filemoon"
    override var mainUrl: String = "https://filemoon.sx"
    override val requiresReferer: Boolean = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, headers = mapOf("Referer" to (referer ?: "")))
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()

        // Find the m3u8 source URL
        val m3u8Url = Regex("""sources:\s*\[\{file:\s*"([^"]+\.m3u8[^"]*)"\}""").find(body)?.groupValues?.get(1)
            ?: Regex("""https://[^"']+\.m3u8[^"']*""").find(body)?.value
            ?: return null

        return listOf(
            newExtractorLink(source = name, name = name, url = m3u8Url, type = ExtractorLinkType.M3U8) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

/**
 * JWPlayer extractor — generic player that's used by many sites.
 */
open class JWPlayer : ExtractorApi() {
    override var name: String = "JWPlayer"
    override var mainUrl: String = ""
    override val requiresReferer: Boolean = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, headers = mapOf("Referer" to (referer ?: "")))
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()

        val links = mutableListOf<ExtractorLink>()

        // Find all sources in the jwplayer setup
        val sourceRegex = Regex("""\{\s*file:\s*"(https?://[^"]+)"\s*(?:,\s*label:\s*"([^"]+)")?""")
        for (match in sourceRegex.findAll(body)) {
            val sourceUrl = match.groupValues[1]
            val label = match.groupValues.getOrNull(2)
            val quality = label?.let { parseQuality(it) } ?: Qualities.Unknown.value
            val type = if (sourceUrl.contains(".m3u8")) ExtractorLinkType.M3U8
                       else if (sourceUrl.contains(".mpd")) ExtractorLinkType.DASH
                       else ExtractorLinkType.VIDEO

            links.add(newExtractorLink(source = name, name = label ?: name, url = sourceUrl, type = type) {
                this.referer = url
                this.quality = quality
            })
        }

        return if (links.isEmpty()) null else links
    }

    private fun parseQuality(label: String): Int {
        // Labels like "1080p", "720p", "HD", "4K"
        return when {
            label.contains("4k", ignoreCase = true) -> Qualities.P2160.value
            label.contains("1080", ignoreCase = true) -> Qualities.P1080.value
            label.contains("720", ignoreCase = true) -> Qualities.P720.value
            label.contains("480", ignoreCase = true) -> Qualities.P480.value
            label.contains("360", ignoreCase = true) -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}

/**
 * Upstream extractor — mirrors CloudStream's UpstreamExtractor.kt.
 */
open class Upstream : ExtractorApi() {
    override var name: String = "Upstream"
    override var mainUrl: String = "https://upstream.to"
    override val requiresReferer: Boolean = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, headers = mapOf("Referer" to (referer ?: "")))
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()

        // Sources are in JavaScript: sources:[{file:"...",label:"1080p"}]
        val links = mutableListOf<ExtractorLink>()
        val regex = Regex("""\{\s*file:\s*"(https://[^"]+)"\s*,\s*label:\s*"([^"]+)"\s*\}""")
        for (match in regex.findAll(body)) {
            val sourceUrl = match.groupValues[1]
            val label = match.groupValues[2]
            val quality = when {
                label.contains("1080") -> Qualities.P1080.value
                label.contains("720") -> Qualities.P720.value
                label.contains("480") -> Qualities.P480.value
                label.contains("360") -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }
            links.add(newExtractorLink(source = name, name = "$name - $label", url = sourceUrl, type = ExtractorLinkType.VIDEO) {
                this.referer = url
                this.quality = quality
            })
        }
        return if (links.isEmpty()) null else links
    }
}

/**
 * Doodstream variants
 */
class DoodstreamCom : Doodstream() { override var mainUrl = "https://doodstream.com" }
class DoodstreamWs : Doodstream() { override var mainUrl = "https://dood.ws" }
class DoodstreamSo : Doodstream() { override var mainUrl = "https://dood.so" }
class DoodstreamPm : Doodstream() { override var mainUrl = "https://dood.pm" }

/**
 * Sendvid extractor
 */
open class Sendvid : ExtractorApi() {
    override var name: String = "Sendvid"
    override var mainUrl: String = "https://sendvid.com"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()
        val videoUrl = Regex("""https://[^"']+\.mp4[^"']*""").find(body)?.value ?: return null
        return listOf(
            newExtractorLink(source = name, name = name, url = videoUrl, type = ExtractorLinkType.VIDEO) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

/**
 * Mp4Upload extractor
 */
open class Mp4Upload : ExtractorApi() {
    override var name: String = "Mp4Upload"
    override var mainUrl: String = "https://mp4upload.com"
    override val requiresReferer: Boolean = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, headers = mapOf("Referer" to (referer ?: "")))
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()
        val videoUrl = Regex("""player\.src\(\s*"([^"]+\.mp4[^"]*)"\s*\)""").find(body)?.groupValues?.get(1)
            ?: Regex("""https://[^"']+\.mp4[^"']*""").find(body)?.value
            ?: return null
        return listOf(
            newExtractorLink(source = name, name = name, url = videoUrl, type = ExtractorLinkType.VIDEO) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

/**
 * Vidmo extractor
 */
open class Vidmo : ExtractorApi() {
    override var name: String = "Vidmo"
    override var mainUrl: String = "https://vidmo.org"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()
        val videoUrl = Regex("""https://[^"']+\.mp4[^"']*""").find(body)?.value ?: return null
        return listOf(
            newExtractorLink(source = name, name = name, url = videoUrl, type = ExtractorLinkType.VIDEO) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

/**
 * Vidoza extractor
 */
open class Vidoza : ExtractorApi() {
    override var name: String = "Vidoza"
    override var mainUrl: String = "https://vidoza.net"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()
        val links = mutableListOf<ExtractorLink>()
        val regex = Regex("""src:\s*"(https://[^"]+\.mp4[^"]*)"\s*,\s*res:\s*(\d+)""")
        for (match in regex.findAll(body)) {
            val sourceUrl = match.groupValues[1]
            val res = match.groupValues[2].toIntOrNull() ?: 0
            val quality = when {
                res >= 2160 -> Qualities.P2160.value
                res >= 1080 -> Qualities.P1080.value
                res >= 720 -> Qualities.P720.value
                res >= 480 -> Qualities.P480.value
                res >= 360 -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }
            links.add(newExtractorLink(source = name, name = "$name ${res}p", url = sourceUrl, type = ExtractorLinkType.VIDEO) {
                this.referer = url
                this.quality = quality
            })
        }
        return if (links.isEmpty()) null else links
    }
}
