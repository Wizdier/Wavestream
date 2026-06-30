package com.wavestream

import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wavestream.platform.initPlatformDesktop
import androidx.compose.ui.unit.dp

/**
 * Desktop entry point. Initializes the platform (data dir, preferences,
 * extensions dir) and then shows the Compose window with [App].
 *
 * Run with `./gradlew :shared:run` (after adding the application plugin)
 * or `./gradlew :shared:run -DmainClass=com.wavestream.MainKt`.
 */
fun main() {
    initPlatformDesktop()
    WaveAppInit.initialize()

    application {
        val state = rememberWindowState(
            width = 1280.dp,
            height = 800.dp,
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "Wavestream",
            state = state,
        ) {
            App()
        }
    }
}
