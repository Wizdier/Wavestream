package com.wizdier.wavestream.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wizdier.wavestream.data.db.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE itemId = :itemId LIMIT 1")
    suspend fun getByItemId(itemId: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity): Long

    @Query("UPDATE history SET progressMs = :progress, durationMs = :duration, updatedAt = :ts WHERE rowId = :rowId")
    suspend fun updateProgress(rowId: Long, progress: Long, duration: Long, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM history WHERE rowId = :rowId")
    suspend fun delete(rowId: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}
