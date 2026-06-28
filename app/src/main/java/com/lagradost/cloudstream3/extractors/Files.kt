@file:Suppress("UNUSED", "unused")
package com.lagradost.cloudstream3.extractors
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
open class MixDrop : ExtractorApi() {
    override val name = "MixDrop"; override val mainUrl = "https://mixdrop.co"; override val requiresReferer = false
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).text
        val videoUrl = Regex("""vdsr\s*=\s*["']([^"']+)["']""").find(doc)?.groupValues?.get(1) ?: return null
        return listOf(ExtractorLink(source = name, name = "MixDrop", url = videoUrl, referer = url, quality = ExtractorLink.QUALITY_UNKNOWN, headers = mapOf("User-Agent" to USER_AGENT)))
    }
}
open class DoodStream : ExtractorApi() {
    override val name = "DoodStream"; override val mainUrl = "https://dood.to"; override val requiresReferer = true
}
open class FileMoon : ExtractorApi() {
    override val name = "FileMoon"; override val mainUrl = "https://filemoon.sx"; override val requiresReferer = true
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to (referer ?: ""))).text
        val videoUrl = Regex("""sources:\s*\[\{"file":"([^"]+)"""").find(doc)?.groupValues?.get(1) ?: return null
        return listOf(ExtractorLink(source = name, name = "FileMoon", url = videoUrl, referer = url, quality = ExtractorLink.QUALITY_UNKNOWN, headers = mapOf("User-Agent" to USER_AGENT)))
    }
}
open class Streamtape : ExtractorApi() {
    override val name = "Streamtape"; override val mainUrl = "https://streamtape.com"; override val requiresReferer = false
}
open class Mp4Upload : ExtractorApi() {
    override val name = "Mp4Upload"; override val mainUrl = "https://mp4upload.com"; override val requiresReferer = false
}
open class StreamWish : ExtractorApi() {
    override val name = "StreamWish"; override val mainUrl = "https://streamwish.to"; override val requiresReferer = false
}
fun registerBuiltinExtractors() {
    com.lagradost.cloudstream3.utils.extractorApis.addAll(listOf(MixDrop(), DoodStream(), FileMoon(), Streamtape(), Mp4Upload(), StreamWish()))
}
