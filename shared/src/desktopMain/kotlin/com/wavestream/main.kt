package com.wavestream

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.io.File

fun main() = application {
    WaveAppInit.initialize(File(System.getProperty("user.home"), ".wavestream/plugins"))
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(onCloseRequest = ::exitApplication, title = "Wavestream", state = windowState) {
        window.minimumSize = Dimension(800, 600)
        App()
    }
}
private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
