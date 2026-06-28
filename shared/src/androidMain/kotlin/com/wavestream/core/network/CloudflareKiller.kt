package com.wavestream.core.network

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

private var appContext: Context? = null

fun initCloudflareKiller(context: Context) {
    appContext = context.applicationContext
}

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
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
    }

    fun getWebViewUserAgent(): String? {
        if (webViewUserAgent == null) {
            webViewUserAgent = runCatching {
                val ctx = appContext ?: return null
                WebSettings.getDefaultUserAgentString(ctx)
            }.getOrNull()
        }
        return webViewUserAgent
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val cookies = savedCookies[host]

        if (cookies == null) {
            val response = chain.proceed(request)
            val server = response.header("Server")
            if (server in CLOUDFLARE_SERVERS && response.code in ERROR_CODES) {
                response.close()
                // Try to solve Cloudflare
                val solved = solveCloudflare(request.url.toString())
                if (solved) {
                    val newCookies = savedCookies[host]
                    if (newCookies != null) {
                        val ua = webViewUserAgent?.let { mapOf("user-agent" to it) } ?: emptyMap()
                        val cookieStr = newCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                        val newRequest = request.newBuilder()
                            .header("Cookie", cookieStr)
                            .apply { ua.forEach { (k, v) -> header(k, v) } }
                            .build()
                        return chain.proceed(newRequest)
                    }
                }
            }
            return response
        } else {
            val ua = webViewUserAgent?.let { mapOf("user-agent" to it) } ?: emptyMap()
            val cookieStr = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val newRequest = request.newBuilder()
                .header("Cookie", cookieStr)
                .apply { ua.forEach { (k, v) -> header(k, v) } }
                .build()
            return chain.proceed(newRequest)
        }
    }

    private fun solveCloudflare(url: String): Boolean {
        val cookieString = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        if (cookieString != null && cookieString.contains("cf_clearance")) {
            val host = try { java.net.URI(url).host } catch (e: Exception) { return false }
            savedCookies[host] = parseCookieMap(cookieString)
            return true
        }
        // In a full implementation, this would spawn a WebView to solve the challenge.
        // For now, just check existing cookies.
        return false
    }
}

fun initNetworkClientWithCloudflareBypass() {
    val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    NetworkClient.init(HttpClient(OkHttp) {
        engine { preconfigured = okHttpClient }
        install(ContentNegotiation) { json(NetworkClient.json) }
        install(HttpCookies)
        install(HttpTimeout)
        install(UserAgent) { agent = NetworkClient.DEFAULT_USER_AGENT }
        BrowserUserAgent()
    })
}
