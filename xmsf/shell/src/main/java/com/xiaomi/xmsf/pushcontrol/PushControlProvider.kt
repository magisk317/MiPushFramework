package com.xiaomi.xmsf.pushcontrol

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class PushControlProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        // Stock XMSF 7.4.67-C ignores every query-shape argument and always returns one fixed
        // online-config row. The older project returned an empty cursor for noncanonical calls;
        // restoring the read-only result cannot mutate or disclose any additional state.
        val context = context ?: return emptyCursor()
        return StockSurfaceSupport.pushControlCursor(context)
    }

    override fun getType(uri: Uri): String? = null

    // Stock XMSF 7.4.67-C acknowledges this inert operation with the input URI. The older project
    // returned null even though neither implementation persists the supplied values.
    override fun insert(uri: Uri, values: ContentValues?): Uri = uri

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun emptyCursor() = MatrixCursor(COLUMNS)

    private companion object {
        val COLUMNS = arrayOf("control_mode", "key_words", "special_pkg_names", "control_switch")
    }
}
