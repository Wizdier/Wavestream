package com.wizdier.wavestream.data.network
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {
    val client: OkHttpClient by lazy { build() }
    private fun build(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
}
