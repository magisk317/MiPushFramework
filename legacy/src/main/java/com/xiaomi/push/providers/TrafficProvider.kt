package com.xiaomi.push.providers
import io.github.magisk317.mipush.protocol.model.*

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.BaseColumns
import com.xiaomi.smack.util.TrafficUtils

class TrafficProvider : ContentProvider() {
    private lateinit var dbHelper: SQLiteOpenHelper

    interface TrafficColumns : BaseColumns {
        companion object {
            const val BYTES = "bytes"
            const val IMSI = "imsi"
            const val MESSAGE_TS = "message_ts"
            const val NETWORK_TYPE = "network_type"
            const val PACKAGE_NAME = "package_name"
            const val RCV = "rcv"
        }
    }

    override fun bulkInsert(uri: Uri, values: Array<ContentValues>): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun getType(uri: Uri): String {
        return when (sUriMatcher.match(uri)) {
            TRAFFICS -> CONTENT_TYPE
            else -> throw IllegalArgumentException("Unknown URI $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun onCreate(): Boolean {
        dbHelper = TrafficDatabaseHelper(context!!)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return synchronized(TrafficDatabaseHelper.DataBaseLock) {
            when (sUriMatcher.match(uri)) {
                TRAFFICS -> dbHelper.readableDatabase.query(
                    TRAFFIC_TABLE_NAME,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    sortOrder
                )
                else -> throw IllegalArgumentException("Unknown URI $uri")
            }
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        return when (sUriMatcher.match(uri)) {
            IMSI -> {
                if (values != null && values.containsKey(TrafficColumns.IMSI)) {
                    TrafficUtils.updateIMSI(values.getAsString(TrafficColumns.IMSI))
                }
                0
            }
            else -> 0
        }
    }

    companion object {
        const val AUTHORITY = "com.xiaomi.push.providers.TrafficProvider"
        const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.xiaomi.push.traffic"
        val CONTENT_URI: Uri = Uri.parse("content://com.xiaomi.push.providers.TrafficProvider/traffic")
        private const val IMSI = 2
        private const val TRAFFICS = 1
        const val TRAFFIC_TABLE_NAME = "traffic"
        const val UPDATE_IMSI = "update_imsi"

        private val sUriMatcher: UriMatcher = UriMatcher(-1).apply {
            addURI(AUTHORITY, "traffic", TRAFFICS)
            addURI(AUTHORITY, UPDATE_IMSI, IMSI)
        }
    }
}
