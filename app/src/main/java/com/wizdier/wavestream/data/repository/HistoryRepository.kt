package com.wizdier.wavestream.data.repository

import com.wizdier.wavestream.data.api.CatalogType
import com.wizdier.wavestream.data.db.dao.HistoryDao
import com.wizdier.wavestream.data.db.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Watch-history repository. Nuvio-style: persists per-episode progress so the
 * Continue Watching carousel can resume mid-episode. Each item is keyed by a
 * composite `providerId:url` id so the same title in two providers produces
 * two distinct rows.
 */
class HistoryRepository(private val dao: HistoryDao) {

    fun observeAll(): Flow<List<HistoryEntity>> = dao.observeAll()

    fun observeRecent(limit: Int = 10): Flow<List<HistoryEntity>> = dao.observeRecent(limit)

    fun observeContinueWatching(limit: Int = 12): Flow<List<HistoryEntity>> =
        dao.observeRecent(limit).map { rows ->
            rows.filter { it.progressMs > 0 && (it.durationMs == 0L || it.progressMs < it.durationMs - 10_000) }
        }

    suspend fun upsert(
        itemId: String,
        providerId: String,
        title: String,
        posterUrl: String?,
        backdropUrl: String?,
        url: String,
        type: CatalogType,
        season: Int,
        episode: Int,
        progressMs: Long,
        durationMs: Long
    ): Long {
        val existing = dao.getByItemId(itemId)
        val merged = HistoryEntity(
            rowId = existing?.rowId ?: 0,
            itemId = itemId,
            providerId = providerId,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            url = url,
            type = type.slug,
            season = season,
            episode = episode,
            progressMs = progressMs,
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis()
        )
        return dao.upsert(merged)
    }

    suspend fun updateProgress(rowId: Long, progressMs: Long, durationMs: Long) {
        dao.updateProgress(rowId, progressMs, durationMs)
    }

    /** Look up a single history entry by its composite itemId. Used for resume playback. */
    suspend fun getByItemId(itemId: String): HistoryEntity? = dao.getByItemId(itemId)

    suspend fun delete(rowId: Long) = dao.delete(rowId)

    suspend fun clear() = dao.clear()

    companion object {
        /** Build the composite id used as [HistoryEntity.itemId]. */
        fun composeItemId(providerId: String, url: String): String = "$providerId:${url.hashCode()}"
    }
}
