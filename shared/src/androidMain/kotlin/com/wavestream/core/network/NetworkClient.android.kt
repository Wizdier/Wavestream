package com.wavestream.core.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*

/**
 * Initialize NetworkClient with Android-specific OkHttp engine.
 * Call this once at app startup.
 */
fun initNetworkClient() {
    NetworkClient.init(HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
                followSslRedirects(true)
                retryOnConnectionFailure(true)
                connectTimeout(30_000)
                readTimeout(30_000)
                writeTimeout(30_000)
            }
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
