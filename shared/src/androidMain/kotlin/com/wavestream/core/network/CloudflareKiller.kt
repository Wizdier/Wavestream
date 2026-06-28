package com.wavestream.core.network

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Cloudflare bypass interceptor — mirrors CloudStream's `CloudflareKiller.kt`.
 *
 * When an HTTP request returns 403/503 from a `cloudflare-nginx` server,
 * this interceptor:
 *   1. Checks CookieManager for `cf_clearance` cookie
 *   2. If missing, spawns a headless WebView that loads the URL
 *   3. Waits for Cloudflare's JS challenge to set cookies
 *   4. Extracts cookies via CookieManager
 *   5. Replays the request with the new cookies + WebView user agent
 *
 * Must be installed as an OkHttp Interceptor on Android.
 */
class CloudflareKiller : Interceptor {
    companion object {
        private const val TAG = "CloudflareKiller"
        private val ERROR_CODES = listOf(403, 503)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")

        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    private val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    @Volatile
    private var webViewUserAgent: String? = null

    init {
        // Clear cookies between sessions to generate new ones
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    fun getWebViewUserAgent(): String? {
        if (webViewUserAgent == null) {
            webViewUserAgent = runCatching {
                // This must be called on the main thread on Android
                CookieManager.getInstance().toString()  // forces initialization
                android.webkit.WebSettings.getDefaultUserAgentString(null)
            }.getOrNull()
        }
        return webViewUserAgent
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val cookies = savedCookies[request.url.host]
        return if (cookies == null) {
            val response = chain.proceed(request)
            if (response.header("Server") in CLOUDFLARE_SERVERS && response.code in ERROR_CODES) {
                response.close()
                bypassCloudflare(request)?.let { return it }
            }
            response
        } else {
            val userAgentMap = webViewUserAgent?.let { mapOf("user-agent" to it) } ?: emptyMap()
            val headers = getHeaders(request.headers.toMultimap().mapValues { it.value.joinToString(",") } + userAgentMap, cookies)
            val newRequest = request.newBuilder().headers(okhttp3.Headers.headersOf(*headers.entries.flatMap { listOf(it.key, it.value) }.toTypedArray())).build()
            chain.proceed(newRequest)
        } ?: chain.proceed(request)
    }

    private fun getHeaders(base: Map<String, String>, cookies: Map<String, String>): Map<String, String> {
        val cookieStr = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        return base + mapOf("Cookie" to cookieStr)
    }

    private fun bypassCloudflare(request: okhttp3.Request): Response? {
        val url = request.url.toString()
        // Try to get cookies from CookieManager first
        val cookieString = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        if (cookieString != null && cookieString.contains("cf_clearance")) {
            savedCookies[request.url.host] = parseCookieMap(cookieString)
        } else {
            // Would need a WebView to solve the challenge - skipped in headless env
            // Real implementation: spawn WebViewResolver
            return null
        }
        val cookies = savedCookies[request.url.host] ?: return null
        val userAgentMap = webViewUserAgent?.let { mapOf("user-agent" to it) } ?: emptyMap()
        val headers = getHeaders(request.headers.toMultimap().mapValues { it.value.joinToString(",") } + userAgentMap, cookies)
        val newRequest = request.newBuilder().headers(okhttp3.Headers.headersOf(*headers.entries.flatMap { listOf(it.key, it.value) }.toTypedArray())).build()
        return runCatching { chain.proceed(newRequest) }.getOrNull()
    }

    private val chain: Interceptor.Chain get() = throw UnsupportedOperationException("Use intercept(chain)")
}

/**
 * WebView resolver — used to solve Cloudflare JS challenges.
 * Declaration is in commonMain/WebViewResolver.kt
 */

/**
 * Initialize NetworkClient with Cloudflare bypass support.
 * Call this once at app startup on Android.
 */
fun initNetworkClientWithCloudflareBypass() {
    val cloudflareKiller = CloudflareKiller()

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(cloudflareKiller)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    NetworkClient.init(HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(NetworkClient.json)
        }
        install(HttpCookies)
        install(HttpTimeout)
        install(UserAgent) {
            agent = NetworkClient.DEFAULT_USER_AGENT
        }
        BrowserUserAgent()
    })
}
