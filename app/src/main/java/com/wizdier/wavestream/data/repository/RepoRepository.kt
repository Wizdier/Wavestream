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
 * Repo JSON shape:
 * ```
 * {
 *   "name": "My WaveStream Repo",
 *   "description": "...",
 *   "author": "...",
 *   "requiresApi": 1,
 *   "versions": [
 *     {
 *       "name": "ExampleProvider",
 *       "version": "1.0.0",
 *       "apk": "https://example.com/exampleprovider-1.0.0.apk",
 *       "providerClass": "com.example.provider.ExampleProvider",
 *       "description": "..."
 *     }
 *   ]
 * }
 * ```
 */
class RepoRepository(private val dao: RepoDao) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<RepoEntity>> = dao.observeAll()

    suspend fun getAll(): List<RepoEntity> = dao.getAll()

    suspend fun add(url: String): RepoEntity = withContext(Dispatchers.IO) {
        if (dao.getByUrl(url) != null) throw IllegalArgumentException("Repository already added")
        val manifest = fetchManifest(url)
        val entity = RepoEntity(
            url = url,
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
        val req = Request.Builder().url(url).build()
        NetworkModule.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Repo fetch failed: ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("Empty repo body")
            return json.decodeFromString(RepoManifest.serializer(), body)
        }
    }
}

@Serializable
data class RepoManifest(
    val name: String,
    val description: String? = null,
    val author: String? = null,
    val requiresApi: Int? = null,
    val versions: List<RepoExtension> = emptyList()
)

@Serializable
data class RepoExtension(
    val name: String,
    val version: String,
    val apk: String,
    val providerClass: String,
    val description: String? = null,
    val icon: String? = null
)
