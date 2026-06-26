package com.wizdier.wavestream.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wizdier.wavestream.data.db.entities.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE listName = :listName ORDER BY addedAt DESC")
    fun observeByList(listName: String): Flow<List<FavoriteEntity>>

    @Query("SELECT listName FROM favorites GROUP BY listName ORDER BY MIN(addedAt) ASC")
    fun observeListNames(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE itemId = :itemId AND listName = :listName)")
    fun observeIsFavorite(itemId: String, listName: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE itemId = :itemId AND listName = :listName)")
    suspend fun isFavorite(itemId: String, listName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE itemId = :itemId AND listName = :listName")
    suspend fun remove(itemId: String, listName: String)

    @Query("DELETE FROM favorites WHERE listName = :listName")
    suspend fun deleteList(listName: String)

    @Query("UPDATE favorites SET listName = :newName WHERE listName = :oldName")
    suspend fun renameList(oldName: String, newName: String)
}
