@file:Suppress("UNUSED", "unused", "MemberVisibilityCanBePrivate")
package com.lagradost.cloudstream3.utils
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mvvm.logError
abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean
    var sourcePlugin: String? = null
    @Throws
    open suspend fun getUrl(url: String, referer: String? = null, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        getUrl(url, referer)?.forEach(callback)
    }
    suspend fun getSafeUrl(url: String, referer: String? = null, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try { getUrl(url, referer, subtitleCallback, callback) } catch (e: Exception) { logError(e) }
    }
    @Throws
    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? = emptyList()
    open fun getExtractorUrl(id: String): String = id
}
val extractorApis: MutableList<ExtractorApi> = java.util.concurrent.CopyOnWriteArrayList()
suspend fun runExtractors(url: String, referer: String? = null, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
    for (api in extractorApis) {
        if (!url.startsWith(api.mainUrl, ignoreCase = true)) continue
        try { api.getSafeUrl(url, referer, subtitleCallback, callback); return true } catch (e: Exception) { logError(e) }
    }
    return false
}
