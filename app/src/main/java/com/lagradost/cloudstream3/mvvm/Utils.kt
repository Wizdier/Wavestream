@file:Suppress("UNUSED", "unused")

package com.lagradost.cloudstream3.mvvm

import android.util.Log

object Coroutines {
    /** Thread-safe list backed by [java.util.concurrent.CopyOnWriteArrayList]. */
    fun <T> atomicListOf(): MutableList<T> = java.util.concurrent.CopyOnWriteArrayList()
}

/** Log an error to logcat with the WaveStream tag. */
fun logError(error: Throwable) {
    Log.e("WaveStream", "Plugin error", error)
}

/** Run [block] and return its result, or null if it threw. Errors are logged. */
inline fun <T> safe(block: () -> T): T? = try {
    block()
} catch (t: Throwable) {
    logError(t)
    null
}
