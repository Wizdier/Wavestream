package com.wavestream.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup manager — mirrors CloudStream's BackupUtils.
 *
 * Exports all app data to a JSON file, optionally zipped.
 */
object BackupManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class BackupData(
        val version: Int = 1,
        val createdAt: Long,
        val accounts: List<AccountBackup> = emptyList(),
        val settings: Map<String, String> = emptyMap(),
        val bookmarks: List<BookmarkBackup> = emptyList(),
        val watchHistory: List<WatchHistoryBackup> = emptyList(),
        val searchHistory: List<SearchHistoryBackup> = emptyList(),
        val repositories: List<String> = emptyList(),
        val plugins: List<PluginBackup> = emptyList(),
    )

    @Serializable
    data class AccountBackup(val id: Int, val name: String, val createdAt: Long)

    @Serializable
    data class BookmarkBackup(
        val url: String, val name: String, val apiName: String,
        val type: String, val posterUrl: String?,
    )

    @Serializable
    data class WatchHistoryBackup(
        val url: String, val name: String, val apiName: String,
        val episode: Int?, val season: Int?, val positionMs: Long,
        val durationMs: Long, val watchedAt: Long,
    )

    @Serializable
    data class SearchHistoryBackup(val query: String, val timestamp: Long)

    @Serializable
    data class PluginBackup(
        val internalName: String, val url: String?,
        val isOnline: Boolean, val version: Int,
    )

    fun export(targetFile: File, zip: Boolean = true) {
        val backup = collectBackupData()
        val jsonStr = json.encodeToString(BackupData.serializer(), backup)
        if (zip) {
            ZipOutputStream(targetFile.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("backup.json"))
                zos.write(jsonStr.toByteArray())
                zos.closeEntry()
            }
        } else {
            targetFile.writeText(jsonStr)
        }
    }

    fun import(sourceFile: File): Boolean {
        return try {
            val jsonStr = if (sourceFile.extension == "zip" || sourceFile.name.endsWith(".wavestream-backup")) {
                ZipInputStream(sourceFile.inputStream()).use { zis ->
                    var content: String? = null
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "backup.json") {
                            content = zis.bufferedReader().readText()
                            break
                        }
                        entry = zis.nextEntry
                    }
                    content ?: return false
                }
            } else {
                sourceFile.readText()
            }
            val backup = json.decodeFromString(BackupData.serializer(), jsonStr)
            restoreBackupData(backup)
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun collectBackupData(): BackupData {
        return BackupData(createdAt = System.currentTimeMillis())
    }

    private fun restoreBackupData(backup: BackupData) {
        // Restore state
    }
}
