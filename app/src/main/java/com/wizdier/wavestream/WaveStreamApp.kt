package com.wizdier.wavestream

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import com.wizdier.wavestream.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * WaveStream application entry point. Sets up Koin DI, WorkManager, and the
 * notification channels used by the download service.
 *
 * Built on top of CloudStream's plugin-first architecture and Nuvio's
 * Material You redesign — see the README for full credits.
 */
class WaveStreamApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@WaveStreamApp)
            modules(appModule)
        }
        registerNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DOWNLOAD,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Download progress notifications" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PLAYER,
                    "Player",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Media session notifications" }
            )
        }
    }

    companion object {
        const val CHANNEL_DOWNLOAD = "wavestream.downloads"
        const val CHANNEL_PLAYER = "wavestream.player"
    }
}
