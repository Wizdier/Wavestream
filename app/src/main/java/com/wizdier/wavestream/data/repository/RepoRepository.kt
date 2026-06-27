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
 * CloudStream 3 repos come in TWO file shapes, and each can use either an
 * inline `versions` array OR a `pluginLists` indirection:
 *
 * **Shape 1 — repo.json with `pluginLists` (modern CloudStream 3):**
 * ```
 * {
 *   "name": "My Repo",
 *   "description": "...",
 *   "manifestVersion": 1,
 *   "pluginLists": [
 *     "https://.../plugins.json"
 *   ]
 * }
 * ```
 * The actual extensions live in each `pluginLists` URL.
 *
 * **Shape 2 — repo.json with inline `versions` (classic):**
 * ```
 * {
 *   "name": "My Repo",
 *   "versions": [ { ... }, { ... } ]
 * }
 * ```
 *
 * **plugins.json itself can be either:**
 *   - A JSON array of extension objects (modern CloudStream 3 style — your repo
 *     uses this format), OR
 *   - A JSON object wrapping `versions: [...]` (older style)
 *
 * Extension objects have these fields (with multiple aliases for compat):
 *   - `name`           (required) — display name
 *   - `version`        (int or string)
 *   - `url` OR `apk`   (required) — download URL for the .cs3 or .apk file
 *   - `internalName`   — CloudStream 3 internal name (used by DexClassLoader)
 *   - `providerClass`  — classic CloudStream provider class
 *   - `description`
 *   - `authors`        (array of strings)
 *   - `tvTypes`        (array of strings: "Movie", "TvSeries", "Anime", ...)
 *   - `language`       (ISO code)
 *   - `iconUrl`        — provider icon URL
 *   - `fileSize`       — bytes
 *   - `fileHash`       — sha256-... for integrity check
 *   - `apiVersion`     — int (CloudStream 3 uses 1)
 *   - `status`         — 1 = active, 0 = disabled
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
     * URLs to gather the actual extensions.
     */
    private fun fetchManifest(url: String): RepoManifest {
        val rawJson = fetchJson(url)
        if (rawJson.trimStart().startsWith("<")) {
            throw IOException("Got HTML instead of JSON. Check the URL — it should point to the raw repo.json file.")
        }
        val rootElement = try {
            json.parseToJsonElement(rawJson)
        } catch (e: Exception) {
            throw IOException("Failed to parse repo JSON: ${e.message ?: "invalid JSON"}")
        }

        // Case 1: top-level array of extensions (some plugins.json files use this).
        if (rootElement is JsonArray) {
            val versions = parseVersionsArray(rootElement)
            return RepoManifest(
                name = "Untitled Repository",
                description = null,
                versions = versions
            )
        }

        val root = rootElement.jsonObject

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
        val pluginLists = root["pluginLists"]?.jsonArray ?: return emptyList()

        val all = mutableListOf<RepoExtension>()
        for (entry in pluginLists) {
            val pluginsUrl = (entry as? JsonPrimitive)?.contentOrNull ?: continue
            runCatching {
                val pluginsJson = fetchJson(pluginsUrl)
                val pluginsElement = json.parseToJsonElement(pluginsJson)
                // plugins.json can be either a top-level array OR an object
                // with a `versions` field — handle both.
                val versionsList = when (pluginsElement) {
                    is JsonArray -> parseVersionsArray(pluginsElement)
                    is JsonObject -> parseVersionsArray(pluginsElement["versions"] ?: JsonArray(emptyList()))
                    else -> emptyList()
                }
                all.addAll(versionsList)
            }
        }
        return all
    }

    /**
     * Parse a JSON array of extension objects into [RepoExtension]s.
     * Accepts every known field-name variation across CloudStream 3 versions.
     */
    private fun parseVersionsArray(element: JsonElement): List<RepoExtension> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null

            // Required: name + url (CloudStream 3) or apk (classic)
            val name = obj["name"]?.jsonPrimitive?.contentOrNull
                ?: obj["Name"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val downloadUrl = obj["url"]?.jsonPrimitive?.contentOrNull
                ?: obj["apk"]?.jsonPrimitive?.contentOrNull
                ?: obj["file"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null

            // Provider class — CloudStream 3 uses `internalName`, classic uses
            // `providerClass` or `class`. We accept all three.
            val providerClass = obj["internalName"]?.jsonPrimitive?.contentOrNull
                ?: obj["providerClass"]?.jsonPrimitive?.contentOrNull
                ?: obj["class"]?.jsonPrimitive?.contentOrNull
                ?: name

            // Version can be int or string in CloudStream 3.
            val version = obj["version"]?.let { v ->
                (v as? JsonPrimitive)?.contentOrNull
            } ?: "1"

            RepoExtension(
                name = name,
                version = version,
                apk = downloadUrl,
                providerClass = providerClass,
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                icon = obj["iconUrl"]?.jsonPrimitive?.contentOrNull
                    ?: obj["icon"]?.jsonPrimitive?.contentOrNull,
                tvTypes = (obj["tvTypes"] as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                },
                authors = (obj["authors"] as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                },
                language = obj["language"]?.jsonPrimitive?.contentOrNull,
                fileSize = obj["fileSize"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                fileHash = obj["fileHash"]?.jsonPrimitive?.contentOrNull,
                apiVersion = obj["apiVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                status = obj["status"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
                repositoryUrl = obj["repositoryUrl"]?.jsonPrimitive?.contentOrNull
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
    val apk: String,                  // download URL (.cs3 or .apk)
    val providerClass: String,
    val description: String? = null,
    val icon: String? = null,
    val tvTypes: List<String>? = null,
    val authors: List<String>? = null,
    // CloudStream 3-specific fields
    val language: String? = null,
    val fileSize: Long? = null,
    val fileHash: String? = null,     // sha256-... for integrity check
    val apiVersion: Int? = null,
    val status: Int = 1,              // 1 = active, 0 = disabled
    val repositoryUrl: String? = null
) {
    /** True if this is a CloudStream 3 .cs3 file (vs. classic .apk). */
    val isCs3: Boolean get() = apk.endsWith(".cs3", ignoreCase = true)

    /** File extension to display ("cs3" or "apk"). */
    val fileExtension: String get() = if (isCs3) "cs3" else "apk"
}
