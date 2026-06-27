@file:Suppress("UNUSED", "unused", "MemberVisibilityCanBePrivate")

package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import kotlinx.serialization.serializer
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
//  Locale helper (referenced by SubtitleHelper)
// ─────────────────────────────────────────────────────────────────────────────

fun getCurrentLocale(): String = Locale.getDefault().toLanguageTag()

// ─────────────────────────────────────────────────────────────────────────────
//  AppUtils — CS3 plugins call these heavily
// ─────────────────────────────────────────────────────────────────────────────

object AppUtils {
    /** Serialize any nullable value to JSON. Falls back to toString() on error. */
    fun Any?.toJson(): String = try {
        if (this == null) "null"
        else {
            @Suppress("UNCHECKED_CAST")
            val ser = kotlinx.serialization.serializer(this::class.java.kotlin)
                as kotlinx.serialization.KSerializer<Any?>
            com.lagradost.cloudstream3.json.encodeToString(ser, this)
        }
    } catch (t: Throwable) {
        this.toString()
    }

    /** Parse JSON into T, returning null on failure. */
    inline fun <reified T> tryParseJson(value: String): T? = try {
        com.lagradost.cloudstream3.json.decodeFromString(value)
    } catch (t: Throwable) { null }

    /** Parse JSON into T, throwing on failure. */
    inline fun <reified T> parseJson(value: String): T =
        com.lagradost.cloudstream3.json.decodeFromString(value)

    /** Convenience: get a Jsoup Document for [url]. */
    fun toJsoup(url: String, headers: Map<String, String> = emptyMap()): org.jsoup.nodes.Document {
        val resp = app.get(url, headers = headers)
        return try {
            org.jsoup.Jsoup.parse(resp.text, url)
        } finally {
            resp.close()
        }
    }

    /** Convenience: get a Jsoup Document for [url] with a custom User-Agent. */
    fun toJsoupUa(url: String, userAgent: String = USER_AGENT): org.jsoup.nodes.Document =
        toJsoup(url, mapOf("User-Agent" to userAgent))

    /** Try to parse an integer, returning null on failure. */
    fun parseIntSafe(value: String?): Int? = value?.toIntOrNull()

    /** Try to parse a long, returning null on failure. */
    fun parseLongSafe(value: String?): Long? = value?.toLongOrNull()
}

// ─────────────────────────────────────────────────────────────────────────────
//  SubtitleHelper — language code helpers
// ─────────────────────────────────────────────────────────────────────────────

object SubtitleHelper {
    fun fromCodeToLangTagIETF(code: String?): String? = code?.let {
        if (it.contains("-")) it else Locale.forLanguageTag(it).toLanguageTag()
    }

    fun fromLanguageToTagIETF(language: String?): String? = language?.let {
        Locale.forLanguageTag(it).toLanguageTag()
    }

    /** Get the flag emoji for a 2-letter country code. */
    fun getFlagFromIso(iso: String): String? {
        if (iso.length != 2) return null
        val flag = iso.uppercase().map { c ->
            Character.toCodePoint(c) - Character.toCodePoint('A') + 0x1F1E6
        }.map { Character.toChars(it)[0] }.joinToString("")
        return flag
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Coroutines — atomicListOf, mainWork (CS3 plugins reference these)
// ─────────────────────────────────────────────────────────────────────────────

object Coroutines {
    fun <T> atomicListOf(): MutableList<T> = java.util.concurrent.CopyOnWriteArrayList()
    fun <T> atomicListOf(vararg items: T): MutableList<T> = java.util.concurrent.CopyOnWriteArrayList(items)

    suspend fun <T> mainWork(block: suspend () -> T): T = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { block() }
    suspend fun <T> ioWork(block: suspend () -> T): T = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SingleThread — CS3 plugins sometimes use this for serializing work
// ─────────────────────────────────────────────────────────────────────────────

object SingleThread {
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    fun execute(block: () -> Unit) = executor.execute(block)
}
