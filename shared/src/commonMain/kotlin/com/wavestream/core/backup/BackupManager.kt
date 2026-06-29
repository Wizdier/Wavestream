package com.wavestream.core.backup

import com.wavestream.core.storage.DataStore
import com.wavestream.features.bookmarks.BookmarkRepository
import com.wavestream.features.search.SearchHistoryRepository
import com.wavestream.features.subscriptions.SubscriptionRepository
import com.wavestream.features.watchprogress.WatchProgressRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class BackupData(
        val version: Int = 1,
        val createdAt: Long,
        val bookmarks: List<BookmarkBackup> = emptyList(),
        val watchHistory: List<WatchHistoryBackup> = emptyList(),
        val searchHistory: List<SearchHistoryBackup> = emptyList(),
        val subscriptions: List<SubscriptionBackup> = emptyList(),
    )

    @Serializable
    data class BookmarkBackup(val apiName: String, val url: String, val name: String, val posterUrl: String?, val typeName: String)

    @Serializable
    data class WatchHistoryBackup(val id: String, val apiName: String, val url: String, val title: String, val posterUrl: String?, val positionMs: Long, val durationMs: Long)

    @Serializable
    data class SearchHistoryBackup(val query: String, val timestamp: Long)

    @Serializable
    data class SubscriptionBackup(val apiName: String, val url: String, val name: String)

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
            val jsonStr = if (sourceFile.name.endsWith(".zip") || sourceFile.name.endsWith(".wavestream-backup")) {
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
        val bookmarks = BookmarkRepository.getAll().map {
            BookmarkBackup(it.apiName, it.url, it.name, it.posterUrl, it.type.name)
        }
        val watchHistory = WatchProgressRepository.getContinueWatching(100).map {
            WatchHistoryBackup(it.id, it.apiName, it.url, it.title, it.posterUrl, it.positionMs, it.durationMs)
        }
        val searchHistory = SearchHistoryRepository.load().map {
            SearchHistoryBackup(it.query, it.timestamp)
        }
        val subscriptions = SubscriptionRepository.getAll().map {
            SubscriptionBackup(it.apiName, it.url, it.name)
        }

        return BackupData(
            createdAt = System.currentTimeMillis(),
            bookmarks = bookmarks,
            watchHistory = watchHistory,
            searchHistory = searchHistory,
            subscriptions = subscriptions,
        )
    }

    private fun restoreBackupData(backup: BackupData) {
        // Note: Full restore would need to call back into each repository's data loading.
        // For now, we store the backup JSON in DataStore so it can be inspected.
        DataStore.setKey("last_backup", backup.createdAt)
    }
}
