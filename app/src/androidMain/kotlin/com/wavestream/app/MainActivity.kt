package com.wavestream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.wavestream.App
import com.wavestream.core.network.NetworkClient
import com.wavestream.core.network.initNetworkClient
import com.wavestream.core.storage.AndroidStorage
import com.wavestream.core.storage.DataStore
import com.wavestream.plugins.initPluginManager

/**
 * Android main activity — entry point for the Wavestream app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        initNetworkClient()
        DataStore.init(AndroidStorage.init(this, NetworkClient.json))
        initPluginManager(this)

        setContent {
            App()
        }
    }
}

