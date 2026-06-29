package com.wavestream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.wavestream.App
import com.wavestream.WaveAppInit
import com.wavestream.initPlatform
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize platform (SharedPreferences, plugins dir)
        initPlatform(this)

        // Initialize Wavestream (loads plugins, fetches repos, registers extractors)
        WaveAppInit.initialize(File(filesDir, "Extensions"))

        setContent {
            App()
        }
    }
}
