package com.wizdier.wavestream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.wizdier.wavestream.ui.navigation.WaveNavHost
import com.wizdier.wavestream.ui.theme.WaveStreamTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaveStreamTheme {
                WaveNavHost()
            }
        }
    }
}
