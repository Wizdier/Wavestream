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

class ExtensionInstaller(private val context: Context) {
    private val cs3Dir: File by lazy { File(context.cacheDir, "extensions").apply { mkdirs() } }
    private val apkDir: File by lazy { File(context.cacheDir, "apks").apply { mkdirs() } }

    suspend fun download(extension: RepoExtension): File = withContext(Dispatchers.IO) {
        val isCs3 = extension.apk.endsWith(".cs3", ignoreCase = true)
        val ext = if (isCs3) "cs3" else "apk"
        val targetDir = if (isCs3) cs3Dir else apkDir
        val filename = "${extension.name}-${extension.version}.$ext".replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(targetDir, filename)
        if (target.exists() && target.length() > 0) return@withContext target
        val req = Request.Builder().url(extension.apk).header("User-Agent", "WaveStream/1.0").build()
        NetworkModule.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response body")
            target.outputStream().use { sink -> body.byteStream().copyTo(sink) }
        }
        extension.fileHash?.let { expected ->
            if (expected.startsWith("sha256-")) {
                val actual = sha256(target)
                if (!expected.endsWith(actual)) { target.delete(); throw IOException("Hash mismatch") }
            }
        }
        target
    }

    suspend fun install(extension: RepoExtension, onReload: suspend () -> Unit) {
        val downloaded = download(extension)
        if (extension.apk.endsWith(".cs3", ignoreCase = true)) { onReload(); return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", downloaded)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buf = ByteArray(8192); while (true) { val n = input.read(buf); if (n <= 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
