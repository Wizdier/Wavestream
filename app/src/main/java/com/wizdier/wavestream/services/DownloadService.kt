package com.wizdier.wavestream.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wizdier.wavestream.MainActivity
import com.wizdier.wavestream.R
import com.wizdier.wavestream.WaveStreamApp
import com.wizdier.wavestream.data.db.entities.DownloadEntity
import com.wizdier.wavestream.data.network.NetworkModule
import com.wizdier.wavestream.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Request
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Foreground download service. CloudStream-style: drains the queue of pending
 * [DownloadEntity] rows one at a time, writing each stream to disk and
 * updating the row's progress as bytes flow in. WorkManager scheduling is
 * intentionally avoided for the actual transfer — direct OkIO streaming gives
 * us finer-grained progress callbacks.
 *
 * WorkManager is used to wake the service back up after process death; see
 * [WaveDownloadWorker].
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private val repo: DownloadRepository by lazy {
        GlobalContext.getOrNull()?.get() ?: error("Koin context not initialized")
    }
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Starting downloads…", 0, 0))
        processQueue()
        return START_STICKY
    }

    private fun processQueue() {
        scope.launch {
            val pending = runCatching { repo.observePending().first() }.getOrDefault(emptyList())
            if (pending.isEmpty()) {
                stopSelf(); return@launch
            }
            pending.forEach { download ->
                currentJob = launch { runDownload(download) }
                currentJob?.join()
            }
            stopSelf()
        }
    }

    private suspend fun runDownload(entity: DownloadEntity) {
        try {
            repo.setStatus(entity.rowId, "running")
            val targetPath = android.net.Uri.parse(entity.outputUri).path
                ?: throw IOException("Invalid output URI: ${entity.outputUri}")
            val target = File(targetPath)
            target.parentFile?.mkdirs()

            val req = Request.Builder().url(entity.url).build()
            NetworkModule.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    repo.setStatus(entity.rowId, "failed", "HTTP ${resp.code}")
                    return
                }
                val total = resp.body?.contentLength() ?: -1L
                if (total > 0) repo.updateProgress(entity.rowId, 0L, total, "running")
                val body = resp.body ?: throw IOException("Empty response body")
                var read = 0L
                var lastNotify = 0L
                FileOutputStream(target).use { sink ->
                    val source = body.byteStream()
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val n = source.read(buf)
                        if (n <= 0) break
                        sink.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            repo.updateProgress(entity.rowId, read, total, "running")
                            // Throttle notification updates to ~1 per second.
                            if (System.currentTimeMillis() - lastNotify > 1000) {
                                lastNotify = System.currentTimeMillis()
                                notificationManager.notify(
                                    NOTIFICATION_ID,
                                    buildNotification(
                                        "Downloading ${entity.title}",
                                        read,
                                        total
                                    )
                                )
                            }
                        }
                    }
                }
            }
            repo.setStatus(entity.rowId, "completed")
            notificationManager.notify(
                NOTIFICATION_ID,
                buildNotification("${entity.title} downloaded", 1, 1)
            )
        } catch (t: Throwable) {
            repo.setStatus(entity.rowId, "failed", t.message ?: "Unknown error")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    WaveStreamApp.CHANNEL_DOWNLOAD,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Download progress notifications" }
            )
        }
    }

    private fun buildNotification(text: String, downloaded: Long, total: Long): Notification {
        val builder = NotificationCompat.Builder(this, WaveStreamApp.CHANNEL_DOWNLOAD)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (total > 0) {
            val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
            builder.setProgress(100, pct, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        // Tapping the notification opens the app.
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(openPi)

        return builder.build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
