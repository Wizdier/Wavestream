package com.wizdier.wavestream.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wizdier.wavestream.data.db.dao.DownloadDao
import com.wizdier.wavestream.data.db.dao.FavoritesDao
import com.wizdier.wavestream.data.db.dao.HistoryDao
import com.wizdier.wavestream.data.db.dao.RepoDao
import com.wizdier.wavestream.data.db.dao.SearchHistoryDao
import com.wizdier.wavestream.data.db.entities.DownloadEntity
import com.wizdier.wavestream.data.db.entities.FavoriteEntity
import com.wizdier.wavestream.data.db.entities.HistoryEntity
import com.wizdier.wavestream.data.db.entities.RepoEntity
import com.wizdier.wavestream.data.db.entities.SearchHistoryEntity

@Database(
    entities = [
        HistoryEntity::class,
        FavoriteEntity::class,
        DownloadEntity::class,
        RepoEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WaveStreamDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun downloadDao(): DownloadDao
    abstract fun repoDao(): RepoDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val NAME = "wavestream.db"
    }
}
