package com.wizdier.wavestream.data.plugin

import android.content.Context
import android.content.pm.PackageManager
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
 * Loads [Provider] plugins from two sources:
 *  1. Built-in providers shipped inside the app (e.g. [PublicDomainProvider]).
 *  2. Provider extensions installed from user-supplied repository URLs.
 *
 * The second case mirrors CloudStream's site-extension system: each extension
 * is a small APK advertising a `wavestream-provider` meta-data entry whose
 * value is the fully-qualified class name implementing [Provider]. We load it
 * with a [DexClassLoader] and instantiate the class reflectively.
 *
 * Plugins are cached on disk under `filesDir/plugins/`.
 */
class PluginLoader(private val context: Context) {

    private val mutex = Mutex()
    private val _providers = MutableStateFlow<List<Provider>>(emptyList())
    val providers: StateFlow<List<Provider>> = _providers.asStateFlow()

    private val builtin: List<Provider> = listOf(PublicDomainProvider())

    /** Warm up the registry on first launch. Idempotent. */
    suspend fun initialize() = mutex.withLock {
        if (_providers.value.isEmpty()) {
            _providers.value = builtin + loadInstalledExtensions()
        }
    }

    /**
     * Reload the registry — call after installing or uninstalling an extension.
     */
    suspend fun reload() = mutex.withLock {
        _providers.value = builtin + loadInstalledExtensions()
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

    companion object {
        const val META_PROVIDER_CLASS = "com.wizdier.wavestream.provider.class"
    }
}
