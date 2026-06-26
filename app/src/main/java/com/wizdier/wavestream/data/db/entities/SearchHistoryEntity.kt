package com.wizdier.wavestream.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Recent search query used to populate the search screen's history chips.
 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val query: String,
    val createdAt: Long = System.currentTimeMillis()
)
