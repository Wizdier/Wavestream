package com.wavestream.platform

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Android-specific initialization. The host Activity should call this in
 * [Activity.onCreate] before rendering any composable.
 *
 * Usage:
 * ```
 * initPlatform(this)        // simplest form, picks reasonable defaults
 * // or
 * initPlatform {
 *     dataDir = File(filesDir, "Wavestream")
 *     ...
 * }
 * ```
 */
fun initPlatform(context: Context) {
    initPlatform {
        val dataDir = File(context.filesDir, "Wavestream").also { it.mkdirs() }
        this.dataDir = dataDir
        this.extensionsDir = File(dataDir, "Extensions").also { it.mkdirs() }
        this.downloadsDir = File(dataDir, "Downloads").also { it.mkdirs() }
        this.preferences = AndroidPreferences(context)
        this.coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

private class AndroidPreferences(private val context: Context) : WavePreferences {
    private val prefs by lazy {
        context.getSharedPreferences("wavestream", Context.MODE_PRIVATE)
    }

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String?) = prefs.edit().apply {
        if (value == null) remove(key) else putString(key, value)
    }.apply()
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun getBool(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun keys(): Set<String> = prefs.all.keys
}

actual fun initPlatform(configure: WavePlatformBuilder.() -> Unit) {
    val builder = WavePlatformBuilder()
    builder.configure()
    WavePlatformInstance = builder.build()
}
