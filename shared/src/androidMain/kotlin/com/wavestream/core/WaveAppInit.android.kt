package com.wavestream.core

import java.io.File

private var appFilesDir: File? = null

fun initWaveAppInit(filesDir: File) {
    appFilesDir = filesDir
}

actual fun getDefaultPluginsDirPlatform(): File {
    val dir = appFilesDir ?: File(System.getProperty("user.home"), ".wavestream")
    val pluginsDir = File(dir, "Extensions")
    if (!pluginsDir.exists()) pluginsDir.mkdirs()
    return pluginsDir
}
