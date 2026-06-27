package com.wizdier.wavestream.data.plugin

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.wizdier.wavestream.data.api.Provider
import com.wizdier.wavestream.data.api.PublicDomainProvider
import dalvik.system.DexClassLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Loads [Provider] plugins from three sources:
 *  1. Built-in providers shipped inside the app (e.g. [PublicDomainProvider]).
 *  2. Provider APK extensions installed from user-supplied repository URLs
 *     (classic CloudStream-style .apk extensions with the
 *     `com.wizdier.wavestream.provider.class` meta-data).
 *  3. CloudStream 3 `.cs3` plugin files downloaded to `cacheDir/extensions/`
 *     — these are ZIP archives containing `classes.dex` + `manifest.json`.
 *     Loaded by [Cs3PluginLoader], which uses a DexClassLoader with
 *     WaveStream's classloader as parent so the plugin can resolve the
 *     `com.lagradost.cloudstream3.*` stub classes shipped inside WaveStream.
 *
 * Plugins are cached on disk under `filesDir/plugins/` (APKs) and
 * `codeCacheDir/cs3-plugins/` (extracted .dex files).
 */
class PluginLoader(private val context: Context) {

    private val mutex = Mutex()
    private val _providers = MutableStateFlow<List<Provider>>(emptyList())
    val providers: StateFlow<List<Provider>> = _providers.asStateFlow()

    private val builtin: List<Provider> = listOf(PublicDomainProvider())
    private val cs3Loader by lazy { Cs3PluginLoader(context) }

    /** Warm up the registry on first launch. Idempotent. */
    suspend fun initialize() = mutex.withLock {
        if (_providers.value.isEmpty()) {
            _providers.value = builtin + loadInstalledExtensions() + loadCs3Plugins()
        }
    }

    /** Reload the registry — call after installing or uninstalling an extension. */
    suspend fun reload() = mutex.withLock {
        _providers.value = builtin + loadInstalledExtensions() + loadCs3Plugins()
    }

    fun byId(id: String): Provider? = _providers.value.firstOrNull { it.id == id }

    private fun loadInstalledExtensions(): List<Provider> {
        val pm = context.packageManager
        val flag = PackageManager.GET_META_DATA or PackageManager.GET_SHARED_LIBRARY_FILES
        val candidates = runCatching {
            pm.getInstalledPackages(flag).filter { pkg ->
                pkg.applicationInfo?.metaData?.getString(META_PROVIDER_CLASS) != null
            }
        }.getOrDefault(emptyList())

        return candidates.mapNotNull { pkg ->
            val className = pkg.applicationInfo?.metaData?.getString(META_PROVIDER_CLASS) ?: return@mapNotNull null
            val sourceApk = pkg.applicationInfo?.sourceDir ?: return@mapNotNull null
            runCatching {
                val loader = DexClassLoader(
                    sourceApk,
                    File(context.filesDir, "plugins").apply { mkdirs() }.absolutePath,
                    null,
                    context.classLoader
                )
                val cls = loader.loadClass(className)
                cls.getDeclaredConstructor().newInstance() as? Provider
            }.getOrNull()
        }
    }

    /**
     * Scan `cacheDir/extensions/` for `.cs3` files and load each one.
     * Failures are logged and skipped so one bad plugin doesn't break the
     * rest.
     */
    private fun loadCs3Plugins(): List<Provider> {
        val extDir = File(context.cacheDir, "extensions")
        if (!extDir.exists()) return emptyList()
        val cs3Files = extDir.listFiles { f -> f.isFile && f.name.endsWith(".cs3", ignoreCase = true) }
            ?: return emptyList()

        val all = mutableListOf<Provider>()
        for (file in cs3Files) {
            runCatching {
                all += cs3Loader.load(file)
            }.onFailure {
                Log.e("PluginLoader", "Failed to load ${file.name}", it)
            }
        }
        return all
    }

    companion object {
        const val META_PROVIDER_CLASS = "com.wizdier.wavestream.provider.class"
    }
}
