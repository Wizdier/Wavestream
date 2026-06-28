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
import okhttp3.Request
import java.io.IOException

class RepoRepository(private val dao: RepoDao) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    fun observeAll(): Flow<List<RepoEntity>> = dao.observeAll()
    suspend fun getAll(): List<RepoEntity> = dao.getAll()

    suspend fun add(url: String): RepoEntity = withContext(Dispatchers.IO) {
        val trimmed = url.trim().trimEnd('/').let { if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it }
        if (dao.getByUrl(trimmed) != null) throw IllegalArgumentException("Repository already added")
        val manifest = fetchManifest(trimmed)
        val entity = RepoEntity(url = trimmed, name = manifest.name, description = manifest.description, author = manifest.resolvedAuthor, requiresApi = manifest.requiresApi ?: manifest.manifestVersion ?: 1)
        val id = dao.insert(entity); entity.copy(rowId = id)
    }
    suspend fun remove(rowId: Long) = dao.delete(rowId)
    suspend fun refresh(rowId: Long): List<RepoExtension> = withContext(Dispatchers.IO) {
        val entity = dao.getAll().firstOrNull { it.rowId == rowId } ?: throw IllegalArgumentException("Unknown repo")
        val manifest = fetchManifest(entity.url); dao.markUpdated(rowId); manifest.versions
    }

    private fun fetchManifest(url: String): RepoManifest {
        val rawJson = fetchJson(url)
        if (rawJson.trimStart().startsWith("<")) throw IOException("Got HTML instead of JSON. Use the raw repo.json URL.")
        val rootElement = try { json.parseToJsonElement(rawJson) } catch (e: Exception) { throw IOException("Failed to parse repo JSON: ${e.message}") }
        if (rootElement is JsonArray) { return RepoManifest(versions = parseVersionsArray(rootElement)) }
        val root = rootElement.jsonObject
        val name = (root["name"] as? JsonPrimitive)?.contentOrNull ?: (root["Name"] as? JsonPrimitive)?.contentOrNull ?: "Unnamed Repo"
        val description = (root["description"] as? JsonPrimitive)?.contentOrNull
        val author = (root["author"] as? JsonPrimitive)?.contentOrNull
        val authors = (root["authors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val manifestVersion = (root["manifestVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val requiresApi = (root["requiresApi"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val versions = resolveExtensions(root)
        return RepoManifest(name, description, author, authors, requiresApi, manifestVersion, versions)
    }

    private fun resolveExtensions(root: JsonObject): List<RepoExtension> {
        val inline = root["versions"]?.let { parseVersionsArray(it) }
        if (!inline.isNullOrEmpty()) return inline
        val pluginLists = root["pluginLists"]?.jsonArray ?: return emptyList()
        val all = mutableListOf<RepoExtension>()
        for (entry in pluginLists) {
            val pluginsUrl = (entry as? JsonPrimitive)?.contentOrNull ?: continue
            runCatching {
                val pluginsJson = fetchJson(pluginsUrl)
                val pluginsElement = json.parseToJsonElement(pluginsJson)
                when (pluginsElement) {
                    is JsonArray -> all.addAll(parseVersionsArray(pluginsElement))
                    is JsonObject -> all.addAll(parseVersionsArray(pluginsElement["versions"] ?: return@runCatching))
                    else -> {}
                }
            }
        }
        return all
    }

    private fun parseVersionsArray(element: JsonElement): List<RepoExtension> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val downloadUrl = (obj["url"] as? JsonPrimitive)?.contentOrNull ?: (obj["apk"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val providerClass = (obj["internalName"] as? JsonPrimitive)?.contentOrNull ?: (obj["providerClass"] as? JsonPrimitive)?.contentOrNull ?: name
            RepoExtension(name = name, version = (obj["version"] as? JsonPrimitive)?.contentOrNull ?: "1", apk = downloadUrl, providerClass = providerClass, description = (obj["description"] as? JsonPrimitive)?.contentOrNull, icon = (obj["iconUrl"] as? JsonPrimitive)?.contentOrNull, tvTypes = (obj["tvTypes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }, authors = (obj["authors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }, language = (obj["language"] as? JsonPrimitive)?.contentOrNull, fileSize = (obj["fileSize"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull(), fileHash = (obj["fileHash"] as? JsonPrimitive)?.contentOrNull, apiVersion = (obj["apiVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(), status = (obj["status"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1, repositoryUrl = (obj["repositoryUrl"] as? JsonPrimitive)?.contentOrNull)
        }
    }

    private fun fetchJson(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 11) CloudStream/3.2.0 WaveStream/1.0").header("Accept", "application/json, text/plain, */*").build()
        NetworkModule.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} ${resp.message}")
            return resp.body?.string() ?: throw IOException("Empty body")
        }
    }
}

@Serializable data class RepoManifest(val name: String = "Unnamed Repo", val description: String? = null, val author: String? = null, val authors: List<String>? = null, val requiresApi: Int? = null, val manifestVersion: Int? = null, val versions: List<RepoExtension> = emptyList()) { val resolvedAuthor: String? get() = author ?: authors?.joinToString(", ")?.takeIf { it.isNotBlank() } }
@Serializable data class RepoExtension(val name: String, val version: String = "1.0.0", val apk: String, val providerClass: String, val description: String? = null, val icon: String? = null, val tvTypes: List<String>? = null, val authors: List<String>? = null, val language: String? = null, val fileSize: Long? = null, val fileHash: String? = null, val apiVersion: Int? = null, val status: Int = 1, val repositoryUrl: String? = null)
