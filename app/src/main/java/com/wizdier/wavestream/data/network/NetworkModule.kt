package com.wizdier.wavestream.data.network

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Singleton network client shared by every provider and sync client.
 * CloudStream-style: every provider gets the same client with sane defaults
 * so cookies / headers / TLS config are uniform.
 *
 * Tuned for streaming-site scraping:
 *  - Long timeouts (sites are slow, especially on mobile networks)
 *  - HTTP/2 enabled (most CDNs require it)
 *  - Modern TLS only (CLEARTEXT blocked — providers must use HTTPS)
 *  - Redirects followed (site → CDN hops)
 *  - Gzip handled automatically by OkHttp
 */
object NetworkModule {

    val client: OkHttpClient by lazy { build() }

    private fun build(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)        // longer for slow streaming sites
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)        // hard cap on full request
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectionSpecs(
                listOf(
                    ConnectionSpec.RESTRICTED_TLS,    // modern TLS only
                    ConnectionSpec.MODERN_TLS
                )
            )
            .addInterceptor(logging)
            .build()
    }
}
