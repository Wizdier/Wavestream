package com.wavestream.plugins.stremio

import com.wavestream.core.network.app
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * Stremio addon URL builder — mirrors NuvioMobile's `AddonTransportUrls.kt`.
 *
 * A Stremio addon's transport URL is the manifest URL:
 *   https://example.com/manifest.json
 *   https://example.com/manifest.json?api_key=xxx
 *
 * Resource URLs are built as:
 *   ${baseUrl}/catalog/${type}/${id}.json[?page=N][&extra=...]
 *   ${baseUrl}/meta/${type}/${id}.json
 *   ${baseUrl}/stream/${type}/${videoId}.json
 *
 * Where baseUrl = manifestUrl minus "/manifest.json" suffix and minus any query string
 * (but the query string is re-appended to resource URLs for api_key propagation).
 */
internal fun addonTransportBaseUrl(manifestUrl: String): String =
    manifestUrl.substringBefore("?").removeSuffix("/manifest.json")

internal fun buildAddonResourceUrl(
    manifestUrl: String,
    resource: String,
    type: String,
    id: String,
    extraPathSegment: String? = null,
): String {
    val encodedId = id.encodeAddonPathSegment()
    val baseUrl = addonTransportBaseUrl(manifestUrl)
    val query = manifestUrl.substringAfter("?", "").let { if (it.isBlank()) "" else "?$it" }
    val resourceUrl = if (extraPathSegment.isNullOrEmpty()) {
        "$baseUrl/$resource/$type/$encodedId.json"
    } else {
        "$baseUrl/$resource/$type/$encodedId/$extraPathSegment.json"
    }
    return resourceUrl + query
}

/**
 * URL-encode a path segment per RFC 3986 unreserved characters.
 */
internal fun String.encodeAddonPathSegment(): String = buildString {
    toByteArray().forEach { byte ->
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' ||
            char == '-' || char == '_' || char == '.' || char == '~'
        ) {
            append(char)
        } else {
            append('%')
            append(HEX[value shr 4])
            append(HEX[value and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"

/**
 * Stremio addon client — fetches manifest/catalog/meta/stream from an addon.
 *
 * Mirrors the inline calls scattered throughout NuvioMobile's AddonRepository, StreamsRepository,
 * CatalogRepository, MetaDetailsRepository — collected here into one client.
 */
class StremioAddonClient(
    val manifestUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * Fetch the manifest JSON from `${baseUrl}/manifest.json`.
     */
    suspend fun getManifest(): AddonManifest {
        val payload = app.get(manifestUrl).bodyAsText()
        val manifest = json.decodeFromString<AddonManifest>(payload)
        return manifest.copy(logoUrl = manifest.logoUrl?.resolveAgainstManifest(manifestUrl)).apply {
            transportUrl = manifestUrl
        }
    }

    /**
     * Fetch catalog items: GET ${baseUrl}/catalog/${type}/${id}.json?page=N&genre=...
     */
    suspend fun getCatalog(
        type: String,
        id: String,
        page: Int? = null,
        extra: Map<String, String> = emptyMap(),
    ): List<StremioMetaPreview> {
        val url = buildAddonResourceUrl(manifestUrl, "catalog", type, id)
        val fullUrl = buildString {
            append(url)
            val sep = if (url.contains("?")) "&" else "?"
            page?.let { append("${sep}page=$it") }
            extra.forEach { (k, v) ->
                append(if (endsWith("?") || contains("?")) "&" else "?")
                append("${k.encodeAddonPathSegment()}=${v.encodeAddonPathSegment()}")
            }
        }
        val payload = app.get(fullUrl).bodyAsText()
        return json.decodeFromString<StremioCatalogResponse>(payload).metas
    }

    /**
     * Fetch metadata: GET ${baseUrl}/meta/${type}/${id}.json
     */
    suspend fun getMeta(type: String, id: String): StremioMeta {
        val url = buildAddonResourceUrl(manifestUrl, "meta", type, id)
        val payload = app.get(url).bodyAsText()
        return json.decodeFromString<StremioMetaResponse>(payload).meta
    }

    /**
     * Fetch streams: GET ${baseUrl}/stream/${type}/${videoId}.json
     */
    suspend fun getStreams(type: String, videoId: String): List<StremioStream> {
        val url = buildAddonResourceUrl(manifestUrl, "stream", type, videoId)
        val payload = app.get(url).bodyAsText()
        return json.decodeFromString<StremioStreamsResponse>(payload).streams
    }
}

private fun String.resolveAgainstManifest(manifestUrl: String): String = when {
    startsWith("http://") || startsWith("https://") || startsWith("data:") -> this
    startsWith("//") -> "https:$this"
    else -> {
        val manifestBase = manifestUrl.substringBefore("?").substringBeforeLast('/', "")
        if (startsWith('/')) {
            val origin = manifestBase.substringBefore("/", missingDelimiterValue = manifestBase)
            val schemeAndHost = if (origin.contains("://")) {
                val scheme = origin.substringBefore("://")
                val host = manifestBase.substringAfter("://").substringBefore("/")
                "$scheme://$host"
            } else {
                manifestBase
            }
            "$schemeAndHost$this"
        } else {
            "$manifestBase/$this"
        }
    }
}
