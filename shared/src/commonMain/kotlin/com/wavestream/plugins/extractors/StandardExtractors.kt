package com.wavestream.plugins.extractors

import com.wavestream.api.ExtractorApi
import com.wavestream.api.ExtractorLink
import com.wavestream.api.ExtractorLinkType
import com.wavestream.api.Qualities
import com.wavestream.api.SubtitleFile
import com.wavestream.api.newExtractorLink
import com.wavestream.core.network.app
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.mozilla.javascript.Context

/**
 * StreamTape extractor — mirrors CloudStream's StreamTape.kt (66 lines).
 *
 * Fetches the StreamTape page, finds the `botlink').innerHTML` script line,
 * extracts the JS, runs it through Mozilla Rhino to compute the video URL,
 * returns an ExtractorLink with the resolved URL.
 */
open class StreamTape : ExtractorApi() {
    override var name: String = "StreamTape"
    override var mainUrl: String = "https://streamtape.com"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        if (!response.status.isSuccess()) return null

        val docText = response.bodyAsText()

        // Find the botlink script
        val scriptContent = docText.lines().firstOrNull { it.contains("botlink').innerHTML") }
            ?.let { line ->
                line.substringAfter(").innerHTML").replaceFirst("=", "var url =")
            } ?: return null

        // Run through Rhino to compute the URL
        val cx = Context.enter()
        cx.optimizationLevel = -1
        try {
            val scope = cx.initSafeStandardObjects()
            cx.evaluateString(scope, scriptContent, "url", 1, null)
            val result = scope.get("url", scope)?.toString() ?: return null

            if (result.isNotBlank()) {
                val extractedUrl = "https:$result&stream=1"
                return listOf(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = extractedUrl,
                        type = ExtractorLinkType.VIDEO,
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } finally {
            try { Context.exit() } catch (_: Throwable) {}
        }
        return null
    }
}

class StreamTapeNet : StreamTape() {
    override var mainUrl = "https://streamtape.net"
}

class StreamTapeXyz : StreamTape() {
    override var mainUrl = "https://streamtape.xyz"
}

/**
 * MixDrop extractor — simplified version.
 */
open class MixDrop : ExtractorApi() {
    override var name: String = "MixDrop"
    override var mainUrl: String = "https://mixdrop.co"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        if (!response.status.isSuccess()) return null

        val bodyText = response.bodyAsText()
        val videoUrl = Regex("""https://[^"']+\.mp4[^"']*""").find(bodyText)?.value
            ?: return null

        return listOf(
            newExtractorLink(
                source = name,
                name = name,
                url = videoUrl,
                type = ExtractorLinkType.VIDEO,
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

/**
 * Doodstream extractor — simplified.
 */
open class Doodstream : ExtractorApi() {
    override var name: String = "Doodstream"
    override var mainUrl: String = "https://doodstream.com"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        if (!response.status.isSuccess()) return null

        val bodyText = response.bodyAsText()
        val passMd5 = Regex("""/pass_md5/[^"']+""").find(bodyText)?.value
            ?: return null

        val tokenUrl = "$mainUrl$passMd5"
        val tokenResp = app.get(tokenUrl, headers = mapOf("Referer" to url))
        if (!tokenResp.status.isSuccess()) return null

        val md5 = tokenResp.bodyAsText()
        val expires = Regex("""expires=([^"']+)""").find(bodyText)?.groupValues?.get(1) ?: ""
        val token = Regex("""token=([^"']+)""").find(bodyText)?.groupValues?.get(1) ?: ""

        val finalUrl = "$md5$expires?token=$token"
        return listOf(
            newExtractorLink(
                source = name,
                name = name,
                url = finalUrl,
                type = ExtractorLinkType.VIDEO,
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

/**
 * M3U8 Manifest extractor — passes through .m3u8 URLs as-is.
 */
class M3u8Manifest : ExtractorApi() {
    override val name: String = "M3U8 Manifest"
    override val mainUrl: String = ""
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        return listOf(
            newExtractorLink(
                source = name,
                name = "M3U8",
                url = url,
                type = ExtractorLinkType.M3U8,
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

