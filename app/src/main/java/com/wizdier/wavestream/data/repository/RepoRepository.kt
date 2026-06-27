package com.wizdier.wavestream.data.repository

import com.wizdier.wavestream.data.db.dao.RepoDao
import com.wizdier.wavestream.data.db.entities.RepoEntity
import com.wizdier.wavestream.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.IOException

/**
 * Multi-repo support — CloudStream's killer feature, ported to WaveStream.
 *
 * A "repo" is a JSON manifest hosted anywhere (GitHub raw, jsdelivr, Codeberg,
 * GitLab, …) describing a list of provider extension APKs the user can install.
 *
 * CloudStream repos come in TWO shapes that we must both handle:
 *
 * **Modern CloudStream (pluginLists — most common):**
 * ```
 * {
 *   "name": "Cloudstream providers repository",
 *   "description": "...",
 *   "manifestVersion": 1,
 *   "pluginLists": [
 *     "https://raw.githubusercontent.com/.../plugins.json"
 *   ]
 * }
 * ```
 * The actual extensions live in each `pluginLists` URL, which is itself a
 * JSON file with the same shape as the classic repo (see below). We have to
 * fetch each plugins.json URL recursively.
 *
 * **Classic CloudStream (versions inline — older format):**
 * ```
 * {
 *   "name": "My Repo",
 *   "tvTypes": ["TvSeries", "Movie"],
 *   "authors": ["..."],
 *   "versions": [
 *     { "name": "X", "version": "1.0", "apk": "url", "providerClass": "com.x.Y" }
 *   ]
 * }
 * ```
 */
class RepoRepository(private val dao: RepoDao) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun observeAll(): Flow<List<RepoEntity>> = dao.observeAll()

    suspend fun getAll(): List<RepoEntity> = dao.getAll()

    suspend fun add(url: String): RepoEntity = withContext(Dispatchers.IO) {
        val trimmed = url.trim().trimEnd('/').let {
            // Auto-https if no scheme given.
            if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
        }
        if (dao.getByUrl(trimmed) != null) {
            throw IllegalArgumentException("Repository already added")
        }
        val manifest = fetchManifest(trimmed)
        val entity = RepoEntity(
            url = trimmed,
            name = manifest.name,
            description = manifest.description,
            author = manifest.resolvedAuthor,
            requiresApi = manifest.requiresApi ?: manifest.manifestVersion ?: 1
        )
        val id = dao.insert(entity)
        entity.copy(rowId = id)
    }

    suspend fun remove(rowId: Long) = dao.delete(rowId)

    suspend fun refresh(rowId: Long): List<RepoExtension> = withContext(Dispatchers.IO) {
        val entity = dao.getAll().firstOrNull { it.rowId == rowId }
            ?: throw IllegalArgumentException("Unknown repo")
        val manifest = fetchManifest(entity.url)
        dao.markUpdated(rowId)
        manifest.versions
    }

    suspend fun listExtensions(rowId: Long): List<RepoExtension> = withContext(Dispatchers.IO) {
        val entity = dao.getAll().firstOrNull { it.rowId == rowId } ?: return@withContext emptyList()
        fetchManifest(entity.url).versions
    }

    /**
     * Fetch and parse the repo manifest, recursively resolving `pluginLists`
     * URLs to gather the actual extensions. CloudStream's modern repo format
     * delegates to one or more `plugins.json` files via `pluginLists`.
     */
    private fun fetchManifest(url: String): RepoManifest {
        val rawJson = fetchJson(url)
        // Some repos wrap the manifest in a redirect or HTML page — bail early.
        if (rawJson.trimStart().startsWith("<")) {
            throw IOException("Got HTML instead of JSON. Check the URL — it should point to the raw repo.json file.")
        }
        val root: JsonObject = try {
            json.parseToJsonElement(rawJson).jsonObject
        } catch (e: Exception) {
            throw IOException("Failed to parse repo JSON: ${e.message ?: "invalid JSON"}")
        }

        // Extract top-level fields.
        val name = root["name"]?.jsonPrimitive?.contentOrNull
            ?: root["Name"]?.jsonPrimitive?.contentOrNull
            ?: "Unnamed Repo"
        val description = root["description"]?.jsonPrimitive?.contentOrNull
            ?: root["Description"]?.jsonPrimitive?.contentOrNull
        val author = root["author"]?.jsonPrimitive?.contentOrNull
        val authors = root["authors"]?.jsonArray?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        }
        val manifestVersion = root["manifestVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val requiresApi = root["requiresApi"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

        // Resolve the actual extension list. CloudStream repos use one of:
        //   a) `versions` array (classic, inline)
        //   b) `pluginLists` array of URLs (modern, indirect — we fetch each)
        val versions = resolveExtensions(root, url)

        return RepoManifest(
            name = name,
            description = description,
            author = author,
            authors = authors,
            requiresApi = requiresApi,
            manifestVersion = manifestVersion,
            versions = versions
        )
    }

    /**
     * Pull the extension list out of the manifest. Modern CloudStream repos
     * use `pluginLists` (an array of URLs to plugins.json files). Classic
     * repos use `versions` directly.
     */
    private fun resolveExtensions(root: JsonObject, repoUrl: String): List<RepoExtension> {
        // Case A: classic inline versions array.
        val inlineVersions = root["versions"]?.let { parseVersionsArray(it) }
        if (!inlineVersions.isNullOrEmpty()) return inlineVersions

        // Case B: modern pluginLists — fetch each one and merge.
        val pluginLists = root["pluginLists"]?.jsonArray
            ?: return emptyList()

        val all = mutableListOf<RepoExtension>()
        for (entry in pluginLists) {
            val pluginsUrl = (entry as? JsonPrimitive)?.contentOrNull ?: continue
            runCatching {
                val pluginsJson = fetchJson(pluginsUrl)
                val pluginsRoot = json.parseToJsonElement(pluginsJson).jsonObject
                val versionsElement = pluginsRoot["versions"]
                if (versionsElement != null) {
                    val list = parseVersionsArray(versionsElement)
                    all.addAll(list)
                }
            }
        }
        return all
    }

    private fun parseVersionsArray(element: JsonElement): List<RepoExtension> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val apk = obj["apk"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val providerClass = obj["providerClass"]?.jsonPrimitive?.contentOrNull
                ?: obj["class"]?.jsonPrimitive?.contentOrNull
                ?: ""
            RepoExtension(
                name = name,
                version = obj["version"]?.jsonPrimitive?.contentOrNull ?: "1.0.0",
                apk = apk,
                providerClass = providerClass,
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                icon = obj["icon"]?.jsonPrimitive?.contentOrNull,
                tvTypes = (obj["tvTypes"] as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                },
                authors = (obj["authors"] as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                }
            )
        }
    }

    private fun fetchJson(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", CloudStreamUserAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        NetworkModule.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} ${resp.message}")
            }
            val body = resp.body?.string()
                ?: throw IOException("Empty response body from $url")
            if (body.isBlank()) throw IOException("Empty body from $url")
            return body
        }
    }

    companion object {
        // Mimic CloudStream's user agent so community repos that filter by UA accept us.
        const val CloudStreamUserAgent =
            "Mozilla/5.0 (Linux; Android 11) CloudStream/3.2.0 WaveStream/1.0"
    }
}

@Serializable
data class RepoManifest(
    val name: String = "Unnamed Repo",
    val description: String? = null,
    val author: String? = null,
    val authors: List<String>? = null,
    val requiresApi: Int? = null,
    val manifestVersion: Int? = null,
    val versions: List<RepoExtension> = emptyList()
) {
    val resolvedAuthor: String? get() = author ?: authors?.joinToString(", ")?.takeIf { it.isNotBlank() }
}

@Serializable
data class RepoExtension(
    val name: String,
    val version: String = "1.0.0",
    val apk: String,
    val providerClass: String,
    val description: String? = null,
    val icon: String? = null,
    val tvTypes: List<String>? = null,
    val authors: List<String>? = null
)
