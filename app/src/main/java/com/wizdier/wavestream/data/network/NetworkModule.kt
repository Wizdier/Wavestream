package com.wizdier.wavestream.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Singleton-ish network module shared by providers and sync clients.
 * CloudStream-style: every provider gets the same client with sane defaults
 * so cookies / headers / TLS config are uniform.
 */
object NetworkModule {

    val client: OkHttpClient by lazy { build() }

    private fun build(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .build()
    }
}
