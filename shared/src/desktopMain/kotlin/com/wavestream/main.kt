package com.wavestream

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wavestream.core.network.NetworkClient
import com.wavestream.core.network.initNetworkClient
import com.wavestream.core.storage.DataStore
import com.wavestream.core.storage.DesktopStorage
import java.awt.Dimension

/**
 * Desktop entry point — launches the Wavestream Compose app in a JVM window.
 *
 * Usage: ./gradlew :shared:run
 *
 * This is primarily for development/testing. The production target is Android.
 */
fun main() = application {
    // Initialize platform-specific storage + networking
    initNetworkClient()
    DataStore.init(DesktopStorage.init(NetworkClient.json))

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
