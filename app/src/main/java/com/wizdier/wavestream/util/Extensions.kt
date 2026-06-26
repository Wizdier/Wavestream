package com.wizdier.wavestream.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Extensions {

    fun Context.downloadDir(): File {
        val dir = File(getExternalFilesDir(null) ?: filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)

    fun fileProviderUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
