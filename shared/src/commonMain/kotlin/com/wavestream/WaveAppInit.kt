package com.wavestream

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginManager
import com.wavestream.platform.wavePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Single source of truth for app boot state. UI observes [bootState] to know
 * when it can render real data vs. a loading screen.
 *
 * Boot is idempotent — calling [initialize] twice is safe. The second call
 * is a no-op once boot has started.
 */
object WaveAppInit {

    enum class BootStage {
        NOT_STARTED,
        LOADING_PLUGINS,
        READY,
        FAILED,
        ;
        val isReady: Boolean get() = this == READY
    }

    data class BootState(
        val stage: BootStage = BootStage.NOT_STARTED,
        val message: String? = null,
        val pluginsLoaded: Int = 0,
    )

    private val _bootState = MutableStateFlow(BootState())
    val bootState: StateFlow<BootState> = _bootState.asStateFlow()

    private val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var bootStarted = false

    /**
     * Wires the persistent store into the library's RepositoryManager, then
     * loads all plugins from the extensions directory.
     *
     * @param extensionsDir Override for the extensions directory; defaults
     *        to [wavePlatform.extensionsDir]. The MainActivity in the guide
     *        passes `File(filesDir, "Extensions")` here, which matches the
     *        platform default.
     */
    fun initialize(extensionsDir: File? = null) {
        if (bootStarted) return
        bootStarted = true

        RepositoryStore.install()

        val dir = extensionsDir ?: wavePlatform.extensionsDir
        _bootState.value = BootState(BootStage.LOADING_PLUGINS, "Loading extensions…")

        bootScope.launch {
            try {
                PluginManager.loadAllFromDirectory(dir)
                APIHolder.initAll()

                val count = PluginManager.plugins.size
                _bootState.value = BootState(
                    stage = BootStage.READY,
                    message = if (count == 0) "No extensions installed" else "Loaded $count extensions",
                    pluginsLoaded = count,
                )
            } catch (e: Throwable) {
                _bootState.value = BootState(
                    stage = BootStage.FAILED,
                    message = e.message ?: "Unknown boot error",
                )
            }
        }
    }

    /** Re-scan the extensions dir and reload plugins. Useful after installing a new repo. */
    fun rescan() {
        val dir = wavePlatform.extensionsDir
        bootScope.launch {
            _bootState.value = _bootState.value.copy(stage = BootStage.LOADING_PLUGINS, message = "Reloading…")
            try {
                PluginManager.loadAllFromDirectory(dir)
                APIHolder.initAll()
                _bootState.value = BootState(
                    stage = BootStage.READY,
                    message = "Loaded ${PluginManager.plugins.size} extensions",
                    pluginsLoaded = PluginManager.plugins.size,
                )
            } catch (e: Throwable) {
                _bootState.value = _bootState.value.copy(stage = BootStage.FAILED, message = e.message)
            }
        }
    }
}
