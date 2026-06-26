package com.wizdier.wavestream.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A queued / in-progress / finished download. Mirrors CloudStream's download
 * table — but with Nuvio-style status flow and per-source quality labels.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val itemId: String,
    val providerId: String,
    val title: String,
    val posterUrl: String?,
    val url: String,                  // Stream URL to fetch
    val outputUri: String,            // Where the file is written once finished
    val qualityLabel: String?,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = "queued",    // queued | running | paused | completed | failed
    val mimeType: String = "video/mp4",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    val progress: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}
