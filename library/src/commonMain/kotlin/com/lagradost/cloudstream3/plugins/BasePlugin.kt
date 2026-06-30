package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis
import kotlinx.serialization.Serializable

abstract class BasePlugin {
    @Serializable
    class Manifest(
        val name: String? = null,
        val pluginClassName: String? = null,
        val requiresResources: Boolean = false,
        val version: Int? = null,
        val apiVersion: Int = 1,
    )

    var filename: String? = null
    var sourceUrl: String? = null
    var version: Int = PluginManager.PLUGIN_VERSION_NOT_SET

    open fun beforeUnload() {}
    open fun load() {}

    fun registerMainAPI(element: MainAPI) {
        println("[Plugin] Adding ${element.name} (${element.mainUrl})")
        element.sourcePlugin = this.filename
        com.lagradost.cloudstream3.APIHolder.addPluginMapping(element)
        com.lagradost.cloudstream3.APIHolder.allProviders.add(element)
    }

    fun registerExtractorAPI(element: ExtractorApi) {
        println("[Plugin] Adding ${element.name} (${element.mainUrl})")
        element.sourcePlugin = this.filename
        extractorApis.add(element)
    }
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudstreamPlugin(
    val name: String = "",
    val version: Int = 1,
    val requiresResources: Boolean = false,
)
