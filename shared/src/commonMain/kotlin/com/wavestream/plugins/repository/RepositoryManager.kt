package com.wavestream.plugins.repository

import com.wavestream.core.network.app
import com.wavestream.core.storage.DataStore
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Repository Manager — handles CloudStream repository JSON files.
 *
 * A CS repository is a JSON file at a URL with this structure:
 * {
 *   "name": "Repo Name",
 *   "description": "...",
 *   "manifestVersion": 1,
 *   "pluginLists": ["https://.../plugins.json"]
 * }
 *
 * Each pluginList URL returns an array of SitePlugin objects:
 * {
 *   "url": "https://.../PluginName.cs3",
 *   "name": "Plugin Name",
 *   "internalName": "PluginName",
 *   "version": 1,
 *   "status": 1,
 *   "language": "en",
 *   "fileHash": "sha256-..."
 * }
 *
 * Repo URL formats supported (mirrors CloudStream's RepositoryManager.parseRepoUrl):
 *   - https://example.com/repo.json
 *   - cloudstreamrepo://example.com/repo.json
 *   - https://cs.repo/?example.com/repo.json
 *   - https://cs.repo/example.com/repo.json
 */
@Serializable
data class RepositoryData(
    val url: String,
    val name: String = "",
)

@Serializable
data class Repository(
    val name: String = "",
    val description: String? = null,
    val manifestVersion: Int = 1,
    val pluginLists: List<String> = emptyList(),
    val iconUrl: String? = null,
)

@Serializable
data class SitePlugin(
    val url: String = "",                  // .cs3 file URL (Android DEX format)
    val jarUrl: String? = null,            // .jar file URL (Desktop JVM format) — optional
    val name: String = "",
    val internalName: String = "",
    val version: Int = 1,
    val status: Int = 1,
    val language: String? = null,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val tvTypes: List<String>? = null,
    val iconUrl: String? = null,
    val repositoryUrl: String? = null,
    val apiVersion: Int = 1,
    val fileSize: Long? = null,
    val fileHash: String? = null,
    val jarHash: String? = null,
    val jarFileSize: Long? = null,
) {
    /** The best URL for the current platform — .jar for Desktop, .cs3 for Android. */
    fun bestUrlForPlatform(): String = when {
        isDesktopPlatform() && !jarUrl.isNullOrBlank() -> jarUrl!!
        else -> url
    }

    /** The matching hash for the chosen URL. */
    fun bestHashForPlatform(): String? = when {
        isDesktopPlatform() && !jarUrl.isNullOrBlank() -> jarHash
        else -> fileHash
    }
}

/** Returns true if running on JVM desktop (not Android). */
expect fun isDesktopPlatform(): Boolean

private const val REPOS_KEY = "cs_repositories_v3"
private val repoSerializer = RepositoryData.serializer()
private val repoListSerializer = ListSerializer(repoSerializer)

object RepositoryManager {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        isLenient = true
    }

    /** Live in-memory cache of plugins per repo URL. */
    private val _repoPlugins = MutableStateFlow<Map<String, List<SitePlugin>>>(emptyMap())
    val repoPlugins: StateFlow<Map<String, List<SitePlugin>>> = _repoPlugins.asStateFlow()

    private val GH_REGEX = Regex("^https://raw\\.githubusercontent\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)/(.*)$")

    /** Convert raw.githubusercontent.com URLs to cdn.jsdelivr.net (faster CDN). */
    fun convertRawGitUrl(url: String): String {
        val match = GH_REGEX.find(url) ?: return url
        val (user, repo, rest) = match.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
    }

    /**
     * Normalize a user-provided repo URL. Handles:
     *   - "https://..." → unchanged
     *   - "cloudstreamrepo://example.com/repo.json" → "https://example.com/repo.json"
     *   - "https://cs.repo/?example.com/repo.json" or "https://cs.repo/example.com/repo.json" → "https://example.com/repo.json"
     *   - "https://cs.repo" alone → unchanged (lets user use cs.repo page)
     */
    fun parseRepoUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> {
                // Strip cs.repo prefix if present
                if (trimmed.contains("cs.repo/")) {
                    val after = trimmed.substringAfter("cs.repo/")
                    val cleaned = after.removePrefix("?")
                    if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) cleaned
                    else "https://$cleaned"
                } else trimmed
            }
            trimmed.startsWith("cloudstreamrepo://") -> {
                val rest = trimmed.removePrefix("cloudstreamrepo://")
                if (rest.startsWith("http://") || rest.startsWith("https://")) rest else "https://$rest"
            }
            trimmed.matches(Regex("^[a-zA-Z0-9!_-]+$")) -> {
                // Treat as shortlink — try cutt.ly resolution (best-effort, returns input on failure)
                trimmed
            }
            else -> trimmed
        }
    }

    /** Fetch + parse a repository JSON. Returns null on failure. */
    suspend fun parseRepository(rawUrl: String): Repository? {
        val url = parseRepoUrl(rawUrl)
        return runCatching {
            val response = app.get(convertRawGitUrl(url))
            if (!response.status.isSuccess()) {
                println("[RepositoryManager] Failed to fetch repo $url: HTTP ${response.status.value}")
                return null
            }
            val text = response.bodyAsText()
            json.decodeFromString<Repository>(text)
        }.getOrElse {
            println("[RepositoryManager] Failed to parse repo $url: ${it.message}")
            null
        }
    }

    /** Fetch all plugins from a repository. Returns null if repo can't be parsed. */
    suspend fun getRepoPlugins(rawUrl: String): List<SitePlugin>? {
        val url = parseRepoUrl(rawUrl)
        val repo = parseRepository(url) ?: return null
        val allPlugins = mutableListOf<SitePlugin>()
        for (pluginListUrl in repo.pluginLists) {
            try {
                val response = app.get(convertRawGitUrl(pluginListUrl))
                if (!response.status.isSuccess()) continue
                val text = response.bodyAsText()
                val plugins = runCatching {
                    json.decodeFromString<Array<SitePlugin>>(text)
                }.getOrNull()?.toList() ?: emptyList()
                allPlugins.addAll(plugins)
            } catch (e: Throwable) {
                println("[RepositoryManager] Failed to fetch plugin list $pluginListUrl: ${e.message}")
            }
        }
        // Cache result for UI
        val current = _repoPlugins.value.toMutableMap()
        current[url] = allPlugins
        _repoPlugins.value = current
        return allPlugins
    }

    /** Download a .cs3 plugin file to disk. Verifies SHA-256 hash if provided. */
    suspend fun downloadPlugin(pluginUrl: String, targetFile: File, expectedHash: String? = null): File? {
        return runCatching {
            targetFile.parentFile?.mkdirs()
            val response = app.get(convertRawGitUrl(pluginUrl))
            if (!response.status.isSuccess()) {
                println("[RepositoryManager] Failed to download $pluginUrl: HTTP ${response.status.value}")
                return null
            }

            val bytes = response.readRawBytes()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tempFile.writeBytes(bytes)

            if (expectedHash != null) {
                val actualHash = sha256(tempFile)
                if (actualHash != expectedHash) {
                    println("[RepositoryManager] Hash mismatch for $pluginUrl: expected=$expectedHash actual=$actualHash")
                    tempFile.delete()
                    return null
                }
            }

            try {
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            targetFile
        }.getOrElse {
            println("[RepositoryManager] Error downloading $pluginUrl: ${it.message}")
            null
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read = fis.read(buffer)
            while (read != -1) { digest.update(buffer, 0, read); read = fis.read(buffer) }
        }
        return "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Sanitized file name for a plugin (internalName + hash) with appropriate extension. */
    fun getPluginFileName(internalName: String, useJar: Boolean = isDesktopPlatform()): String {
        val sanitized = sanitizeFilename(internalName, true)
        val ext = if (useJar) "jar" else "cs3"
        return "${sanitized}_${internalName.hashCode().toUInt()}.$ext"
    }

    /** Sanitized folder name for a repository (based on repo URL). */
    fun getRepoFolderName(repoUrl: String): String {
        val sanitized = sanitizeFilename(repoUrl, true)
        return "${sanitized}_${repoUrl.hashCode().toUInt()}"
    }

    private fun sanitizeFilename(name: String, validateEmpty: Boolean = false): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank {
            if (validateEmpty) "unnamed" else ""
        }
        return cleaned.take(120) // cap length to avoid filesystem limits
    }

    // ========================================================================
    // Repository storage (CRUD)
    // ========================================================================

    fun getRepositories(): List<RepositoryData> {
        return DataStore.getSerializedList(REPOS_KEY, repoSerializer) ?: emptyList()
    }

    fun addRepository(url: String, name: String = ""): Boolean {
        val normalized = parseRepoUrl(url)
        val current = getRepositories().toMutableList()
        if (current.any { it.url == normalized }) return false
        current.add(RepositoryData(normalized, name))
        DataStore.setSerializedList(REPOS_KEY, current, repoSerializer)
        return true
    }

    fun removeRepository(url: String) {
        val normalized = parseRepoUrl(url)
        val current = getRepositories().filter { it.url != normalized }
        DataStore.setSerializedList(REPOS_KEY, current, repoSerializer)
        // Remove from cache
        val cache = _repoPlugins.value.toMutableMap()
        cache.remove(normalized)
        _repoPlugins.value = cache
    }

    fun renameRepository(url: String, newName: String) {
        val normalized = parseRepoUrl(url)
        val current = getRepositories().map {
            if (it.url == normalized) it.copy(name = newName) else it
        }
        DataStore.setSerializedList(REPOS_KEY, current, repoSerializer)
    }
}
