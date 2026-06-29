package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
 * Each pluginList URL returns an array of SitePlugin objects.
 *
 * Repo URL formats supported (mirrors CloudStream's parseRepoUrl):
 *   - https://example.com/repo.json
 *   - cloudstreamrepo://example.com/repo.json
 *   - https://cs.repo/?example.com/repo.json
 */
@Serializable
data class Repository(
    @SerialName("iconUrl") val iconUrl: String? = null,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("manifestVersion") val manifestVersion: Int = 1,
    @SerialName("pluginLists") val pluginLists: List<String> = emptyList(),
)

/**
 * Status int as the following:
 * 0: Down
 * 1: Ok
 * 2: Slow
 * 3: Beta only
 */
@Serializable
data class SitePlugin(
    @SerialName("url") val url: String = "",
    @SerialName("status") val status: Int = 1,
    @SerialName("version") val version: Int = 1,
    @SerialName("apiVersion") val apiVersion: Int = 1,
    @SerialName("name") val name: String = "",
    @SerialName("internalName") val internalName: String = "",
    @SerialName("authors") val authors: List<String> = emptyList(),
    @SerialName("description") val description: String? = null,
    @SerialName("repositoryUrl") val repositoryUrl: String? = null,
    @SerialName("tvTypes") val tvTypes: List<String>? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("iconUrl") val iconUrl: String? = null,
    @SerialName("fileSize") val fileSize: Long? = null,
    @SerialName("fileHash") val fileHash: String? = null,
    // Desktop-specific: CloudStream's repo provides both .cs3 (Android) and .jar (Desktop) URLs
    @SerialName("jarUrl") val jarUrl: String? = null,
    @SerialName("jarHash") val jarHash: String? = null,
    @SerialName("jarFileSize") val jarFileSize: Long? = null,
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

/**
 * Stored repository entry — used by Settings -> Extensions.
 */
@Serializable
data class RepositoryData(
    val url: String,
    val name: String = "",
)

object RepositoryManager {
    const val ONLINE_PLUGINS_FOLDER = "Extensions"
    private val json = Json { ignoreUnknownKeys = true }

    private val GH_REGEX =
        Regex("^https://raw\\.githubusercontent\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)/(.*)$")

    /** Returns a SHA-256 string of the file content. */
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

    /** Convert raw.githubusercontent.com URLs to cdn.jsdelivr.net for faster CDN. */
    fun convertRawGitUrl(url: String): String {
        val match = GH_REGEX.find(url) ?: return url
        val (user, repo, rest) = match.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
    }

    /**
     * Normalize a user-provided repo URL. Handles:
     *   - "https://..." → unchanged
     *   - "cloudstreamrepo://example.com/repo.json" → "https://example.com/repo.json"
     *   - "https://cs.repo/?example.com/repo.json" → "https://example.com/repo.json"
     */
    fun parseRepoUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> {
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
            else -> trimmed
        }
    }

    /** Fetch + parse a repository JSON. Returns null on failure. */
    suspend fun parseRepository(rawUrl: String): Repository? {
        val url = parseRepoUrl(rawUrl)
        return try {
            val response = app.get(convertRawGitUrl(url))
            val text = response.text
            json.decodeFromString<Repository>(text)
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    private suspend fun parsePlugins(pluginUrls: String): List<SitePlugin> {
        return try {
            val response = app.get(convertRawGitUrl(pluginUrls))
            val text = response.text
            json.decodeFromString<Array<SitePlugin>>(text).toList()
        } catch (t: Throwable) {
            logError(t)
            emptyList()
        }
    }

    /**
     * Fetch all plugins from a repository. Returns null if repo can't be parsed.
     */
    suspend fun getRepoPlugins(repositoryUrl: String): List<SitePlugin>? {
        val repo = parseRepository(repositoryUrl) ?: return null
        return repo.pluginLists.flatMap { url ->
            parsePlugins(url)
        }
    }

    /**
     * Download a plugin file to disk. Verifies SHA-256 hash if provided.
     */
    suspend fun downloadPluginToFile(
        pluginUrl: String,
        targetFile: File,
        expectedFileHash: String? = null,
    ): File? {
        return try {
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

            val response = app.get(convertRawGitUrl(pluginUrl))
            val body = response.okhttpResponse.body
            body.byteStream().use { bodyStream ->
                tempFile.outputStream().use { fileStream ->
                    bodyStream.copyTo(fileStream)
                }
            }

            if (expectedFileHash != null) {
                val downloadHash = sha256(tempFile)
                if (expectedFileHash != downloadHash) {
                    tempFile.delete()
                    throw IllegalStateException(
                        "Extension hash mismatch when validating '${targetFile.name}'! " +
                        "Expected: '$expectedFileHash', got: '$downloadHash'."
                    )
                }
            }

            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            targetFile
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    /** Sanitized file name for a plugin (internalName + hash). */
    fun getPluginFileName(internalName: String): String {
        val sanitized = sanitizeFilename(internalName, true)
        return "${sanitized}_${internalName.hashCode()}.${if (isDesktopPlatform()) "jar" else "cs3"}"
    }

    /** Sanitized folder name for a repository (based on repo URL). */
    fun getRepoFolderName(repoUrl: String): String {
        val sanitized = sanitizeFilename(repoUrl, true)
        return "${sanitized}_${repoUrl.hashCode()}"
    }

    private fun sanitizeFilename(name: String, validateEmpty: Boolean = false): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank {
            if (validateEmpty) "unnamed" else ""
        }
        return cleaned.take(120)
    }
}
