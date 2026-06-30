package com.wavestream

import android.content.Context
import java.io.File

private var appContext: Context? = null
fun initPlatform(context: Context) { appContext = context.applicationContext }

actual fun getDefaultPluginsDirPlatform(): File {
    val context = appContext ?: return File(System.getProperty("user.home"), ".wavestream/plugins")
    val pluginsDir = File(context.filesDir, "Extensions"); if (!pluginsDir.exists()) pluginsDir.mkdirs(); return pluginsDir
}

actual object PlatformStorage {
    actual fun getString(key: String): String? = appContext?.getSharedPreferences("wavestream", Context.MODE_PRIVATE)?.getString(key, null)
    actual fun putString(key: String, value: String) { appContext?.getSharedPreferences("wavestream", Context.MODE_PRIVATE)?.edit()?.putString(key, value)?.apply() }
}
