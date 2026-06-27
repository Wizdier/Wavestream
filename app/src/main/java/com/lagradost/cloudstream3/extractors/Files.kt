@file:Suppress("UNUSED", "unused")

package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.ExtractorType
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi

/**
 * Common direct-link extractors that real CloudStream 3 plugins reference.
 * Each subclass knows how to fetch a specific host's embed page and pull
 * out the direct video URL.
 *
 * These are intentionally simple — most just fetch the page HTML and use
 * a regex to find the video URL. Hosts that require JavaScript evaluation
 * (DoodStream, Filemoon with obfuscation) need [com.lagradost.cloudstream3.utils.WebViewExtractorApi]
 * and are stubbed here — the actual WebView-based extraction requires a
 * Context and is set up lazily by the plugin loader.
 */

// ─────────────────────────────────────────────────────────────────────────────
//  MixDrop
// ─────────────────────────────────────────────────────────────────────────────

open class MixDrop : ExtractorApi() {
    override val name = "MixDrop"
    override val mainUrl = "https://mixdrop.co"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val resp = app.get(url, headers = mapOf("User-Agent" to USER_AGENT))
        val doc = resp.text
        resp.close()
        // MixDrop stores the video URL in a JS variable like:
        //   var vdsr = "https://...mp4";
        val videoUrl = Regex("""vdsr\s*=\s*["']([^"']+)["']""").find(doc)?.groupValues?.get(1)
            ?: Regex("""(https?://[^"'<>\s]+\.mp4[^"'<>\s]*)""").find(doc)?.value
            ?: return null
        return listOf(
            ExtractorLink(
                source = name,
                name = "MixDrop",
                url = videoUrl,
                referer = url,
                quality = ExtractorLink.QUALITY_UNKNOWN,
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url)
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DoodStream (and its many domain aliases)
// ─────────────────────────────────────────────────────────────────────────────

abstract class DoodBaseExtractor : ExtractorApi() {
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // DoodStream requires fetching the page to get a token, then
        // constructing the video URL. The token is in a <fsvid> element.
        val resp = app.get(url, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to (referer ?: "")))
        val doc = resp.text
        resp.close()
        val md5Hash = Regex("""/pass_md5/([^/]+)/([^"]+)""").find(doc)?.value ?: return null
        val fullUrl = "https://${mainUrl.substringAfter("//")}$md5Hash"
        val tokenResp = app.get(fullUrl, headers = mapOf("Referer" to url, "User-Agent" to USER_AGENT))
        val token = tokenResp.text.trim()
        tokenResp.close()
        // Random 10-char string for the URL
        val random = (1..10).map { ('a'..'z').random() }.joinToString("")
        val videoUrl = "$token$random"
        return listOf(
            ExtractorLink(
                source = name,
                name = "DoodStream",
                url = videoUrl,
                referer = url,
                quality = ExtractorLink.QUALITY_UNKNOWN,
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url)
            )
        )
    }
}

class DoodLaExtractor : DoodBaseExtractor() {
    override val name = "Dood.la"
    override val mainUrl = "https://dood.la"
}
class DoodSoExtractor : DoodBaseExtractor() {
    override val name = "Dood.so"
    override val mainUrl = "https://dood.so"
}
class DoodToExtractor : DoodBaseExtractor() {
    override val name = "Dood.to"
    override val mainUrl = "https://dood.to"
}
class DoodWatchExtractor : DoodBaseExtractor() {
    override val name = "Dood.watch"
    override val mainUrl = "https://dood.watch"
}
class DoodPmExtractor : DoodBaseExtractor() {
    override val name = "Dood.pm"
    override val mainUrl = "https://dood.pm"
}
class DoodWsExtractor : DoodBaseExtractor() {
    override val name = "Dood.ws"
    override val mainUrl = "https://dood.ws"
}
class DoodShExtractor : DoodBaseExtractor() {
    override val name = "Dood.sh"
    override val mainUrl = "https://dood.sh"
}
class DoodWfExtractor : DoodBaseExtractor() {
    override val name = "Dood.wf"
    override val mainUrl = "https://dood.wf"
}
class DoodYtExtractor : DoodBaseExtractor() {
    override val name = "Dood.yt"
    override val mainUrl = "https://dood.yt"
}
class DoodCxExtractor : DoodBaseExtractor() {
    override val name = "Dood.cx"
    override val mainUrl = "https://dood.cx"
}

// ─────────────────────────────────────────────────────────────────────────────
//  Filemoon (stub — requires JS evaluation)
// ─────────────────────────────────────────────────────────────────────────────

open class FileMoon : ExtractorApi() {
    override val name = "FileMoon"
    override val mainUrl = "https://filemoon.sx"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // Filemoon uses JS obfuscation. Without a WebView, we try a basic
        // regex pass — if it fails, the provider should fall back to its
        // own extraction logic.
        val resp = app.get(url, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to (referer ?: "")))
        val doc = resp.text
        resp.close()
        // Look for sources: [{"file":"https://...mp4"}]
        val sourcesRegex = Regex("""sources:\s*\[\{"file":"([^"]+)"""")
        val videoUrl = sourcesRegex.find(doc)?.groupValues?.get(1) ?: return null
        return listOf(
            ExtractorLink(
                source = name,
                name = "FileMoon",
                url = videoUrl,
                referer = url,
                quality = ExtractorLink.QUALITY_UNKNOWN,
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url)
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Streamtape
// ─────────────────────────────────────────────────────────────────────────────

open class Streamtape : ExtractorApi() {
    override val name = "Streamtape"
    override val mainUrl = "https://streamtape.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val resp = app.get(url, headers = mapOf("User-Agent" to USER_AGENT))
        val doc = resp.text
        resp.close()
        // Streamtape uses an obfuscated JS variable for the video URL:
        //   document.getElementById('robotlink').innerHTML = '...' + 'XYZ';
        val token = Regex("""robotlink.*?\.innerHTML\s*=\s*'([^']+)'\s*\+\s*'([^']+)'""").find(doc)
            ?: return null
        val videoUrl = "https://" + mainUrl.substringAfter("//") + token.groupValues[1] + token.groupValues[2]
        return listOf(
            ExtractorLink(
                source = name,
                name = "Streamtape",
                url = videoUrl,
                referer = url,
                quality = ExtractorLink.QUALITY_UNKNOWN,
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url)
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mp4Upload
// ─────────────────────────────────────────────────────────────────────────────

open class Mp4Upload : ExtractorApi() {
    override val name = "Mp4Upload"
    override val mainUrl = "https://mp4upload.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val resp = app.get(url, headers = mapOf("User-Agent" to USER_AGENT))
        val doc = resp.text
        resp.close()
        val videoUrl = Regex("""player\.src\s*=\s*["']([^"']+\.mp4[^"']*)["']""").find(doc)?.groupValues?.get(1)
            ?: Regex("""(https?://[^"'<>\s]+mp4upload[^"'<>\s]+\.mp4[^"'<>\s]*)""").find(doc)?.value
            ?: return null
        return listOf(
            ExtractorLink(
                source = name,
                name = "Mp4Upload",
                url = videoUrl,
                referer = url,
                quality = ExtractorLink.QUALITY_UNKNOWN,
                headers = mapOf("User-Agent" to USER_AGENT)
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  StreamWish / Streamwish
// ─────────────────────────────────────────────────────────────────────────────

open class StreamWish : ExtractorApi() {
    override val name = "StreamWish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val resp = app.get(url, headers = mapOf("User-Agent" to USER_AGENT))
        val doc = resp.text
        resp.close()
        val sourcesRegex = Regex("""sources:\s*\[\{"file":"([^"]+)"""")
        val videoUrl = sourcesRegex.find(doc)?.groupValues?.get(1) ?: return null
        return listOf(
            ExtractorLink(
                source = name,
                name = "StreamWish",
                url = videoUrl,
                referer = url,
                quality = ExtractorLink.QUALITY_UNKNOWN,
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url)
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  FEmbed / Fembed
// ─────────────────────────────────────────────────────────────────────────────

open class FEmbed : ExtractorApi() {
    override val name = "FEmbed"
    override val mainUrl = "https://fembed9hd.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        // FEmbed uses a POST API to get video sources
        val apiRegex = Regex("""/api/source/([^/]+)""").find(url) ?: return null
        val apiId = apiRegex.groupValues[1]
        val apiUrl = "$mainUrl/api/source/$apiId"
        val resp = app.post(apiUrl, headers = mapOf("User-Agent" to USER_AGENT, "Content-Type" to "application/x-www-form-urlencoded"))
        val body = resp.text
        resp.close()
        // Response is JSON like {"data":[{"file":"...","label":"720p"}]}
        val linksRegex = Regex(""""file":"([^"]+)","label":"([^"]+)"""")
        val links = linksRegex.findAll(body).map { mr ->
            val (file, label) = mr.destructured
            ExtractorLink(
                source = name,
                name = "FEmbed ($label)",
                url = file,
                referer = url,
                quality = when (label.lowercase()) {
                    "360p" -> ExtractorLink.QUALITY_360
                    "480p" -> ExtractorLink.QUALITY_480
                    "720p" -> ExtractorLink.QUALITY_720
                    "1080p" -> ExtractorLink.QUALITY_1080
                    "4k", "2160p" -> ExtractorLink.QUALITY_2160
                    else -> ExtractorLink.QUALITY_UNKNOWN
                },
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url)
            )
        }.toList()
        return if (links.isEmpty()) null else links
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Upstream (stub)
// ─────────────────────────────────────────────────────────────────────────────

open class Upstream : ExtractorApi() {
    override val name = "Upstream"
    override val mainUrl = "https://upstream.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val resp = app.get(url, headers = mapOf("User-Agent" to USER_AGENT))
        val doc = resp.text
        resp.close()
        val sourcesRegex = Regex("""sources:\s*\[\{"file":"([^"]+)","label":"([^"]+)"""")
        val links = sourcesRegex.findAll(doc).map { mr ->
            val (file, label) = mr.destructured
            ExtractorLink(
                source = name,
                name = "Upstream ($label)",
                url = file,
                referer = url,
                quality = when (label.lowercase()) {
                    "360p" -> ExtractorLink.QUALITY_360
                    "480p" -> ExtractorLink.QUALITY_480
                    "720p" -> ExtractorLink.QUALITY_720
                    "1080p" -> ExtractorLink.QUALITY_1080
                    "4k", "2160p" -> ExtractorLink.QUALITY_2160
                    else -> ExtractorLink.QUALITY_UNKNOWN
                },
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url)
            )
        }.toList()
        return if (links.isEmpty()) null else links
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Register all built-in extractors on first class load.
//  Plugins can register their own extractors at runtime via
//  [com.lagradost.cloudstream3.plugins.Plugin.registerExtractorAPI].
// ─────────────────────────────────────────────────────────────────────────────

fun registerBuiltinExtractors() {
    val apis = listOf(
        MixDrop(),
        DoodLaExtractor(), DoodSoExtractor(), DoodToExtractor(), DoodWatchExtractor(),
        DoodPmExtractor(), DoodWsExtractor(), DoodShExtractor(), DoodWfExtractor(),
        DoodYtExtractor(), DoodCxExtractor(),
        FileMoon(),
        Streamtape(),
        Mp4Upload(),
        StreamWish(),
        FEmbed(),
        Upstream()
    )
    com.lagradost.cloudstream3.utils.extractorApis.addAll(apis)
}
