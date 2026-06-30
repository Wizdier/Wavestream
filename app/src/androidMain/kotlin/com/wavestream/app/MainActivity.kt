package com.wavestream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wavestream.App
import com.wavestream.WaveAppInit
import com.wavestream.initPlatform
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(); super.onCreate(savedInstanceState)
        initPlatform(this)
        WaveAppInit.initialize(File(filesDir, "Extensions"))
        setContent { App() }
    }
}
