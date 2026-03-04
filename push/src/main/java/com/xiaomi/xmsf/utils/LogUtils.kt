@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import com.xiaomi.xmsf.R
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.trumeet.common.Constants

object LogUtils {
    @JvmStatic
    fun init(context: Context) {
        Napier.base(DebugAntilog())
        try {
            val logDir = File(getLogFolder(context))
            Napier.base(FileAntilog(logDir))
        } catch (_: Exception) {}
    }

    private class FileAntilog(private val logDir: File) : Antilog() {
        private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
            try {
                if (!logDir.exists()) logDir.mkdirs()
                val fileName = "logs_${fileDateFormat.format(Date())}.txt"
                val file = File(logDir, fileName)
                val time = logDateFormat.format(Date())
                val errorMsg = throwable?.stackTraceToString() ?: ""
                val line = "$time [${priority.name}] ${tag ?: ""}: ${message ?: ""} $errorMsg\n"
                file.appendText(line)
            } catch (_: Exception) {}
        }
    }

    @JvmStatic
    fun getLogFolder(context: Context): String {
        return context.cacheDir.absolutePath + "/logs"
    }

    @JvmStatic
    fun clearLog(context: Context) {
        val file = File(getLogFolder(context))
        if (!file.exists() || file.length() <= 0) {
            Toast.makeText(context, R.string.log_none, Toast.LENGTH_SHORT).show()
            return
        }
        val files = file.listFiles() ?: emptyArray()
        for (f in files) {
            if (f.isFile) {
                f.delete()
            }
        }
        Toast.makeText(context, context.getString(R.string.log_clear_done), Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun getShareIntent(context: Context): Intent? {
        val zipFile = File(
            context.externalCacheDir!!.absolutePath + "/logs/" + logArchiveName(Date()) + ".zip"
        )
        return try {
            compressFolder(getLogFolder(context), zipFile.absolutePath)
            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                Constants.AUTHORITY_FILE_PROVIDER,
                zipFile
            )
            if (!zipFile.exists()) {
                throw NullPointerException()
            }
            val intent = Intent()
            intent.action = Intent.ACTION_SEND
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            var type = context.contentResolver.getType(fileUri)
            if (type == null || type.trim().isEmpty()) {
                type = "application/zip"
            }
            intent.type = type
            intent.putExtra(Intent.EXTRA_STREAM, fileUri)
            intent
        } catch (_: IOException) {
            null
        } catch (_: NullPointerException) {
            null
        }
    }

    @JvmStatic
    fun logArchiveName(date: Date): String {
        return "logs_" + dateInfo(date)
    }

    @JvmStatic
    fun dateInfo(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(date)
    }

    private fun compressFolder(sourceFolder: String, destinationZip: String) {
        val srcDir = File(sourceFolder)
        if (!srcDir.exists() || srcDir.listFiles()?.isEmpty() == true) return
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(destinationZip)).use { zout ->
            srcDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    java.io.FileInputStream(file).use { fin ->
                        val entry = java.util.zip.ZipEntry(file.name)
                        zout.putNextEntry(entry)
                        fin.copyTo(zout)
                        zout.closeEntry()
                    }
                }
            }
        }
    }
}
