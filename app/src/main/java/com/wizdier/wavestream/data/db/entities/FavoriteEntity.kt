package com.wizdier.wavestream.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A favourited title. Users can group favourites into named lists via
 * [listName] — Nuvio-style user lists.
 */
@Entity(
    tableName = "favorites",
    indices = [Index(value = ["itemId", "listName"], unique = true)]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val itemId: String,
    val providerId: String,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val url: String,
    val type: String,
    val listName: String = DEFAULT_LIST,
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_LIST = "Watchlist"
    }
}
