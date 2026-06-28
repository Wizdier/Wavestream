package com.wavestream.core.network

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Global HTTP client used by all Wavestream subsystems.
 *
 * Mirrors CloudStream's `app` singleton (which wraps OkHttp via nicehttp).
 * Provides:
 *   - Default User-Agent
 *   - 30s connect/read timeouts
 *   - Content negotiation (JSON via kotlinx.serialization)
 *   - Cookie jar
 *   - Optional Cloudflare bypass interceptor (Android only)
 *
 * For JS plugin runtime, see `JsFetchBridge` which calls back into this client.
 */
object NetworkClient {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        isLenient = true
    }

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    /**
     * Engine-specific HTTP client factory. Initialized lazily by platform-specific code.
     */
    @Volatile
    private var _client: HttpClient? = null

    val client: HttpClient
        get() = _client ?: throw IllegalStateException("NetworkClient not initialized. Call initNetworkClient() first.")

    fun init(client: HttpClient) {
        _client = client
    }

    /**
     * GET request returning the response.
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeoutMs: Long = 30_000L,
    ): HttpResponse = withContext(Dispatchers.IO) {
        client.get(url) {
            this.timeout {
                requestTimeoutMillis = timeoutMs
                connectTimeoutMillis = timeoutMs
                socketTimeoutMillis = timeoutMs
            }
            headers.forEach { (k, v) -> header(k, v) }
            referer?.let { header(HttpHeaders.Referrer, it) }
            if (headers.keys.none { it.equals("user-agent", ignoreCase = true) }) {
                header(HttpHeaders.UserAgent, DEFAULT_USER_AGENT)
            }
        }
    }

    /**
     * GET request returning the response body as text.
     */
    suspend fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
    ): String = get(url, headers, referer).bodyAsText()

    /**
     * POST request returning the response body as text.
     */
    suspend fun postText(
        url: String,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
        contentType: String = "application/json",
    ): String = withContext(Dispatchers.IO) {
        client.post(url) {
            this.timeout {
                requestTimeoutMillis = 30_000L
                connectTimeoutMillis = 30_000L
                socketTimeoutMillis = 30_000L
            }
            headers.forEach { (k, v) -> header(k, v) }
            if (headers.keys.none { it.equals("user-agent", ignoreCase = true) }) {
                header(HttpHeaders.UserAgent, DEFAULT_USER_AGENT)
            }
            if (body.isNotEmpty()) {
                setBody(body)
                contentType(ContentType.parse(contentType))
            }
        }.bodyAsText()
    }

    /**
     * HEAD request returning the response (for Content-Length checks).
     */
    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse = withContext(Dispatchers.IO) {
        client.head(url) {
            this.timeout {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 15_000L
                socketTimeoutMillis = 15_000L
            }
            headers.forEach { (k, v) -> header(k, v) }
        }
    }
}

/**
 * Convenience top-level `app` object — mirrors CloudStream's `app` singleton.
 * Extensions use `app.get(url)` etc.
 */
object app {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), referer: String? = null): HttpResponse =
        NetworkClient.get(url, headers, referer)

    suspend fun post(url: String, body: String = "", headers: Map<String, String> = emptyMap()): String =
        NetworkClient.postText(url, body, headers)

    suspend fun head(url: String, headers: Map<String, String> = emptyMap()): HttpResponse =
        NetworkClient.head(url, headers)
}

/**
 * Lightweight HTTP response wrapper for cases where the caller needs raw access
 * (mirrors NuvioMobile's `RawHttpResponse`).
 */
data class RawHttpResponse(
    val status: Int,
    val statusText: String,
    val url: String,
    val body: String,
    val headers: Map<String, String>,
) {
    val isSuccessful: Boolean get() = status in 200..299
}

