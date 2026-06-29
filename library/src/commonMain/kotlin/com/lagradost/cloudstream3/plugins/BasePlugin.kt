package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.MainAPI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Base plugin class — mirrors CloudStream's BasePlugin.
 *
 * Subclasses override `load()` and call `registerMainAPI(provider)` /
 * `registerExtractorAPI(extractor)` to register their contributions.
 *
 * A plugin is a .ws3/.cs3 file (zip with classes.dex + manifest.json) on Android,
 * or a .jar file on Desktop.
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
    var version: Int = PluginManager.PLUGIN_VERSION_NOT_SET

    /** Called when the plugin is being unloaded — override for cleanup. */
    open fun beforeUnload() {}

    /** Called when the plugin is loaded — override to register providers/extractors. */
    open fun load() {}

    /** Register a MainAPI provider. */
    fun registerMainAPI(element: MainAPI) {
        println("[Plugin] Adding ${element.name} (${element.mainUrl})")
        element.sourcePlugin = this.filename
        // Add to APIHolder — both allProviders and apis lists
        com.lagradost.cloudstream3.APIHolder.addPluginMapping(element)
        com.lagradost.cloudstream3.APIHolder.allProviders.add(element)
    }

    /** Register an ExtractorApi. */
    fun registerExtractorAPI(element: com.lagradost.cloudstream3.utils.ExtractorApi) {
        println("[Plugin] Adding ${element.name} (${element.mainUrl})")
        element.sourcePlugin = this.filename
        com.lagradost.cloudstream3.utils.extractorApis.add(element)
    }
}

/**
 * Plugin annotation — marks a class as a plugin entry point.
 * Used by build tooling to generate the manifest automatically.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudstreamPlugin(
    val name: String = "",
    val version: Int = 1,
    val requiresResources: Boolean = false,
)
