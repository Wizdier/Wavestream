package com.wizdier.wavestream.data.repository

import com.wizdier.wavestream.data.db.dao.DownloadDao
import com.wizdier.wavestream.data.db.entities.DownloadEntity
import kotlinx.coroutines.flow.Flow

/**
 * Download queue repository. Wraps [DownloadDao] and exposes the operations
 * the download service and the Downloads screen need: enqueue, list,
 * progress updates, pause/resume/cancel.
 */
class DownloadRepository(private val dao: DownloadDao) {

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()
    fun observePending(): Flow<List<DownloadEntity>> = dao.observePending()
    fun observe(rowId: Long): Flow<DownloadEntity?> = dao.observe(rowId)

    suspend fun enqueue(entity: DownloadEntity): Long = dao.insert(entity)

    suspend fun updateProgress(rowId: Long, downloaded: Long, total: Long, status: String) =
        dao.updateProgress(rowId, downloaded, total, status)

    suspend fun setStatus(rowId: Long, status: String, errorMessage: String? = null) =
        dao.updateStatus(rowId, status, errorMessage)

    suspend fun get(rowId: Long): DownloadEntity? = dao.get(rowId)

    suspend fun delete(rowId: Long) = dao.delete(rowId)
}
