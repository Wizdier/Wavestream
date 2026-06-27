package com.wizdier.wavestream.data.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.wizdier.wavestream.data.network.NetworkModule
import com.wizdier.wavestream.data.repository.RepoExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Downloads a CloudStream extension file (.cs3 or .apk) from a [RepoExtension]
 * URL and either:
 *
 *  - For `.cs3` files: saves to `cacheDir/extensions/` so the [PluginLoader]
 *    can load it on the next app start (or via [PluginLoader.reload]).
 *    CloudStream 3 .cs3 files are NOT Android APKs — they're ZIP archives
 *    containing compiled Kotlin (.dex) that WaveStream's Cs3PluginLoader
 *    loads at runtime via DexClassLoader.
 *
 *  - For `.apk` files: triggers the system package installer.
 */
class ExtensionInstaller(private val context: Context) {

    private val cs3Dir: File by lazy {
        File(context.cacheDir, "extensions").apply { mkdirs() }
    }

    private val apkDir: File by lazy {
        File(context.cacheDir, "apks").apply { mkdirs() }
    }

    /** Returns true if the extension is a CloudStream 3 .cs3 runtime plugin. */
    val RepoExtension.isCs3: Boolean get() = apk.endsWith(".cs3", ignoreCase = true)

    /** File extension without the leading dot ("cs3" or "apk"). */
    val RepoExtension.fileExtension: String get() = if (isCs3) "cs3" else "apk"

    /**
     * Download the extension file and return the local File. The file is
     * cached by `[name]-[version].[ext]` so re-installs don't re-download.
     */
    suspend fun download(extension: RepoExtension): File = withContext(Dispatchers.IO) {
        val targetDir = if (extension.isCs3) cs3Dir else apkDir
        val filename = "${extension.name}-${extension.version}.${extension.fileExtension}"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(targetDir, filename)

        // Cache hit — skip download.
        if (target.exists() && target.length() > 0) return@withContext target

        val req = Request.Builder()
            .url(extension.apk)
            .header("User-Agent", "WaveStream/1.0")
            .build()

        NetworkModule.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Download failed: HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IOException("Empty response body")
            target.outputStream().use { sink ->
                body.byteStream().copyTo(sink)
            }
        }

        // Optional integrity check (sha256-... prefix).
        extension.fileHash?.let { expected ->
            if (expected.startsWith("sha256-")) {
                val actual = sha256(target)
                if (!expected.endsWith(actual)) {
                    target.delete()
                    throw IOException("File integrity check failed: hash mismatch")
                }
            }
        }

        target
    }

    /**
     * Install the extension. For `.cs3` files this just downloads + reloads
     * the plugin registry — no system installer involved. For `.apk` files
     * it triggers Android's package installer.
     */
    suspend fun install(extension: RepoExtension, onReload: suspend () -> Unit) {
        val downloaded = download(extension)

        if (extension.isCs3) {
            // .cs3 files are runtime plugins — just reload the registry.
            onReload()
            return
        }

        // .apk files go through Android's installer.
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            downloaded
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
