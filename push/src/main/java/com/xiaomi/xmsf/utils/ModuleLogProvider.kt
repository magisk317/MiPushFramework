package com.xiaomi.xmsf.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ModuleLogProvider : ContentProvider() {
    companion object {
        private const val AUTHORITY = "com.xiaomi.xmsf.module.log"
        private const val PATH_ENTRY = "entry"
        private const val SOURCE_DEFAULT = "module"
        private const val LEVEL_DEFAULT = "I"
        private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        fun entryUri(): Uri = Uri.parse("content://$AUTHORITY/$PATH_ENTRY")

        private fun sanitizeSegment(value: String?): String {
            return value
                ?.takeIf { it.isNotBlank() }
                ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                ?.take(64)
                ?: SOURCE_DEFAULT
        }
    }

    private val writeLock = Any()

    override fun onCreate(): Boolean = true

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (uri.authority != AUTHORITY || uri.lastPathSegment != PATH_ENTRY) {
            return null
        }
        val context = context?.applicationContext ?: return null
        val source = sanitizeSegment(values?.getAsString("source"))
        val level = values?.getAsString("level")?.ifBlank { LEVEL_DEFAULT } ?: LEVEL_DEFAULT
        val tag = values?.getAsString("tag").orEmpty()
        val packageName = values?.getAsString("package_name").orEmpty()
        val processName = values?.getAsString("process_name").orEmpty()
        val message = values?.getAsString("message").orEmpty()
        val throwable = values?.getAsString("throwable").orEmpty()
        val now = Date()
        val logDir = File(LogBundleExporter.getLogDir(context), "modules")
        synchronized(writeLock) {
            runCatching {
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                LogUtils.pruneModuleLogsForToday(logDir, now)
                val file = File(logDir, "${source}_${fileDateFormat.format(now)}.txt")
                val line = buildString {
                    append(logDateFormat.format(now))
                    append(" [")
                    append(source)
                    append("/")
                    append(level)
                    append("] ")
                    if (tag.isNotBlank()) {
                        append(tag)
                    } else {
                        append("unknown")
                    }
                    if (packageName.isNotBlank() || processName.isNotBlank()) {
                        append(" ")
                        append("pkg=")
                        append(packageName.ifBlank { "unknown" })
                        append(" ")
                        append("proc=")
                        append(processName.ifBlank { "unknown" })
                    }
                    append(": ")
                    append(message)
                    append('\n')
                    if (throwable.isNotBlank()) {
                        append(throwable)
                        if (!throwable.endsWith('\n')) {
                            append('\n')
                        }
                    }
                }
                file.appendText(line)
            }
        }
        return entryUri()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? = null
}
