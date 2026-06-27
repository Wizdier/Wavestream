@file:Suppress("UNUSED", "unused")

package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI

const val PLUGIN_TAG = "PluginInstance"

/**
 * CloudStream 3's Plugin base class. A `.cs3` file's manifest declares a
 * `pluginClassName` pointing at a subclass of [Plugin]. WaveStream's
 * Cs3PluginLoader instantiates that class and calls [load].
 *
 * Plugins register providers by calling [registerMainAPI] from inside
 * their `load()` override.
 */
abstract class BasePlugin {

    /** Called when the plugin is loaded. Override to register providers. */
    @Throws(Throwable::class)
    open fun load() {}

    /** Called before the plugin is unloaded. Override for cleanup. */
    @Throws(Throwable::class)
    open fun beforeUnload() {}

    /** Register a [MainAPI] provider with the global [APIHolder]. */
    fun registerMainAPI(element: MainAPI) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} (${element.mainUrl}) MainAPI")
        element.sourcePlugin = this.filename
        APIHolder.allProviders.add(element)
        APIHolder.addPluginMapping(element)
    }

    /** Register an extractor (stub — extractors aren't supported yet). */
    fun registerExtractorAPI(element: com.lagradost.cloudstream3.utils.ExtractorApi) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} ExtractorApi (stub — not supported)")
        element.sourcePlugin = this.filename
        com.lagradost.cloudstream3.utils.extractorApis.add(element)
    }

    /** Full path to the .cs3 file on disk. */
    var filename: String? = null

    @Deprecated(
        "Renamed to `filename` to follow conventions",
        replaceWith = ReplaceWith("filename"),
        level = DeprecationLevel.ERROR
    )
    var __filename: String?
        get() = filename
        set(value) { filename = value }

    class Manifest {
        var name: String? = null
        var pluginClassName: String? = null
        var requiresResources: Boolean = false
        var version: Int? = null
    }
}

/** Android variant — adds Context + Resources. Plugins extend this one. */
abstract class Plugin : BasePlugin() {

    @Throws(Throwable::class)
    open fun load(context: Context) {
        // Default falls back to the cross-platform load()
        load()
    }

    /** Register a VideoClickAction (stub). */
    fun registerVideoClickAction(element: com.lagradost.cloudstream3.actions.VideoClickAction) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} VideoClickAction (stub)")
    }

    var resources: Resources? = null
    var openSettings: ((context: Context) -> Unit)? = null
}
