package com.wizdier.wavestream.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-added provider repository (multi-repo support, CloudStream-style).
 * Repos are JSON manifests that list available provider extensions.
 */
@Entity(
    tableName = "repos",
    indices = [Index(value = ["url"], unique = true)]
)
data class RepoEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val url: String,
    val name: String,
    val description: String?,
    val author: String?,
    val installedAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long? = null,
    val isAutoUpdate: Boolean = true,
    val requiresApi: Int = 1
)
