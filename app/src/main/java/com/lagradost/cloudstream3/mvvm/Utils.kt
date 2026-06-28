@file:Suppress("UNUSED", "unused")
package com.lagradost.cloudstream3.mvvm
import android.util.Log
object Coroutines {
    fun <T> atomicListOf(): MutableList<T> = java.util.concurrent.CopyOnWriteArrayList()
}
fun logError(error: Throwable) { Log.e("WaveStream", "Plugin error", error) }
inline fun <T> safe(block: () -> T): T? = try { block() } catch (t: Throwable) { logError(t); null }
