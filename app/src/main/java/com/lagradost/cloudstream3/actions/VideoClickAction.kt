@file:Suppress("UNUSED", "unused")
package com.lagradost.cloudstream3.actions
abstract class VideoClickAction {
    abstract val name: String
    var sourcePlugin: String? = null
}
