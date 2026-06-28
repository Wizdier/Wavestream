@file:Suppress("UNUSED", "unused")
package com.lagradost.cloudstream3.plugins
import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
const val PLUGIN_TAG = "PluginInstance"
abstract class BasePlugin {
    @Throws(Throwable::class)
    open fun load() {}
    @Throws(Throwable::class)
    open fun beforeUnload() {}
    fun registerMainAPI(element: MainAPI) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} (${element.mainUrl})")
        element.sourcePlugin = this.filename
        APIHolder.allProviders.add(element)
        APIHolder.addPluginMapping(element)
    }
    fun registerExtractorAPI(element: com.lagradost.cloudstream3.utils.ExtractorApi) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} ExtractorApi")
        element.sourcePlugin = this.filename
        com.lagradost.cloudstream3.utils.extractorApis.add(element)
    }
    var filename: String? = null
    class Manifest {
        var name: String? = null
        var pluginClassName: String? = null
        var requiresResources: Boolean = false
        var version: Int? = null
    }
}
abstract class Plugin : BasePlugin() {
    @Throws(Throwable::class)
    open fun load(context: Context) { load() }
    var resources: Resources? = null
    var openSettings: ((context: Context) -> Unit)? = null
}
