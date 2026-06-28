package com.wavestream.plugins.repository

import com.wavestream.core.network.app
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Repository manager — mirrors CloudStream's `com.lagradost.cloudstream3.plugins.RepositoryManager`.
 *
 * A Wavestream repository is a JSON file at a public URL:
 *   {
 *     "name": "My Repo",
 *     "description": "...",
 *     "manifestVersion": 1,
 *     "pluginLists": ["https://.../plugins.json"]
 *   }
 *
 * `plugins.json` is an array of SitePlugin:
 *   {
 *     "url": "https://.../MyProvider.ws3",
 *     "status": 1,                      // 0=down, 1=ok, 2=slow, 3=beta-only
 *     "version": 4,                      // any change triggers auto-update
 *     "apiVersion": 1,
 *     "name": "My Provider",
 *     "internalName": "MyProvider",
 *     "authors": ["..."],
 *     "description": "...",
 *     "repositoryUrl": "https://...",
 *     "tvTypes": ["Movie"],
 *     "language": "en",
 *     "iconUrl": "...",
 *     "fileSize": 12345,
 *     "fileHash": "sha256-..."           // validated after download
 *   }
 */
object RepositoryManager {
    const val ONLINE_PLUGINS_FOLDER = "Extensions"

    val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Repository(
        val name: String,
        val description: String? = null,
        val manifestVersion: Int = 1,
        val pluginLists: List<String> = emptyList(),
    )

    @Serializable
    data class SitePlugin(
        val url: String,
        val status: Int = 1,             // 0=down, 1=ok, 2=slow, 3=beta-only
        val version: Int = 1,
        val apiVersion: Int = 1,
        val name: String,
        val internalName: String,
        val authors: List<String> = emptyList(),
        val description: String? = null,
        val repositoryUrl: String? = null,
        val tvTypes: List<String>? = null,
        val language: String? = null,
        val iconUrl: String? = null,
        val fileSize: Long? = null,
        val fileHash: String? = null,
    )

    /**
     * Convert raw.githubusercontent.com URLs to cdn.jsdelivr.net if enabled.
     */
    fun convertRawGitUrl(url: String): String {
        // Could check a setting: if (DataStore.containsKey("use_jsdelivr_proxy")) ...
        val regex = Regex("^https://raw\\.githubusercontent\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)/(.*)$")
        val match = regex.find(url) ?: return url
        val (user, repo, rest) = match.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
    }

    /**
     * Fetch and parse a repository manifest.
     */
    suspend fun parseRepository(url: String): Repository? {
        return runCatching {
            val response = app.get(convertRawGitUrl(url))
            if (!response.status.isSuccess()) return null
            json.decodeFromString<Repository>(response.bodyAsText())
        }.getOrNull()
    }

    /**
     * Fetch the plugin list from a repository.
     */
    suspend fun getRepoPlugins(repoUrl: String): List<Pair<String, SitePlugin>>? {
        val repo = parseRepository(repoUrl) ?: return null
        return repo.pluginLists.flatMap { pluginListUrl ->
            val response = app.get(convertRawGitUrl(pluginListUrl))
            if (!response.status.isSuccess()) return@flatMap emptyList()
            val plugins = json.decodeFromString<Array<SitePlugin>>(response.bodyAsText())
            plugins.map { repoUrl to it }
        }
    }

    /**
     * Download a plugin file to a local path, optionally verifying SHA-256 hash.
     */
    suspend fun downloadPluginToFile(
        pluginUrl: String,
        targetFile: File,
        expectedHash: String? = null,
    ): File? {
        return runCatching {
            targetFile.parentFile?.mkdirs()
            val response = app.get(convertRawGitUrl(pluginUrl))
            if (!response.status.isSuccess()) return null

            val bytes = response.readRawBytes()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tempFile.writeBytes(bytes)

            if (expectedHash != null) {
                val actualHash = sha256(tempFile)
                if (actualHash != expectedHash) {
                    tempFile.delete()
                    throw IllegalStateException(
                        "Plugin hash mismatch for ${targetFile.name}: expected=$expectedHash, got=$actualHash"
                    )
                }
            }

            // Atomic move
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            targetFile
        }.getOrNull()
    }

    /**
     * Compute SHA-256 of a file, returned as "sha256-..." prefix.
     */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read = fis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Get a sanitized filename for a plugin.
     */
    fun getPluginSanitizedFileName(name: String): String {
        val sanitized = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "$sanitized.${name.hashCode()}"
    }
}
