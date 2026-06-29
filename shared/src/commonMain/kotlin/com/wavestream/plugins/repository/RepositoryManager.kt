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
 */
object RepositoryManager {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Repository(
        val name: String = "",
        val description: String? = null,
        val manifestVersion: Int = 1,
        val pluginLists: List<String> = emptyList(),
    )

    @Serializable
    data class SitePlugin(
        val url: String = "",
        val name: String = "",
        val internalName: String = "",
        val version: Int = 1,
        val status: Int = 1,
        val language: String? = null,
        val authors: List<String> = emptyList(),
        val description: String? = null,
        val tvTypes: List<String>? = null,
        val iconUrl: String? = null,
        val fileSize: Long? = null,
        val fileHash: String? = null,
    )

    /** Fetch + parse a repository JSON. */
    suspend fun parseRepository(url: String): Repository? {
        return runCatching {
            val response = app.get(url)
            if (!response.status.isSuccess()) return null
            json.decodeFromString<Repository>(response.bodyAsText())
        }.getOrNull()
    }

    /** Fetch plugin list from a repository. Returns (repoUrl, SitePlugin) pairs. */
    suspend fun getRepoPlugins(repoUrl: String): List<SitePlugin>? {
        val repo = parseRepository(repoUrl) ?: return null
        val allPlugins = mutableListOf<SitePlugin>()
        for (pluginListUrl in repo.pluginLists) {
            val response = app.get(pluginListUrl)
            if (!response.status.isSuccess()) continue
            val plugins = runCatching {
                json.decodeFromString<Array<SitePlugin>>(response.bodyAsText())
            }.getOrNull()?.toList() ?: emptyList()
            allPlugins.addAll(plugins)
        }
        return allPlugins
    }

    /** Download a .cs3 plugin file to disk. */
    suspend fun downloadPlugin(pluginUrl: String, targetFile: File, expectedHash: String? = null): File? {
        return runCatching {
            targetFile.parentFile?.mkdirs()
            val response = app.get(pluginUrl)
            if (!response.status.isSuccess()) return null

            val bytes = response.readRawBytes()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tempFile.writeBytes(bytes)

            if (expectedHash != null) {
                val actualHash = sha256(tempFile)
                if (actualHash != expectedHash) {
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
        }.getOrNull()
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

    fun getPluginFileName(internalName: String): String = "${internalName}_${internalName.hashCode()}.cs3"
}
