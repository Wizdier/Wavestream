package com.wizdier.wavestream.data.plugin
import android.content.Context
import android.util.Log
import com.wizdier.wavestream.data.api.Provider
import com.wizdier.wavestream.data.api.PublicDomainProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class PluginLoader(private val context: Context) {
    private val mutex = Mutex()
    private val _providers = MutableStateFlow<List<Provider>>(emptyList())
    val providers: StateFlow<List<Provider>> = _providers.asStateFlow()
    private val builtin: List<Provider> = listOf(PublicDomainProvider())
    private val cs3Loader by lazy { Cs3PluginLoader(context) }

    suspend fun initialize() = mutex.withLock { if (_providers.value.isEmpty()) { _providers.value = builtin + loadCs3Plugins() } }
    suspend fun reload() = mutex.withLock { _providers.value = builtin + loadCs3Plugins() }
    fun byId(id: String): Provider? = _providers.value.firstOrNull { it.id == id }

    private fun loadCs3Plugins(): List<Provider> {
        val extDir = File(context.cacheDir, "extensions")
        if (!extDir.exists()) return emptyList()
        val cs3Files = extDir.listFiles { f -> f.isFile && f.name.endsWith(".cs3", ignoreCase = true) } ?: return emptyList()
        val all = mutableListOf<Provider>()
        for (file in cs3Files) { runCatching { all += cs3Loader.load(file) }.onFailure { Log.e("PluginLoader", "Failed ${file.name}", it) } }
        return all
    }
}
