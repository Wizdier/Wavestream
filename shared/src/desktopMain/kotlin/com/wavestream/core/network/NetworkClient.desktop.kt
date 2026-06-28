package com.wavestream.core.network

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*

/**
 * Initialize NetworkClient with Desktop Java engine.
 * Call this once at app startup.
 */
fun initNetworkClient() {
    NetworkClient.init(HttpClient(Java) {
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
    })
}
