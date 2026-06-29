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
        val bookmarks: List<String> = emptyList(),
        val watchHistory: List<String> = emptyList(),
        val searchHistory: List<String> = emptyList(),
        val subscriptions: List<String> = emptyList(),
    )

    fun export(targetFile: File, zip: Boolean = true) {
        val bookmarks = BookmarkRepository.getAll().map { "${it.apiName}|${it.url}|${it.name}" }
        val watchHistory = WatchProgressRepository.getContinueWatching(100).map { "${it.apiName}|${it.url}|${it.title}" }
        val searchHistory = SearchHistoryRepository.load().map { it.query }
        val subscriptions = SubscriptionRepository.getAll().map { "${it.apiName}|${it.url}|${it.name}" }

        val backup = BackupData(
            createdAt = System.currentTimeMillis(),
            bookmarks = bookmarks,
            watchHistory = watchHistory,
            searchHistory = searchHistory,
            subscriptions = subscriptions,
        )
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
            DataStore.setKey("last_backup", backup.createdAt)
            true
        } catch (e: Throwable) {
            false
        }
    }
}
