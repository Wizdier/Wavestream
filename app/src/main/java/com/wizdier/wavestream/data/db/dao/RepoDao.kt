package com.wizdier.wavestream.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wizdier.wavestream.data.db.entities.RepoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {

    @Query("SELECT * FROM repos ORDER BY installedAt DESC")
    fun observeAll(): Flow<List<RepoEntity>>

    @Query("SELECT * FROM repos")
    suspend fun getAll(): List<RepoEntity>

    @Query("SELECT * FROM repos WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): RepoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RepoEntity): Long

    @Query("DELETE FROM repos WHERE rowId = :rowId")
    suspend fun delete(rowId: Long)

    @Query("UPDATE repos SET lastUpdatedAt = :ts WHERE rowId = :rowId")
    suspend fun markUpdated(rowId: Long, ts: Long = System.currentTimeMillis())
}
