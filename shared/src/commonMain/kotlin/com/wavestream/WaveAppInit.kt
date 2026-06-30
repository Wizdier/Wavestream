package com.wavestream

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginManager
import com.wavestream.platform.wavePlatform
import com.wavestream.stremio.StremioAddonRepository
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
        SEEDING_DEFAULTS,
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
        val providersLoaded: Int = 0,
        val reposSeeded: Int = 0,
        val addonsSeeded: Int = 0,
    )

    private val _bootState = MutableStateFlow(BootState())
    val bootState: StateFlow<BootState> = _bootState.asStateFlow()

    private val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var bootStarted = false

    /**
     * Wires the persistent store into the library's RepositoryManager, seeds
     * default repositories + Stremio addons on first launch, then loads all
     * plugins from the extensions directory.
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
        _bootState.value = BootState(BootStage.SEEDING_DEFAULTS, "Preparing first launch…")

        bootScope.launch {
            try {
                // 1. Seed default CS repos + Stremio addons on first launch
                val seeded = DefaultRepos.seedIfFirstLaunch()

                // 2. Register all installed Stremio addons as providers
                //    (also re-runs after seeding to pick up new addons)
                StremioAddonRepository.syncProviders()

                _bootState.value = _bootState.value.copy(
                    stage = BootStage.LOADING_PLUGINS,
                    message = "Loading extensions…",
                    reposSeeded = if (seeded) DefaultRepos.CLOUDSTREAM_REPOS.size else 0,
                    addonsSeeded = if (seeded) DefaultRepos.STREMIO_ADDONS.size else 0,
                )

                // 3. Load all .cs3/.jar plugins from the extensions directory
                PluginManager.loadAllFromDirectory(dir)
                APIHolder.initAll()

                val pluginCount = PluginManager.plugins.size
                val providerCount = APIHolder.allProviders.withLock { APIHolder.allProviders.size }
                _bootState.value = BootState(
                    stage = BootStage.READY,
                    message = buildString {
                        append("$pluginCount plugins")
                        if (providerCount > 0) append(", $providerCount providers")
                        if (seeded) append(" · seeded defaults")
                    },
                    pluginsLoaded = pluginCount,
                    providersLoaded = providerCount,
                    reposSeeded = if (seeded) DefaultRepos.CLOUDSTREAM_REPOS.size else 0,
                    addonsSeeded = if (seeded) DefaultRepos.STREMIO_ADDONS.size else 0,
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
                StremioAddonRepository.syncProviders()
                PluginManager.loadAllFromDirectory(dir)
                APIHolder.initAll()
                _bootState.value = BootState(
                    stage = BootStage.READY,
                    message = "Loaded ${PluginManager.plugins.size} plugins",
                    pluginsLoaded = PluginManager.plugins.size,
                    providersLoaded = APIHolder.allProviders.withLock { APIHolder.allProviders.size },
                )
            } catch (e: Throwable) {
                _bootState.value = _bootState.value.copy(stage = BootStage.FAILED, message = e.message)
            }
        }
    }

    /** Force a re-seed of the default repos and addons. Exposed for the Settings screen. */
    fun restoreDefaults() {
        bootScope.launch {
            _bootState.value = _bootState.value.copy(stage = BootStage.SEEDING_DEFAULTS, message = "Restoring defaults…")
            try {
                DefaultRepos.forceReseed()
                StremioAddonRepository.syncProviders()
                _bootState.value = _bootState.value.copy(
                    stage = BootStage.READY,
                    message = "Restored default repos and addons",
                )
            } catch (e: Throwable) {
                _bootState.value = _bootState.value.copy(stage = BootStage.FAILED, message = e.message)
            }
        }
    }
}

