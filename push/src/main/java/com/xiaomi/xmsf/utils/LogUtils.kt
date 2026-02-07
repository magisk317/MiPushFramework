@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.ClassicFlattener
import com.elvishew.xlog.formatter.message.json.DefaultJsonFormatter
import com.elvishew.xlog.formatter.message.xml.DefaultXmlFormatter
import com.elvishew.xlog.formatter.stacktrace.DefaultStackTraceFormatter
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.Printer
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator
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
        val configuration = LogConfiguration.Builder()
            .tag("Xmsf")
            .logLevel(LogLevel.ALL)
            .jsonFormatter(DefaultJsonFormatter())
            .xmlFormatter(DefaultXmlFormatter())
            .stackTraceFormatter(DefaultStackTraceFormatter())
            .enableThreadInfo()
            .threadFormatter { data -> "TID: [${data.id}] TName: [${data.name}]" }
            .build()

        val androidPrinter: Printer = AndroidPrinter()
        val days7InMillis = 7 * 24 * 60 * 60 * 1000
        val filePrinter: Printer = FilePrinter.Builder(getLogFolder(context))
            .fileNameGenerator(DateFileNameGenerator())
            .backupStrategy(NeverBackupStrategy())
            .cleanStrategy(FileLastModifiedCleanStrategy(days7InMillis.toLong()))
            .flattener(ClassicFlattener())
            .build()

        XLog.init(configuration, androidPrinter, filePrinter)
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
            com.elvishew.xlog.LogUtils.compress(getLogFolder(context), zipFile.absolutePath)
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
}
