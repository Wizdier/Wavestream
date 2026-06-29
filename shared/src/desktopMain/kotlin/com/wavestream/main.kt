package com.wavestream

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wavestream.core.WaveAppInit
import com.wavestream.core.network.NetworkClient
import com.wavestream.core.network.initNetworkClient
import com.wavestream.core.storage.DataStore
import com.wavestream.core.storage.DesktopStorage
import java.awt.Dimension
import java.io.File

fun main() = application {
    initNetworkClient()
    DataStore.init(DesktopStorage.init(NetworkClient.json))
    WaveAppInit.initialize(File(System.getProperty("user.home"), ".wavestream/plugins"))

    val windowState = rememberWindowState(
        width = 1280.dp,
        height = 800.dp,
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "Wavestream",
        state = windowState,
    ) {
        window.minimumSize = Dimension(800, 600)
        App()
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
