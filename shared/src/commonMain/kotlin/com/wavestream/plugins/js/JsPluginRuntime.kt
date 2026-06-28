package com.wavestream.plugins.js

import com.wavestream.api.ExtractorLink
import com.wavestream.api.ExtractorLinkType
import com.wavestream.api.Qualities
import com.wavestream.api.SubtitleFile
import com.wavestream.core.network.NetworkClient
import com.wavestream.core.network.app
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Function

/**
 * JS plugin runtime — executes user-supplied JavaScript plugins in a Rhino sandbox.
 *
 * Mirrors NuvioMobile's `com.nuvio.app.features.plugins.runtime.PluginRuntime.kt` (272 lines),
 * but uses Mozilla Rhino (pure-Java JS engine) instead of QuickJS so it works on both
 * Android and Desktop without native libraries.
 *
 * The plugin contract is:
 *
 *   module.exports.getStreams = async function(tmdbId, mediaType, season, episode) {
 *       var res = await fetch("https://api.example.com/streams?tmdbId=" + tmdbId);
 *       var data = await res.json();
 *       return data.streams;  // [{ url: "...", title: "1080p", quality: "1080" }, ...]
 *   };
 *
 *   module.exports.onSettings = async function() {
 *       return [{ id: "apiKey", label: "API Key", type: "text" }];
 *   };
 *
 * The runtime injects polyfills for `fetch`, `URL`, `URLSearchParams`, `atob`/`btoa`,
 * `console`, `AbortController`, `TextEncoder`/`TextDecoder`.
 *
 * Native bridges:
 *   - `__native_fetch(url, method, headersJson, body, followRedirects)` → RawHttpResponse JSON
 *   - `__capture_result(jsonString)` → completes the deferred
 *   - `console.log/error/warn/info/debug` → println
 */
class JsPluginRuntime(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun executePlugin(
        code: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        scraperId: String,
        scraperSettings: Map<String, JsonElement> = emptyMap(),
    ): List<JsPluginResult> = withContext(Dispatchers.Default) {
        withTimeout(PLUGIN_TIMEOUT_MS) {
            executePluginInternal(code, tmdbId, mediaType, season, episode, scraperId, scraperSettings)
        }
    }

    private suspend fun executePluginInternal(
        code: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        scraperId: String,
        scraperSettings: Map<String, JsonElement>,
    ): List<JsPluginResult> {
        println("[JsPluginRuntime] Executing plugin '$scraperId' for tmdbId=$tmdbId type=$mediaType season=$season ep=$episode")

        val settingsJson = JsonObject(scraperSettings).toString()
        val resultDeferred = CompletableDeferred<String>()

        // Run Rhino on a background thread (it's blocking)
        withContext(Dispatchers.IO) {
            runRhino(code, settingsJson, tmdbId, mediaType, season, episode, scraperId, resultDeferred)
        }

        val rawJson = resultDeferred.await().ifBlank { "[]" }
        return parseJsonResults(rawJson, scraperId)
    }

    /**
     * Run the JS plugin in a Rhino context.
     */
    private fun runRhino(
        code: String,
        settingsJson: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        scraperId: String,
        resultDeferred: CompletableDeferred<String>,
    ): Any? {
        val cx = Context.enter()
        cx.optimizationLevel = -1  // interpreted mode (no JIT, works on Android)
        cx.languageVersion = Context.VERSION_ES6

        try {
            val scope: Scriptable = cx.initSafeStandardObjects()

            // 1. Inject polyfills + scraper id + settings
            val polyfillCode = JsBindings.buildPolyfillCode(
                scraperIdJson = JsonPrimitive(scraperId).toString(),
                settingsJson = settingsJson,
            )
            cx.evaluateString(scope, polyfillCode, "polyfills", 1, null)

            // 2. Wrap user code so it sees `module.exports`
            val wrappedCode = """
                var module = { exports: {} };
                var exports = module.exports;
                (function() {
                    $code
                })();
            """.trimIndent()
            cx.evaluateString(scope, wrappedCode, "plugin-code", 1, null)

            // 3. Register native bridges
            registerNativeBridges(cx, scope, scraperId, resultDeferred)

            // 4. Build the call code — invokes module.exports.getStreams(...)
            val seasonArg = season?.toString() ?: "undefined"
            val episodeArg = episode?.toString() ?: "undefined"
            val callCode = """
                (function() {
                    try {
                        var getStreams = module.exports.getStreams || globalThis.getStreams;
                        if (!getStreams) {
                            console.error("getStreams function not found");
                            __capture_result(JSON.stringify([]));
                            return;
                        }
                        var result = getStreams(${JsonPrimitive(tmdbId).toString()}, ${JsonPrimitive(mediaType).toString()}, $seasonArg, $episodeArg);
                        if (result && typeof result.then === 'function') {
                            result.then(function(r) {
                                __capture_result(JSON.stringify(r || []));
                            }).catch(function(e) {
                                console.error("getStreams async error:", e && e.message ? e.message : e);
                                __capture_result(JSON.stringify([]));
                            });
                        } else {
                            __capture_result(JSON.stringify(result || []));
                        }
                    } catch (e) {
                        console.error("getStreams error:", e && e.message ? e.message : e);
                        __capture_result(JSON.stringify([]));
                    }
                })();
            """.trimIndent()
            cx.evaluateString(scope, callCode, "call", 1, null)

            return null
        } catch (e: Throwable) {
            println("[JsPluginRuntime] Rhino error: ${e.message}")
            e.printStackTrace()
            resultDeferred.complete("[]")
            return null
        } finally {
            try { Context.exit() } catch (_: Throwable) {}
        }
    }

    /**
     * Register native bridge functions in the Rhino scope.
     */
    private fun registerNativeBridges(
        cx: Context,
        scope: Scriptable,
        scraperId: String,
        resultDeferred: CompletableDeferred<String>,
    ) {
        // __native_fetch(url, method, headersJson, body, followRedirects) → JSON string
        val fetchFn = object : BaseFunction() {
            override fun call(cx: Context?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>): Any {
                val url = args.getOrNull(0)?.toString() ?: ""
                val method = args.getOrNull(1)?.toString() ?: "GET"
                val headersJson = args.getOrNull(2)?.toString() ?: "{}"
                val body = args.getOrNull(3)?.toString() ?: ""
                val followRedirects = args.getOrNull(4) as? Boolean ?: true

                return kotlinx.coroutines.runBlocking {
                    performNativeFetch(url, method, headersJson, body, followRedirects)
                }
            }
        }
        scope.put("__native_fetch", scope, fetchFn)

        // __capture_result(jsonString) → completes the deferred
        val captureFn = object : BaseFunction() {
            override fun call(cx: Context?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>): Any {
                val result = args.getOrNull(0)?.toString() ?: "[]"
                resultDeferred.complete(result)
                return Context.getUndefinedValue()
            }
        }
        scope.put("__capture_result", scope, captureFn)

        // __console_log(level, message) → println
        val consoleFn = object : BaseFunction() {
            override fun call(cx: Context?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>): Any {
                val level = args.getOrNull(0)?.toString() ?: "log"
                val msg = args.drop(1).joinToString(" ") { it?.toString() ?: "null" }
                println("[Plugin:$scraperId] [$level] $msg")
                return Context.getUndefinedValue()
            }
        }
        scope.put("__console_log", scope, consoleFn)
        scope.put("__console_error", scope, consoleFn)
        scope.put("__console_warn", scope, consoleFn)
        scope.put("__console_info", scope, consoleFn)
        scope.put("__console_debug", scope, consoleFn)

        // __parse_url(url) → JSON string
        val parseUrlFn = object : BaseFunction() {
            override fun call(cx: Context?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>): Any {
                val url = args.getOrNull(0)?.toString() ?: return Context.getUndefinedValue()
                return try {
                    val parsed = java.net.URL(url)
                    val obj = buildMap {
                        put("protocol", JsonPrimitive(parsed.protocol))
                        put("host", JsonPrimitive(parsed.host))
                        put("hostname", JsonPrimitive(parsed.host.substringBefore(":")))
                        put("port", JsonPrimitive(parsed.port.takeIf { it > 0 }?.toString() ?: ""))
                        put("pathname", JsonPrimitive(parsed.path))
                        put("search", JsonPrimitive(parsed.query ?: ""))
                        put("hash", JsonPrimitive(parsed.ref ?: ""))
                    }
                    json.encodeToString(JsonObject.serializer(), JsonObject(obj))
                } catch (e: Exception) {
                    json.encodeToString(JsonObject.serializer(), JsonObject(emptyMap()))
                }
            }
        }
        scope.put("__parse_url", scope, parseUrlFn)
    }

    /**
     * Native fetch implementation — called from JS via __native_fetch.
     */
    private suspend fun performNativeFetch(
        url: String,
        method: String,
        headersJson: String,
        body: String,
        followRedirects: Boolean,
    ): String {
        return try {
            val headers = parseHeaders(headersJson)
            val response = when (method.uppercase()) {
                "GET" -> app.get(url, headers)
                "POST" -> {
                    val text = app.post(url, body, headers)
                    return makeFetchResponseJson(url, 200, "OK", text, emptyMap())
                }
                "HEAD" -> app.head(url, headers)
                else -> app.get(url, headers)  // fallback
            }
            val status = response.status
            val bodyText = response.bodyAsText()
            val responseHeaders = response.headers.entries().associate { entry ->
                entry.key to entry.value.joinToString(", ")
            }
            makeFetchResponseJson(url, status.value, status.description, bodyText, responseHeaders)
        } catch (e: Throwable) {
            makeFetchResponseJson(url, 0, e.message ?: "Fetch failed", "", emptyMap(), ok = false)
        }
    }

    private fun makeFetchResponseJson(
        url: String,
        status: Int,
        statusText: String,
        body: String,
        headers: Map<String, String>,
        ok: Boolean = status in 200..299,
    ): String {
        val map = buildMap {
            put("ok", JsonPrimitive(ok))
            put("status", JsonPrimitive(status))
            put("statusText", JsonPrimitive(statusText))
            put("url", JsonPrimitive(url))
            put("body", JsonPrimitive(body))
            put("headers", JsonObject(headers.mapValues { JsonPrimitive(it.value) }))
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(map))
    }

    private fun parseHeaders(headersJson: String): Map<String, String> {
        return runCatching {
            val obj = json.parseToJsonElement(headersJson) as? JsonObject ?: JsonObject(emptyMap())
            obj.entries.mapNotNull { (k, v) ->
                v.jsonPrimitive.contentOrNull?.let { k to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    /**
     * Parse the JSON result from `__capture_result(JSON.stringify(result || []))`.
     */
    private fun parseJsonResults(rawJson: String, scraperId: String): List<JsPluginResult> {
        return runCatching {
            val array = json.parseToJsonElement(rawJson) as? JsonArray ?: return emptyList()
            array.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val url = when (val urlValue = item["url"]) {
                    is JsonPrimitive -> urlValue.contentOrNull?.takeIf { it.isNotBlank() }
                    is JsonObject -> urlValue["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    else -> null
                } ?: return@mapNotNull null

                val headers = (item["headers"] as? JsonObject)
                    ?.mapNotNull { (key, value) ->
                        value.jsonPrimitive.contentOrNull?.let { key to it }
                    }?.toMap()?.takeIf { it.isNotEmpty() }

                val subtitles = (item["subtitles"] as? JsonArray)?.mapNotNull { subElement ->
                    val subObj = subElement as? JsonObject ?: return@mapNotNull null
                    val subUrl = subObj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val subLang = subObj["language"]?.jsonPrimitive?.contentOrNull
                        ?: subObj["lang"]?.jsonPrimitive?.contentOrNull
                        ?: "Unknown"
                    val subName = subObj["name"]?.jsonPrimitive?.contentOrNull
                        ?: subObj["label"]?.jsonPrimitive?.contentOrNull
                    val subHeaders = (subObj["headers"] as? JsonObject)
                        ?.mapNotNull { (k, v) -> v.jsonPrimitive.contentOrNull?.let { k to it } }
                        ?.toMap()?.takeIf { it.isNotEmpty() }
                    JsPluginSubtitle(url = subUrl, language = subLang, name = subName, headers = subHeaders)
                }?.takeIf { it.isNotEmpty() }

                JsPluginResult(
                    title = item.stringOrNull("title") ?: item.stringOrNull("name") ?: "Unknown",
                    name = item.stringOrNull("name"),
                    url = url,
                    quality = item.stringOrNull("quality"),
                    size = item.stringOrNull("size"),
                    language = item.stringOrNull("language"),
                    provider = item.stringOrNull("provider"),
                    type = item.stringOrNull("type"),
                    seeders = item["seeders"]?.jsonPrimitive?.intOrNull,
                    peers = item["peers"]?.jsonPrimitive?.intOrNull,
                    infoHash = item.stringOrNull("infoHash"),
                    fileIdx = item["fileIdx"]?.jsonPrimitive?.intOrNull,
                    sources = (item["sources"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    headers = headers,
                    subtitles = subtitles ?: emptyList(),
                    scraperId = scraperId,
                )
            }.filter { it.url.isNotBlank() }
        }.getOrElse { emptyList() }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && !it.contains("[object") }

    companion object {
        const val PLUGIN_TIMEOUT_MS = 60_000L
    }
}

/**
 * Result of executing a JS plugin — the playable streams it returned.
 */
data class JsPluginResult(
    val title: String,
    val name: String?,
    val url: String,
    val quality: String?,
    val size: String?,
    val language: String?,
    val provider: String?,
    val type: String?,
    val seeders: Int?,
    val peers: Int?,
    val infoHash: String?,
    val fileIdx: Int?,
    val sources: List<String>,
    val headers: Map<String, String>?,
    val subtitles: List<JsPluginSubtitle>,
    val scraperId: String,
)

data class JsPluginSubtitle(
    val url: String,
    val language: String,
    val name: String?,
    val headers: Map<String, String>?,
)
