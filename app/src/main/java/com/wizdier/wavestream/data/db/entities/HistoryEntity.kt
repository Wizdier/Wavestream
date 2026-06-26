package com.wizdier.wavestream.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent watch history entry. Nuvio-style: stores per-episode progress
 * so the Continue Watching carousel can resume mid-episode.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val itemId: String,            // Provider item id (providerId + ':' + url hash)
    val providerId: String,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val url: String,               // Provider page url used for load()
    val type: String,              // CatalogType slug
    val season: Int = 1,
    val episode: Int = 1,
    val progressMs: Long = 0L,     // Last known playback position
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
