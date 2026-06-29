package com.wavestream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.wavestream.App
import com.wavestream.core.AppLogger
import com.wavestream.core.WaveAppInit
import com.wavestream.core.network.NetworkClient
import com.wavestream.core.network.initCloudflareKiller
import com.wavestream.core.network.initNetworkClient
import com.wavestream.core.network.initWebViewResolver
import com.wavestream.core.storage.AndroidStorage
import com.wavestream.core.storage.DataStore
import com.wavestream.core.WaveExceptionHandler
import com.wavestream.plugins.initPluginManager
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize crash handler
        AppLogger.init(filesDir)
        WaveExceptionHandler.install { }

        // Initialize network + storage
        initNetworkClient()
        DataStore.init(AndroidStorage.init(this, NetworkClient.json))

        // Initialize Cloudflare bypass + WebView resolver
        initCloudflareKiller(this)
        initWebViewResolver(this)

        // Initialize plugin manager + load all plugins
        initPluginManager(this)

        // Load built-in extractors and plugins from disk
        WaveAppInit.initialize(File(filesDir, "Extensions"))

        setContent {
            App()
        }
    }
}
