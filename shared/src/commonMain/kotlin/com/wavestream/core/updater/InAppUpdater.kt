package com.wavestream.core.updater

import com.wavestream.core.network.app
import com.wavestream.core.storage.DataStore
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object InAppUpdater {
    private const val OWNER = "wavestream"
    private const val REPO = "wavestream"
    private const val RELEASE_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val LAST_CHECK_KEY = "last_update_check"
    private const val SKIPPED_VERSION_KEY = "skipped_version"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class GitHubRelease(
        val tag_name: String,
        val name: String? = null,
        val body: String = "",
        val html_url: String = "",
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    data class Asset(
        val name: String = "",
        val browser_download_url: String = "",
        val size: Long = 0,
    )

    data class UpdateInfo(
        val version: String,
        val title: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val downloadSize: Long,
        val htmlUrl: String,
    )

    suspend fun checkForUpdates(currentVersion: String, force: Boolean = false): UpdateInfo? {
        if (!force) {
            val lastCheck = DataStore.getKey(LAST_CHECK_KEY, Long::class.java, 0L) ?: 0L
            if (System.currentTimeMillis() - lastCheck < 24 * 60 * 60 * 1000L) return null
        }
        DataStore.setKey(LAST_CHECK_KEY, System.currentTimeMillis())

        return try {
            val response = app.get(RELEASE_URL, headers = mapOf("Accept" to "application/vnd.github.v3+json"))
            if (!response.status.isSuccess()) return null
            val release = json.decodeFromString<GitHubRelease>(response.bodyAsText())

            val skippedVersion = DataStore.getKey(SKIPPED_VERSION_KEY, String::class.java) ?: ""
            if (release.tag_name == skippedVersion) return null

            val latestVersion = release.tag_name.removePrefix("v")
            if (!isNewerVersion(latestVersion, currentVersion)) return null

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null

            UpdateInfo(
                version = latestVersion,
                title = release.name ?: release.tag_name,
                releaseNotes = release.body,
                downloadUrl = apkAsset.browser_download_url,
                downloadSize = apkAsset.size,
                htmlUrl = release.html_url,
            )
        } catch (e: Throwable) {
            null
        }
    }

    fun skipVersion(version: String) {
        DataStore.setKey(SKIPPED_VERSION_KEY, version)
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrNull(i) ?: 0
            val c = currentParts.getOrNull(i) ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
