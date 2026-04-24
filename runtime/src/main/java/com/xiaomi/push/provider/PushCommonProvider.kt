package com.xiaomi.push.provider
import io.github.magisk317.mipush.protocol.model.*

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class PushCommonProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = context ?: return Bundle()
        return StockSurfaceSupport.handlePushCommonCall(context, method, extras)
    }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
