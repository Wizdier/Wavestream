package com.wavestream.plugins

import com.wavestream.api.APIHolder
import com.wavestream.api.MainAPI
import com.wavestream.api.ExtractorApi
import kotlinx.serialization.Serializable

/**
 * Base plugin class — mirrors CloudStream's `com.lagradost.cloudstream3.plugins.BasePlugin`.
 *
 * A plugin is a .ws3/.cs3 file (zip containing classes.dex + manifest.json) on Android,
 * or a .jar file on Desktop.
 *
 * Subclasses override `load()` and call `registerMainAPI(provider)` / `registerExtractorAPI(extractor)`
 * to register their contributions.
 */
abstract class BasePlugin {
    @Serializable
    class Manifest(
        val name: String? = null,
        val pluginClassName: String? = null,
        val requiresResources: Boolean = false,
        val version: Int? = null,
        val apiVersion: Int = 1,
    )

    /** Full file path to the plugin file — set by PluginManager on load. */
    var filename: String? = null

    /** URL the plugin was downloaded from (null for local plugins). */
    var sourceUrl: String? = null

    /** Plugin version, populated from the manifest after load. */
    var version: Int = PLUGIN_VERSION_NOT_SET

    /** Called when the plugin is being unloaded — override for cleanup. */
    open fun beforeUnload() {}

    /** Called when the plugin is loaded — override to register providers/extractors. */
    open fun load() {}

    /** Register a MainAPI provider — adds it to APIHolder.allProviders + apis. */
    fun registerMainAPI(element: MainAPI) {
        println("[Plugin] Adding ${element.name} (${element.mainUrl}) MainAPI")
        element.sourcePlugin = this.filename
        APIHolder.allProviders.add(element)
        APIHolder.addPluginMapping(element)
    }

    /** Register an ExtractorApi — adds it to APIHolder.extractorApis. */
    fun registerExtractorAPI(element: ExtractorApi) {
        println("[Plugin] Adding ${element.name} (${element.mainUrl}) ExtractorApi")
        element.sourcePlugin = this.filename
        APIHolder.extractorApis.add(element)
    }
}

/**
 * Plugin annotation — marks a class as a Wavestream plugin entry point.
 * Used by the build tooling to generate the manifest automatically.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class WavestreamPlugin(
    val name: String = "",
    val version: Int = 1,
    val requiresResources: Boolean = false,
)
