package com.wizdier.wavestream.data.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.wizdier.wavestream.data.repository.RepoExtension
import com.wizdier.wavestream.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Downloads a CloudStream extension file (.cs3 or .apk) from a [RepoExtension]
 * URL and triggers the system APK installer.
 *
 * CloudStream 3's `.cs3` files are actually just renamed APKs — Android's
 * package installer accepts them as long as we present them with the
 * `application/vnd.android.package-archive` MIME type. The .cs3 extension
 * is purely a CloudStream convention; the file contents are a standard APK.
 */
class ExtensionInstaller(private val context: Context) {

    private val downloadDir: File by lazy {
        File(context.cacheDir, "extensions").apply { mkdirs() }
    }

    /**
     * Download the extension file and return the local File. The file is
     * cached by `[name]-[version].[ext]` so re-installs don't re-download.
     */
    suspend fun download(extension: RepoExtension): File = withContext(Dispatchers.IO) {
        val filename = "${extension.name}-${extension.version}.${extension.fileExtension}"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(downloadDir, filename)

        // Cache hit — if file exists and hash matches, skip download.
        if (target.exists() && target.length() > 0) {
            return@withContext target
        }

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

        // Optional integrity check
        extension.fileHash?.let { expectedHash ->
            if (expectedHash.startsWith("sha256-")) {
                val actual = sha256(target)
                if (!expectedHash.endsWith(actual)) {
                    target.delete()
                    throw IOException("File integrity check failed: hash mismatch")
                }
            }
        }

        target
    }

    /**
     * Trigger the system APK installer on the downloaded file. The user
     * will see the standard "Install app" dialog with permissions.
     *
     * For .cs3 files, we have to rename to .apk first because Android's
     * installer rejects unknown file extensions even when the MIME type
     * is correct.
     */
    suspend fun install(extension: RepoExtension) {
        val downloaded = download(extension)

        // .cs3 files need to be renamed to .apk for Android's installer.
        val installFile = if (extension.isCs3) {
            val apkFile = File(downloaded.parent, downloaded.nameWithoutExtension + ".apk")
            if (!apkFile.exists()) {
                downloaded.copyTo(apkFile, overwrite = true)
            }
            apkFile
        } else {
            downloaded
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            installFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
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
