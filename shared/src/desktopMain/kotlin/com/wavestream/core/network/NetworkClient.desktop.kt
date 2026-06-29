package com.wavestream.core.network

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.observer.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

/**
 * Initialize NetworkClient with Desktop Java engine.
 * Call this once at app startup.
 *
 * Features:
 *   - Java HTTP engine (follows redirects by default)
 *   - ContentNegotiation for JSON
 *   - HttpCookies for session persistence
 *   - HttpTimeout (30s default)
 *   - Browser-like User-Agent
 *   - Response observer for debugging (logs HTTP errors)
 */
fun initNetworkClient() {
    NetworkClient.init(HttpClient(Java) {
        followRedirects = true
        install(ContentNegotiation) {
            json(NetworkClient.json)
        }
        install(HttpCookies)
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        install(UserAgent) {
            agent = NetworkClient.DEFAULT_USER_AGENT
        }
        BrowserUserAgent()
        install(ResponseObserver) {
            onResponse { response ->
                if (!response.status.value.toString().startsWith("2")) {
                    val url = response.call.request.url
                    val method = response.call.request.method.value
                    println("[NetworkClient] $method $url -> ${response.status.value} ${response.status.description}")
                }
            }
        }
    })
}


