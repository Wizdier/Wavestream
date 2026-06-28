@file:Suppress("UNUSED", "unused", "MemberVisibilityCanBePrivate")
package com.lagradost.cloudstream3.utils
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import java.util.Locale
fun getCurrentLocale(): String = Locale.getDefault().toLanguageTag()
object AppUtils {
    fun Any?.toJson(): String = this?.toString() ?: "null"
    inline fun <reified T> tryParseJson(value: String): T? = try { com.lagradost.cloudstream3.json.decodeFromString(value) } catch (t: Throwable) { null }
    fun toJsoup(url: String, headers: Map<String, String> = emptyMap()): org.jsoup.nodes.Document {
        val resp = app.get(url, headers = headers)
        return try { org.jsoup.Jsoup.parse(resp.text, url) } finally { resp.close() }
    }
    fun parseIntSafe(value: String?): Int? = value?.toIntOrNull()
    fun parseLongSafe(value: String?): Long? = value?.toLongOrNull()
}
object SubtitleHelper {
    fun fromCodeToLangTagIETF(code: String?): String? = code
    fun fromLanguageToTagIETF(language: String?): String? = language
    fun getFlagFromIso(iso: String): String? {
        if (iso.length != 2) return null
        return iso.uppercase().map { c -> c.code - 'A'.code + 0x1F1E6 }.map { Character.toChars(it)[0] }.joinToString("")
    }
}
