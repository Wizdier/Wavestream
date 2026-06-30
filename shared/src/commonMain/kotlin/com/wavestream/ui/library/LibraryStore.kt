package com.wavestream.ui.library

import androidx.compose.runtime.staticCompositionLocalOf
import com.wavestream.platform.wavePlatform
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Single library entry persisted across sessions.
 */
@Serializable
data class LibraryEntry(
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String? = null,
    val addedAt: Long = 0L,
)

/**
 * Persistence layer for the user's library / watchlist. Stored as a JSON
 * array of [LibraryEntry] under a single preferences key.
 *
 * Writes are synchronous — fine for the small list sizes we expect. A
 * production implementation would debounce writes.
 */
interface LibraryStore {
    fun load(): List<LibraryEntry>
    fun add(entry: LibraryEntry)
    fun remove(url: String)
    fun clear()
}

class DefaultLibraryStore : LibraryStore {
    private val KEY = "wavestream.library"
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs get() = wavePlatform.preferences

    override fun load(): List<LibraryEntry> = try {
        prefs.getString(KEY)?.let {
            json.decodeFromString(ListSerializer(LibraryEntry.serializer()), it)
        } ?: emptyList()
    } catch (_: Throwable) { emptyList() }

    override fun add(entry: LibraryEntry) {
        val current = load().toMutableList()
        if (current.none { it.url == entry.url }) current.add(entry)
        persist(current)
    }

    override fun remove(url: String) {
        persist(load().filterNot { it.url == url })
    }

    override fun clear() = persist(emptyList())

    private fun persist(items: List<LibraryEntry>) {
        prefs.putString(KEY, json.encodeToString(ListSerializer(LibraryEntry.serializer()), items))
    }
}

val LocalLibraryStore = staticCompositionLocalOf<LibraryStore> { DefaultLibraryStore() }
