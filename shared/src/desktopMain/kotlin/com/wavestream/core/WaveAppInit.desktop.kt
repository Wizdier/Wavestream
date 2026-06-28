package com.wavestream.core

import java.io.File

actual fun getDefaultPluginsDirPlatform(): File {
    val dir = File(System.getProperty("user.home"), ".wavestream")
    val pluginsDir = File(dir, "plugins")
    if (!pluginsDir.exists()) pluginsDir.mkdirs()
    return pluginsDir
}
