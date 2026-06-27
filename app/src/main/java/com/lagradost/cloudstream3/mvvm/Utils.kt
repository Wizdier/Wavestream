@file:Suppress("UNUSED", "unused")

package com.lagradost.cloudstream3.mvvm

/**
 * CloudStream 3 shim — `mvvm/Utils.kt`.
 *
 * In real CloudStream this file declares the small `Coroutines` factory and
 * the `logError` / `safe` helpers. WaveStream already ships the full
 * implementations of `logError`, `safe` and `launchSafe` in
 * [ArchComponentExt.kt] (alongside `Resource`, `safeApiCall`, etc.) — those
 * are the canonical versions referenced by the rest of the shim.
 *
 * To avoid "conflicting overload" / redeclaration errors we only keep the
 * `Coroutines` factory here; the helpers are imported from
 * `ArchComponentExt.kt` by callers via `com.lagradost.cloudstream3.mvvm.*`.
 */
object Coroutines {
    /** Thread-safe list backed by [java.util.concurrent.CopyOnWriteArrayList]. */
    fun <T> atomicListOf(): MutableList<T> = java.util.concurrent.CopyOnWriteArrayList()
}
