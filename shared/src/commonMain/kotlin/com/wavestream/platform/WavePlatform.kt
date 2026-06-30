package com.wavestream.platform

import java.io.File

/**
 * Platform-specific runtime context. Set by the host app at startup
 * (Android: Activity; Desktop: main entry point).
 */
interface WavePlatform {
    /** Root directory for persistent app data (extensions, settings, cache). */
    val dataDir: File

    /** Directory for downloaded extension plugins (.cs3/.jar). */
    val extensionsDir: File

    /** Directory for downloaded video files. */
    val downloadsDir: File

    /** Persistent key-value store (settings, repo list, library). */
    val preferences: WavePreferences

    /** A scope that lives as long as the platform (process / activity). */
    val coroutineScope: kotlinx.coroutines.CoroutineScope
}

interface WavePreferences {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String, default: Int = -1): Int
    fun putInt(key: String, value: Int)
    fun getBool(key: String, default: Boolean = false): Boolean
    fun putBool(key: String, value: Boolean)
    fun remove(key: String)
    fun keys(): Set<String>
}

/** Set by [initPlatform] at boot. */
@Volatile
var WavePlatformInstance: WavePlatform? = null

/** Convenience accessor — throws if called before [initPlatform]. */
val wavePlatform: WavePlatform
    get() = WavePlatformInstance
        ?: error("WavePlatform not initialized. Call initPlatform() first.")

expect fun initPlatform(configure: WavePlatformBuilder.() -> Unit)

/**
 * Builder used by [initPlatform] to construct the platform instance.
 * The platform-specific actual implementations fill in defaults.
 */
class WavePlatformBuilder {
    var dataDir: File? = null
    var extensionsDir: File? = null
    var downloadsDir: File? = null
    var preferences: WavePreferences? = null
    var coroutineScope: kotlinx.coroutines.CoroutineScope? = null

    fun build(): WavePlatform {
        val data = dataDir ?: error("dataDir must be set")
        val ext = extensionsDir ?: File(data, "Extensions").also { it.mkdirs() }
        val dl = downloadsDir ?: File(data, "Downloads").also { it.mkdirs() }
        val prefs = preferences ?: InMemoryPreferences()
        val scope = coroutineScope ?: kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
        )
        return DefaultWavePlatform(data, ext, dl, prefs, scope)
    }
}

private class DefaultWavePlatform(
    override val dataDir: File,
    override val extensionsDir: File,
    override val downloadsDir: File,
    override val preferences: WavePreferences,
    override val coroutineScope: kotlinx.coroutines.CoroutineScope,
) : WavePlatform

/**
 * Simple in-memory preferences implementation. Used as a fallback when the
 * platform doesn't provide a real persistent store. Not suitable for
 * production persistence.
 */
class InMemoryPreferences : WavePreferences {
    private val backing = mutableMapOf<String, Any?>()
    private val lock = Any()

    override fun getString(key: String, default: String?): String? = synchronized(lock) {
        backing[key] as? String ?: default
    }

    override fun putString(key: String, value: String?): Unit = synchronized(lock) {
        if (value == null) backing.remove(key) else backing[key] = value
    }

    override fun getInt(key: String, default: Int): Int = synchronized(lock) {
        (backing[key] as? Int) ?: default
    }

    override fun putInt(key: String, value: Int): Unit = synchronized(lock) {
        backing[key] = value
    }

    override fun getBool(key: String, default: Boolean): Boolean = synchronized(lock) {
        (backing[key] as? Boolean) ?: default
    }

    override fun putBool(key: String, value: Boolean): Unit = synchronized(lock) {
        backing[key] = value
    }

    override fun remove(key: String): Unit = synchronized(lock) {
        backing.remove(key)
    }

    override fun keys(): Set<String> = synchronized(lock) {
        backing.keys.toSet()
    }
}
