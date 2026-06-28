package com.wavestream.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private var appContext: Context? = null

fun initWebViewResolver(context: Context) {
    appContext = context.applicationContext
}

/**
 * Android implementation of WebViewResolver — uses WebView to solve Cloudflare challenges.
 *
 * Mirrors CloudStream's `WebViewResolver.android.kt`.
 */
actual class WebViewResolver {
    actual val webViewUserAgent: String? by lazy {
        runCatching {
            android.webkit.WebSettings.getDefaultUserAgentString(appContext)
        }.getOrNull()
    }

    @SuppressLint("SetJavaScriptEnabled")
    actual suspend fun resolveUsingWebView(url: String, callback: () -> Boolean): Boolean {
        val context = appContext ?: return false
        return withTimeoutOrNull(30_000L) {
            var resolved = false
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.userAgentString = webViewUserAgent
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (callback()) resolved = true
                }
            }
            webView.webChromeClient = WebChromeClient()
            webView.loadUrl(url)

            // Poll for cookies
            while (!resolved) {
                delay(500)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (cookies?.contains("cf_clearance") == true) {
                    if (callback()) resolved = true
                }
            }
            webView.destroy()
            resolved
        } ?: false
    }
}
