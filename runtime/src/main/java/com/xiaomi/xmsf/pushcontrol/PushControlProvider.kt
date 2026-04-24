package com.xiaomi.xmsf.pushcontrol

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class PushControlProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val context = context ?: return android.database.MatrixCursor(arrayOf("control_mode", "key_words", "special_pkg_names", "control_switch"))
        return StockSurfaceSupport.pushControlCursor(context)
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = uri

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
