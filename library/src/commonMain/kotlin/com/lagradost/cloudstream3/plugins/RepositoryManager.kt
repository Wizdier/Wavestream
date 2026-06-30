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

@Serializable
data class Repository(
    @SerialName("iconUrl") val iconUrl: String? = null,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("manifestVersion") val manifestVersion: Int = 1,
    @SerialName("pluginLists") val pluginLists: List<String> = emptyList(),
)

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
    @SerialName("jarUrl") val jarUrl: String? = null,
    @SerialName("jarHash") val jarHash: String? = null,
    @SerialName("jarFileSize") val jarFileSize: Long? = null,
) {
    fun bestUrlForPlatform(): String = if (isDesktopPlatform() && !jarUrl.isNullOrBlank()) jarUrl!! else url
    fun bestHashForPlatform(): String? = if (isDesktopPlatform() && !jarUrl.isNullOrBlank()) jarHash else fileHash
}

@Serializable
data class RepositoryData(val url: String, val name: String = "")

object RepositoryManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val GH_REGEX = Regex("^https://raw\\.githubusercontent\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)/(.*)$")

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis -> val buffer = ByteArray(8192); var read = fis.read(buffer); while (read != -1) { digest.update(buffer, 0, read); read = fis.read(buffer) } }
        return "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun convertRawGitUrl(url: String): String {
        val match = GH_REGEX.find(url) ?: return url
        val (user, repo, rest) = match.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
    }

    fun parseRepoUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> {
                if (trimmed.contains("cs.repo/")) { val after = trimmed.substringAfter("cs.repo/").removePrefix("?"); if (after.startsWith("http")) after else "https://$after" } else trimmed
            }
            trimmed.startsWith("cloudstreamrepo://") -> { val rest = trimmed.removePrefix("cloudstreamrepo://"); if (rest.startsWith("http")) rest else "https://$rest" }
            else -> trimmed
        }
    }

    suspend fun parseRepository(rawUrl: String): Repository? {
        return try { val url = parseRepoUrl(rawUrl); json.decodeFromString<Repository>(app.get(convertRawGitUrl(url)).text) }
        catch (t: Throwable) { logError(t); null }
    }

    private suspend fun parsePlugins(pluginUrls: String): List<SitePlugin> {
        return try { json.decodeFromString<Array<SitePlugin>>(app.get(convertRawGitUrl(pluginUrls)).text).toList() }
        catch (t: Throwable) { logError(t); emptyList() }
    }

    suspend fun getRepoPlugins(repositoryUrl: String): List<SitePlugin>? {
        val repo = parseRepository(repositoryUrl) ?: return null
        return repo.pluginLists.flatMap { parsePlugins(it) }
    }

    suspend fun downloadPluginToFile(pluginUrl: String, targetFile: File, expectedFileHash: String? = null): File? {
        return try {
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            val body = app.get(convertRawGitUrl(pluginUrl)).okhttpResponse.body
            body.byteStream().use { bodyStream -> tempFile.outputStream().use { fileStream -> bodyStream.copyTo(fileStream) } }
            if (expectedFileHash != null) { val downloadHash = sha256(tempFile); if (expectedFileHash != downloadHash) { tempFile.delete(); return null } }
            try { Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            targetFile
        } catch (t: Throwable) { logError(t); null }
    }

    fun getPluginFileName(internalName: String): String {
        val s = internalName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "unnamed" }.take(120)
        return "${s}_${internalName.hashCode()}.${if (isDesktopPlatform()) "jar" else "cs3"}"
    }

    fun getRepoFolderName(repoUrl: String): String {
        val s = repoUrl.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "unnamed" }.take(120)
        return "${s}_${repoUrl.hashCode()}"
    }
}
