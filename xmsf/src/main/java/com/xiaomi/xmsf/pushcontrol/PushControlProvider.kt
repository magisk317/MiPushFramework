package com.xiaomi.xmsf.pushcontrol

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.xiaomi.xmsf.security.ExportedSurfacePolicy
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class PushControlProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        if (!ExportedSurfacePolicy.isPushControlQueryAllowed(uri, projection, selection, selectionArgs, sortOrder)) {
            return emptyCursor()
        }
        val context = context ?: return emptyCursor()
        return StockSurfaceSupport.pushControlCursor(context)
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun emptyCursor() = MatrixCursor(COLUMNS)

    private companion object {
        val COLUMNS = arrayOf("control_mode", "key_words", "special_pkg_names", "control_switch")
    }
}
