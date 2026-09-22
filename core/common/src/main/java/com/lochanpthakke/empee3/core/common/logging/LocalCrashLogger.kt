package com.lochanpthakke.empee3.core.common.logging

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashLogEntry(
    val fileName: String,
    val timestamp: String,
    val content: String,
)

@Suppress("MagicNumber", "TooGenericExceptionCaught")
class LocalCrashLogger(
    private val context: Context,
) {
    private val crashDir: File by lazy {
        File(context.filesDir, "crash_logs").apply {
            if (!exists()) mkdirs()
        }
    }

    fun logCrash(throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(crashDir, "crash_$timestamp.log")

            val report =
                buildString {
                    appendLine("=== empee3 Crash Log ===")
                    appendLine("Timestamp: $timestamp")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                    appendLine("App Version: 1.0.0")
                    appendLine("------------------------")
                    appendLine(throwable.stackTraceToString())
                }

            file.writeText(report)
            trimOldLogs()
        } catch (_: Exception) {
            // Best effort local logging
        }
    }

    fun getCrashLogs(): List<CrashLogEntry> =
        try {
            val files = crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            files.map { file ->
                CrashLogEntry(
                    fileName = file.name,
                    timestamp = file.name.removePrefix("crash_").removeSuffix(".log"),
                    content = file.readText(),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

    fun clearAllLogs() {
        try {
            crashDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
            // Best effort
        }
    }

    private fun trimOldLogs() {
        val files = crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        if (files.size > 20) {
            files.drop(20).forEach { it.delete() }
        }
    }
}
