package com.xiaomi.xmsf.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import io.github.magisk317.mipush.utils.LogUtils

class ModuleLogProvider : ContentProvider() {
    companion object {
        private const val AUTHORITY = "com.xiaomi.xmsf.module.log"
        private const val PATH_ENTRY = "entry"
        private const val SOURCE_DEFAULT = "module"
        private const val LEVEL_DEFAULT = "I"

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
        synchronized(writeLock) {
            LogUtils.appendModuleLog(
                context = context,
                source = source,
                level = level,
                tag = tag,
                packageName = packageName,
                processName = processName,
                message = message,
                throwable = throwable,
            )
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
