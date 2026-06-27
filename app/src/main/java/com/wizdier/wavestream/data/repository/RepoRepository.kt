package com.wizdier.wavestream.data.repository

import com.wizdier.wavestream.data.db.dao.RepoDao
import com.wizdier.wavestream.data.db.entities.RepoEntity
import com.wizdier.wavestream.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.IOException

/**
 * Multi-repo support — CloudStream's killer feature, ported to WaveStream.
 *
 * A "repo" is a JSON manifest hosted anywhere (GitHub raw, jsdelivr, a static
 * server, …) describing a list of provider extension APKs the user can
 * install. The format is intentionally compatible with CloudStream's
 * `repository.json` shape so existing community repos can be added as-is.
 *
 * Supported CloudStream-compatible repo JSON shapes (we try both):
 *
 * **WaveStream / modern CloudStream:**
 * ```
 * {
 *   "name": "My Repo",
 *   "description": "...",
 *   "author": "...",
 *   "requiresApi": 1,
 *   "versions": [
 *     { "name": "X", "version": "1.0", "apk": "url", "providerClass": "com.x.Y", "description": "..." }
 *   ]
 * }
 * ```
 *
 * **Classic CloudStream (also accepted — we adapt):**
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
            author = manifest.author,
            requiresApi = manifest.requiresApi ?: 1
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

    private fun fetchManifest(url: String): RepoManifest {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", CloudStreamUserAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        NetworkModule.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Repo fetch failed: HTTP ${resp.code} ${resp.message}")
            }
            val body = resp.body?.string()
                ?: throw IOException("Empty response body from $url")
            if (body.isBlank()) throw IOException("Empty body from $url")
            // Some repos wrap the manifest in a redirect or HTML page — bail early.
            if (body.trimStart().startsWith("<")) {
                throw IOException("Got HTML instead of JSON. Check the URL — it should point to the raw repo.json file.")
            }
            return try {
                json.decodeFromString(RepoManifest.serializer(), body)
            } catch (e: Exception) {
                throw IOException("Failed to parse repo JSON: ${e.message ?: "invalid JSON"}")
            }
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
    // CloudStream classic uses "authors" as a list — accept both.
    val authors: List<String>? = null,
    val requiresApi: Int? = null,
    val versions: List<RepoExtension> = emptyList()
) {
    /** Resolve the author field from either `author` or `authors[0]`. */
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
    // CloudStream classic fields we accept but don't use yet.
    val tvTypes: List<String>? = null,
    val authors: List<String>? = null
)
