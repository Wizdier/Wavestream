package com.wizdier.wavestream.data.repository

import com.wizdier.wavestream.data.api.CatalogType
import com.wizdier.wavestream.data.db.dao.FavoritesDao
import com.wizdier.wavestream.data.db.entities.FavoriteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Favorites repository. Supports Nuvio-style user lists: every favourite is
 * filed under a named list, defaulting to "Watchlist". The Favorites tab
 * surfaces each list as a horizontal carousel.
 */
class FavoritesRepository(private val dao: FavoritesDao) {

    fun observeAll(): Flow<List<FavoriteEntity>> = dao.observeAll()

    fun observeList(listName: String): Flow<List<FavoriteEntity>> = dao.observeByList(listName)

    fun observeListNames(): Flow<List<String>> = dao.observeListNames()

    fun observeIsFavorite(itemId: String, listName: String = FavoriteEntity.DEFAULT_LIST): Flow<Boolean> =
        dao.observeIsFavorite(itemId, listName)

    suspend fun isFavorite(itemId: String, listName: String = FavoriteEntity.DEFAULT_LIST): Boolean =
        dao.isFavorite(itemId, listName)

    suspend fun toggle(
        itemId: String,
        providerId: String,
        title: String,
        posterUrl: String?,
        backdropUrl: String?,
        url: String,
        type: CatalogType,
        listName: String = FavoriteEntity.DEFAULT_LIST
    ): Boolean {
        val already = dao.isFavorite(itemId, listName)
        if (already) {
            dao.remove(itemId, listName)
        } else {
            dao.insert(
                FavoriteEntity(
                    itemId = itemId,
                    providerId = providerId,
                    title = title,
                    posterUrl = posterUrl,
                    backdropUrl = backdropUrl,
                    url = url,
                    type = type.slug,
                    listName = listName
                )
            )
        }
        return !already
    }

    suspend fun remove(itemId: String, listName: String = FavoriteEntity.DEFAULT_LIST) =
        dao.remove(itemId, listName)

    suspend fun renameList(oldName: String, newName: String) = dao.renameList(oldName, newName)

    suspend fun deleteList(name: String) = dao.deleteList(name)

    companion object {
        fun composeItemId(providerId: String, url: String): String =
            "$providerId:${url.hashCode()}"
    }
}
