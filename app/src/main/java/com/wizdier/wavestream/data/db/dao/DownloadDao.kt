package com.wizdier.wavestream.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wizdier.wavestream.data.db.entities.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('queued','running','paused')")
    fun observePending(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE rowId = :rowId LIMIT 1")
    suspend fun get(rowId: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE rowId = :rowId LIMIT 1")
    fun observe(rowId: Long): Flow<DownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, status = :status, updatedAt = :ts WHERE rowId = :rowId")
    suspend fun updateProgress(rowId: Long, downloaded: Long, total: Long, status: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = :status, errorMessage = :err, updatedAt = :ts WHERE rowId = :rowId")
    suspend fun updateStatus(rowId: Long, status: String, err: String? = null, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM downloads WHERE rowId = :rowId")
    suspend fun delete(rowId: Long)
}
