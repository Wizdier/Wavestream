package com.lagradost.cloudstream3.plugins

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.mvvm.safeAsync
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * A repository descriptor. Comes from a repo's `repo.json`.
 */
@Serializable
data class Repository(
    @JsonProperty("iconUrl") @SerialName("iconUrl") val iconUrl: String?,
    @JsonProperty("name") @SerialName("name") val name: String,
    @JsonProperty("description") @SerialName("description") val description: String?,
    @JsonProperty("manifestVersion") @SerialName("manifestVersion") val manifestVersion: Int,
    @JsonProperty("pluginLists") @SerialName("pluginLists") val pluginLists: List<String>,
)

/**
 * Status int:
 * 0: Down
 * 1: Ok
 * 2: Slow
 * 3: Beta only
 */
@Serializable
data class SitePlugin(
    @JsonProperty("url") @SerialName("url") val url: String,
    @JsonProperty("status") @SerialName("status") val status: Int,
    @JsonProperty("version") @SerialName("version") val version: Int,
    @JsonProperty("apiVersion") @SerialName("apiVersion") val apiVersion: Int,
    @JsonProperty("name") @SerialName("name") val name: String,
    @JsonProperty("internalName") @SerialName("internalName") val internalName: String,
    @JsonProperty("authors") @SerialName("authors") val authors: List<String>,
    @JsonProperty("description") @SerialName("description") val description: String?,
    @JsonProperty("repositoryUrl") @SerialName("repositoryUrl") val repositoryUrl: String?,
    @JsonProperty("tvTypes") @SerialName("tvTypes") val tvTypes: List<String>?,
    @JsonProperty("language") @SerialName("language") val language: String?,
    @JsonProperty("iconUrl") @SerialName("iconUrl") val iconUrl: String?,
    @JsonProperty("fileSize") @SerialName("fileSize") val fileSize: Long?,
    @JsonProperty("fileHash") @SerialName("fileHash") val fileHash: String?,
)

/**
 * Pairing of a repository URL with the plugin lists it advertises.
 * Used for persistent storage of added repos.
 */
@Serializable
data class RepositoryData(
    @JsonProperty("url") @SerialName("url") val url: String,
    @JsonProperty("name") @SerialName("name") val name: String? = null,
    @JsonProperty("description") @SerialName("description") val description: String? = null,
    @JsonProperty("manifestVersion") @SerialName("manifestVersion") val manifestVersion: Int = 0,
    @JsonProperty("pluginLists") @SerialName("pluginLists") val pluginLists: List<String> = emptyList(),
    @JsonProperty("iconUrl") @SerialName("iconUrl") val iconUrl: String? = null,
)

/**
 * Storage abstraction that the host app (shared module's RepositoryStore) must
 * assign before any persistence operations are used. Allows the library module
 * to remain platform-agnostic.
 */
interface RepositoryStorage {
    fun <T> getKey(key: String): T?
    fun <T> setKey(key: String, value: T?)
    fun removeKey(key: String)
}

object RepositoryManager {
    const val TAG = "RepositoryManager"
    const val ONLINE_PLUGINS_FOLDER = "Extensions"
    const val REPOSITORIES_KEY = "REPOSITORIES_KEY"

    /**
     * Set by the host app at boot. If unset, persistence operations degrade
     * gracefully (no repos are remembered between sessions).
     */
    @Volatile
    var storage: RepositoryStorage? = null

    val PREBUILT_REPOSITORIES: Array<RepositoryData> by lazy {
        @Suppress("UNCHECKED_CAST")
        storage?.getKey<Array<RepositoryData>>("PREBUILT_REPOSITORIES") ?: emptyArray()
    }

    private val GH_REGEX =
        Regex("^https://raw\\.githubusercontent\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)/(.*)$")

    /**
     * Returns a SHA-256 string of the file content.
     * Example: "sha256-b70462c264cb7f90fc2860a8e58d7544ce747ff347d1d11fa093623901853573"
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
     * Convert raw.githubusercontent.com URLs to cdn.jsdelivr.net if [useJsDelivr] is true.
     * Defaults to false (no proxy) — the host app can opt in.
     */
    @Volatile
    var useJsDelivrProxy: Boolean = false

    fun convertRawGitUrl(url: String): String {
        if (!useJsDelivrProxy) return url
        val match = GH_REGEX.find(url) ?: return url
        val (user, repo, rest) = match.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
    }

    /**
     * Parses a user-supplied repo URL into a raw JSON URL.
     * Supports:
     *  - Direct https URLs (returned as-is)
     *  - `cloudstreamrepo://` and `https://cs.repo/?` shortcuts
     *  - Short codes (via cutt.ly redirect)
     */
    suspend fun parseRepoUrl(url: String): String? {
        val fixedUrl = url.trim()
        return if (fixedUrl.contains("^https?://".toRegex())) {
            fixedUrl
        } else if (fixedUrl.contains("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)".toRegex())) {
            fixedUrl.replace("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)".toRegex(), "").let {
                return@let if (!it.contains("^https?://".toRegex()))
                    "https://${it}"
                else fixedUrl
            }
        } else if (fixedUrl.matches("^[a-zA-Z0-9!_-]+$".toRegex())) {
            safeAsync {
                app.get("https://cutt.ly/${fixedUrl}", allowRedirects = false).let { it2 ->
                    it2.headers["Location"]?.let { url ->
                        if (url.startsWith("https://cutt.ly/404")) return@safeAsync null
                        if (url.removeSuffix("/") == "https://cutt.ly") return@safeAsync null
                        return@safeAsync url
                    }
                }
            }
        } else null
    }

    suspend fun parseRepository(url: String): Repository? {
        return safeAsync {
            // Take manifestVersion and such into account later
            app.get(convertRawGitUrl(url), cacheTime = 5, cacheUnit = TimeUnit.MINUTES).parsedSafe<Repository>()
        }
    }

    private suspend fun parsePlugins(pluginUrls: String): List<SitePlugin> {
        return try {
            app.get(convertRawGitUrl(pluginUrls), cacheTime = 5, cacheUnit = TimeUnit.MINUTES)
                .parsed<Array<SitePlugin>>().toList()
        } catch (t: Throwable) {
            logError(t)
            emptyList()
        }
    }

    /**
     * Gets all plugins from a repository and pairs them with the repository url.
     *
     * Handles two URL formats:
     * 1. A Repository JSON (object with `pluginLists` field) — fetches each
     *    plugin list URL and merges results.
     * 2. A direct plugin list JSON (array of SitePlugin) — uses it directly.
     *    This is common when users paste a `plugins.json` URL instead of a
     *    `repo.json` URL (e.g. raw.githubusercontent.com/.../plugins.json).
     *
     * Returns null only if both approaches fail. Returns an empty list if
     * the repo parsed but has zero plugins.
     */
    suspend fun getRepoPlugins(repositoryUrl: String): List<Pair<String, SitePlugin>>? {
        // Approach 1: try parsing as a Repository JSON
        val repo = parseRepository(repositoryUrl)
        if (repo != null && repo.pluginLists.isNotEmpty()) {
            return repo.pluginLists.amap { url ->
                parsePlugins(url).map { repositoryUrl to it }
            }.flatten()
        }
        // Approach 2: try parsing the URL directly as a plugin list
        val directPlugins = parsePlugins(repositoryUrl)
        if (directPlugins.isNotEmpty()) {
            return directPlugins.map { repositoryUrl to it }
        }
        // Both failed
        return null
    }

    /**
     * Downloads a plugin .cs3/.jar from [pluginUrl] into [file], validating
     * [expectedFileHash] if provided. Returns the destination file on success,
     * or null on failure.
     *
     * Uses an atomic move when supported to avoid corrupting existing plugins
     * if the download fails mid-stream.
     */
    suspend fun downloadPluginToFile(
        pluginUrl: String,
        file: File,
        expectedFileHash: String?,
        tempDir: File? = null,
    ): File? {
        return safeAsync {
            val parentDir = file.parentFile ?: return@safeAsync null
            parentDir.mkdirs()

            val tempFile = if (tempDir != null) {
                tempDir.mkdirs()
                File.createTempFile(file.name, ".tmp", tempDir)
            } else {
                File.createTempFile(file.name, ".tmp")
            }

            val body = app.get(convertRawGitUrl(pluginUrl)).okhttpResponse.body

            body.byteStream().use { body ->
                tempFile.outputStream().use { fileSteam ->
                    body.copyTo(fileSteam)
                }
            }

            if (expectedFileHash != null) {
                val downloadHash = sha256(tempFile)
                if (expectedFileHash != downloadHash) {
                    tempFile.delete()
                    throw IllegalStateException(
                        "Extension hash mismatch when validating '${file.name}'! " +
                            "Expected: '$expectedFileHash', got: '$downloadHash'."
                    )
                }
            }

            try {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            file
        }
    }

    // ------------------------------------------------------------------
    // Repository persistence — delegates to [storage] if set.
    // ------------------------------------------------------------------

    fun getRepositories(): Array<RepositoryData> {
        return storage?.getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()
    }

    private val repoLock = Mutex()

    suspend fun addRepository(repository: RepositoryData) {
        val s = storage ?: return
        repoLock.withLock {
            val currentRepos = getRepositories()
            s.setKey(REPOSITORIES_KEY, (currentRepos + repository).distinctBy { it.url })
        }
    }

    suspend fun removeRepository(repository: RepositoryData) {
        val s = storage ?: return
        repoLock.withLock {
            val currentRepos = getRepositories()
            s.setKey(REPOSITORIES_KEY, currentRepos.filter { it.url != repository.url })
        }
    }
}
