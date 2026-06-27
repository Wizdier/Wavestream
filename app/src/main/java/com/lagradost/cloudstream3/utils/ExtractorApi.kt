@file:Suppress("UNUSED", "unused", "MemberVisibilityCanBePrivate")

package com.lagradost.cloudstream3.utils

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * CloudStream 3's ExtractorApi — base class for iframe/embed extractors.
 * Real CS3 ships ~100 of these (MixDrop, DoodStream, Filemoon, Streamtape,
 * Mp4Upload, Vivo, Upstream, etc.). Each subclass knows how to fetch a
 * specific host's embed page and pull out the direct video URL.
 *
 * WaveStream's shim accepts registrations but only direct-link extractors
 * are fully functional. WebView-based extractors work via [WebViewExtractorApi].
 */
abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    /**
     * New-style getUrl with subtitle + link callbacks. Override this in
     * subclasses to extract both video links AND subtitles.
     */
    @Throws
    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        getUrl(url, referer)?.forEach(callback)
    }

    /**
     * Safe wrapper that swallows exceptions and logs them.
     */
    suspend fun getSafeUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try { getUrl(url, referer, subtitleCallback, callback) } catch (e: Exception) { logError(e) }
    }

    /**
     * Old-style getUrl that returns a list. Override this if your extractor
     * only returns links (no subtitles).
     */
    @Throws
    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? = emptyList()

    /**
     * Convert a partial URL to a full URL on this extractor's mainUrl.
     */
    open fun getExtractorUrl(id: String): String {
        return when {
            id.startsWith("http") -> id
            id.startsWith("/") -> "$mainUrl$id"
            else -> "$mainUrl/$id"
        }
    }
}

/**
 * Base class for extractors that need to evaluate JavaScript in a WebView
 * to extract the video URL. Subclasses override [getUrl] and use [evalJs].
 *
 * Many streaming hosts (DoodStream, Filemoon, MixDrop variants) obfuscate
 * the video URL with JS that needs to actually run to reveal the source.
 */
abstract class WebViewExtractorApi(protected val context: Context) : ExtractorApi() {

    /**
     * Load [url] in a headless WebView, wait for the page to finish loading,
     * then evaluate [jsExpression] and return the result.
     *
     * Times out after [timeoutMs] (default 15s) and returns null.
     */
    suspend fun evalJs(
        url: String,
        jsExpression: String,
        timeoutMs: Long = 15_000,
        extraHeaders: Map<String, String> = emptyMap()
    ): String? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = USER_AGENT
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript(jsExpression) { result ->
                                if (cont.isActive) cont.resume(result)
                            }
                        }
                    }
                }
                if (extraHeaders.isNotEmpty()) {
                    webView.loadUrl(url, extraHeaders)
                } else {
                    webView.loadUrl(url)
                }
                cont.invokeOnCancellation { webView.destroy() }
            }
        }
    }

    /**
     * Wait for a specific URL pattern to appear in the WebView's resource
     * requests — useful for catching the actual video stream URL.
     */
    suspend fun waitForUrl(
        url: String,
        pattern: Regex,
        timeoutMs: Long = 15_000
    ): String? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = USER_AGENT
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            if (url != null && pattern.matches(url)) {
                                if (cont.isActive) cont.resume(url)
                                return true
                            }
                            return false
                        }
                    }
                }
                webView.loadUrl(url)
                cont.invokeOnCancellation { webView.destroy() }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Extractor registry — CS3 plugins register their extractors here.
//  ProviderRepository / loadLinks calls runExtractors to find a match.
// ─────────────────────────────────────────────────────────────────────────────

val extractorApis: MutableList<ExtractorApi> = java.util.concurrent.CopyOnWriteArrayList()

/**
 * Try every registered extractor against [url]. Returns true if any
 * extractor produced links (calls [callback] for each link found).
 *
 * The first extractor whose [mainUrl] is a prefix of [url] is tried first;
 * if it fails, fall back to trying every extractor.
 */
suspend fun runExtractors(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    if (extractorApis.isEmpty()) return false

    // Try matching extractors first.
    val matching = extractorApis.filter { url.startsWith(it.mainUrl, ignoreCase = true) }
    for (api in matching) {
        try {
            api.getSafeUrl(url, referer, subtitleCallback, callback)
            // If at least one link was emitted, we're done.
            return true
        } catch (e: Exception) { logError(e) }
    }

    // Fall back to trying every extractor if none matched by URL prefix.
    if (matching.isEmpty()) {
        for (api in extractorApis) {
            try {
                api.getSafeUrl(url, referer, subtitleCallback, callback)
                return true
            } catch (e: Exception) { logError(e) }
        }
    }

    return false
}

/**
 * Get the [ExtractorApi] registered for [url], or null if none matches.
 */
fun getExtractorForUrl(url: String): ExtractorApi? {
    return extractorApis.firstOrNull { url.startsWith(it.mainUrl, ignoreCase = true) }
}
