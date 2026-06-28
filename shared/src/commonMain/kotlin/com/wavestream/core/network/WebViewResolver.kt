package com.wavestream.core.network

/**
 * WebView resolver — used to solve Cloudflare JS challenges.
 *
 * On Android: creates a headless WebView that loads the URL,
 * waits for Cloudflare's JS challenge to set cookies, then extracts them.
 *
 * On Desktop: not available (returns false).
 *
 * Mirrors CloudStream's `WebViewResolver`.
 */
expect class WebViewResolver() {
    val webViewUserAgent: String?
    suspend fun resolveUsingWebView(url: String, callback: () -> Boolean): Boolean
}
