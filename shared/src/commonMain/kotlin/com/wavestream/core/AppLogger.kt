package com.wavestream.core

import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

/**
 * App logger — provides structured logging to file + console.
 *
 * Mirrors CloudStream's logging + crash reporting.
 *
 * On Android, logs go to Logcat + a file in filesDir/last_error
 * On Desktop, logs go to stdout + ~/.wavestream/wavestream.log
 */
object AppLogger {
    private const val TAG = "Wavestream"

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var errorFile: File? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    fun init(logDir: File) {
        logFile = File(logDir, "wavestream.log").apply {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        }
        errorFile = File(logDir, "last_error")
    }

    fun d(tag: String = TAG, message: String) {
        log("DEBUG", tag, message)
    }

    fun i(tag: String = TAG, message: String) {
        log("INFO", tag, message)
    }

    fun w(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log("WARN", tag, message, throwable)
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log("ERROR", tag, message, throwable)
        // Also write to error file
        errorFile?.let { file ->
            try {
                PrintWriter(file).use { writer ->
                    writer.println("[$tag] $message")
                    throwable?.printStackTrace(writer)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val line = "$timestamp [$level] [$tag] $message"
        println(line)
        throwable?.printStackTrace()

        // Append to log file (keep last 5MB)
        logFile?.let { file ->
            try {
                if (file.exists() && file.length() > 5 * 1024 * 1024) {
                    file.delete()
                    file.createNewFile()
                }
                file.appendText(line + "\n")
                throwable?.let {
                    file.appendText(it.stackTraceToString() + "\n")
                }
            } catch (_: Throwable) {}
        }
    }

    fun getLogFile(): File? = logFile
    fun getErrorFile(): File? = errorFile

    fun clearLogs() {
        logFile?.takeIf { it.exists() }?.writeText("")
        errorFile?.takeIf { it.exists() }?.delete()
    }
}

/**
 * Global exception handler — catches uncaught exceptions and logs them.
 *
 * Mirrors CloudStream's ExceptionHandler.
 */
class WaveExceptionHandler(
    private val onError: () -> Unit,
) : Thread.UncaughtExceptionHandler {

    private val previousHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            AppLogger.e("UncaughtException", "Fatal exception on thread ${thread.name}", error)
        } catch (_: Throwable) {}
        try {
            onError()
        } catch (_: Throwable) {}
        previousHandler?.uncaughtException(thread, error)
    }

    companion object {
        fun install(onError: () -> Unit) {
            Thread.setDefaultUncaughtExceptionHandler(WaveExceptionHandler(onError))
        }
    }
}
