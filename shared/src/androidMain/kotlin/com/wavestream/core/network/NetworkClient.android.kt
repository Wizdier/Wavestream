package com.wavestream.core.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*
import java.util.concurrent.TimeUnit

fun initNetworkClient() {
    NetworkClient.init(HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
                followSslRedirects(true)
                retryOnConnectionFailure(true)
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
            }
        }
        install(ContentNegotiation) { json(NetworkClient.json) }
        install(HttpCookies)
        install(HttpTimeout)
        install(UserAgent) { agent = NetworkClient.DEFAULT_USER_AGENT }
        BrowserUserAgent()
    })
}
