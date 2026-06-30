package com.wavestream.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Desktop-specific initialization. Call from `main()` before launching the
 * Compose window.
 *
 * Usage:
 * ```
 * initPlatform {
 *     dataDir = File(System.getProperty("user.home"), ".wavestream")
 * }
 * ```
 */
fun initPlatformDesktop(configure: WavePlatformBuilder.() -> Unit = {}) {
    val builder = WavePlatformBuilder()
    val defaultData = File(System.getProperty("user.home", "."), ".wavestream").also { it.mkdirs() }
    builder.dataDir = defaultData
    builder.extensionsDir = File(defaultData, "Extensions").also { it.mkdirs() }
    builder.downloadsDir = File(defaultData, "Downloads").also { it.mkdirs() }
    builder.preferences = JsonFilePreferences(File(defaultData, "preferences.json"))
    builder.coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    builder.configure()
    WavePlatformInstance = builder.build()
}

actual fun initPlatform(configure: WavePlatformBuilder.() -> Unit) {
    initPlatformDesktop(configure)
}
