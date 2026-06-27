@file:Suppress("UNUSED", "unused")

package com.lagradost.cloudstream3.actions

/** Stub VideoClickAction base class. */
abstract class VideoClickAction {
    abstract val name: String
    var sourcePlugin: String? = null
}
