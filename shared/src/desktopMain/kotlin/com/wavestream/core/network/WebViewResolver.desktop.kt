package com.wavestream.core.network

/**
 * Desktop stub for WebViewResolver — no WebView available on JVM.
 *
 * Cloudflare bypass will not work on desktop. Use a different IP or proxy.
 */
actual class WebViewResolver {
    actual val webViewUserAgent: String? = null

    actual suspend fun resolveUsingWebView(url: String, callback: () -> Boolean): Boolean {
        println("[WebViewResolver] Not available on desktop — skipping Cloudflare bypass for $url")
        return false
    }
}
