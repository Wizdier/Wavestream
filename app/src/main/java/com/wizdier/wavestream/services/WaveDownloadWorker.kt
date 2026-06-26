package com.wizdier.wavestream.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Wake-up worker. After process death WorkManager will trigger this so the
 * [DownloadService] can resume draining the queue. The worker itself doesn't
 * perform the download — it just starts the foreground service.
 */
class WaveDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        DownloadService.start(applicationContext)
        return Result.success()
    }
}
