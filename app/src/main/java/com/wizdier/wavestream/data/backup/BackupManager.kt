package com.wizdier.wavestream.data.backup

import android.content.Context
import com.wizdier.wavestream.data.db.WaveStreamDatabase
import com.wizdier.wavestream.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Backup and restore — exports the user's favorites, history, repos,
 * search history, and settings as a JSON file. Restore re-imports.
 */
class BackupManager(
    private val context: Context,
    private val db: WaveStreamDatabase,
    private val settingsRepo: SettingsRepository
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Serializable
    data class Backup(
        val version: Int = 1,
        val timestamp: Long,
        val favorites: List<FavEntry>,
        val history: List<HistEntry>,
        val repos: List<RepoEntry>,
        val searchHistory: List<String>,
        val settings: Map<String, String>
    )

    @Serializable
    data class FavEntry(
        val itemId: String, val providerId: String, val title: String,
        val posterUrl: String?, val backdropUrl: String?, val url: String,
        val type: String, val listName: String
    )

    @Serializable
    data class HistEntry(
        val itemId: String, val providerId: String, val title: String,
        val posterUrl: String?, val backdropUrl: String?, val url: String,
        val type: String, val season: Int, val episode: Int,
        val progressMs: Long, val durationMs: Long
    )

    @Serializable
    data class RepoEntry(
        val url: String, val name: String, val description: String?,
        val author: String?
    )

    suspend fun export(): File = withContext(Dispatchers.IO) {
        val favorites = db.favoritesDao().observeAll().first().map {
            FavEntry(it.itemId, it.providerId, it.title, it.posterUrl, it.backdropUrl, it.url, it.type, it.listName)
        }
        val history = db.historyDao().observeAll().first().map {
            HistEntry(it.itemId, it.providerId, it.title, it.posterUrl, it.backdropUrl, it.url, it.type, it.season, it.episode, it.progressMs, it.durationMs)
        }
        val repos = db.repoDao().getAll().map {
            RepoEntry(it.url, it.name, it.description, it.author)
        }
        val searchHistory = db.searchHistoryDao().observeRecent(100).first().map { it.query }
        val settings = mapOf<String, String>()

        val backup = Backup(
            version = 1,
            timestamp = System.currentTimeMillis(),
            favorites = favorites,
            history = history,
            repos = repos,
            searchHistory = searchHistory,
            settings = settings
        )

        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val backupFile = File(backupDir, "wavestream-backup-${System.currentTimeMillis()}.json")
        backupFile.writeText(json.encodeToString(Backup.serializer(), backup))
        backupFile
    }

    suspend fun import(file: File): Int = withContext(Dispatchers.IO) {
        val text = file.readText()
        val backup = json.decodeFromString(Backup.serializer(), text)

        backup.favorites.forEach { f ->
            db.favoritesDao().insert(
                com.wizdier.wavestream.data.db.entities.FavoriteEntity(
                    itemId = f.itemId,
                    providerId = f.providerId,
                    title = f.title,
                    posterUrl = f.posterUrl,
                    backdropUrl = f.backdropUrl,
                    url = f.url,
                    type = f.type,
                    listName = f.listName
                )
            )
        }

        backup.history.forEach { h ->
            db.historyDao().upsert(
                com.wizdier.wavestream.data.db.entities.HistoryEntity(
                    itemId = h.itemId,
                    providerId = h.providerId,
                    title = h.title,
                    posterUrl = h.posterUrl,
                    backdropUrl = h.backdropUrl,
                    url = h.url,
                    type = h.type,
                    season = h.season,
                    episode = h.episode,
                    progressMs = h.progressMs,
                    durationMs = h.durationMs
                )
            )
        }

        backup.repos.forEach { r ->
            db.repoDao().insert(
                com.wizdier.wavestream.data.db.entities.RepoEntity(
                    url = r.url,
                    name = r.name,
                    description = r.description,
                    author = r.author
                )
            )
        }

        backup.searchHistory.forEach { q ->
            db.searchHistoryDao().insert(
                com.wizdier.wavestream.data.db.entities.SearchHistoryEntity(query = q)
            )
        }

        backup.favorites.size + backup.history.size + backup.repos.size
    }
}
