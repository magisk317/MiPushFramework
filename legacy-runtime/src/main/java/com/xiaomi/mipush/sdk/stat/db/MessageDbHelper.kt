package com.xiaomi.mipush.sdk.stat.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.xiaomi.mipush.sdk.stat.db.base.BaseDbHelper

/*
 * Local legacy stat database helper retained for compatibility.
 * No stock 7.4.67-C or 2026-04-13 current override same-path source was found in the dump.
 */
class MessageDbHelper(
    context: Context,
    str: String,
    cursorFactory: SQLiteDatabase.CursorFactory? = null,
    version: Int = DATABASE_VERSION,
) : BaseDbHelper(context, str, cursorFactory, version) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val SQL_CREATE_ENTRIES =
            "CREATE TABLE IF NOT EXISTS messageTable (rowDataId INTEGER PRIMARY KEY AUTOINCREMENT,appId TEXT,messageId TEXT,messageItemId TEXT,messageItem BLOB,createTimeStamp INTEGER,uploadTimestamp INTEGER,status INTEGER,packageName TEXT )"
        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS messageTable"

        fun newInstance(context: Context, str: String): MessageDbHelper {
            return MessageDbHelper(context, str)
        }
    }

    override fun getDatabaseName(): String = DataBaseConfig.DATABASE_NAME

    override fun getTableName(): String = MessageInfoContract.MessageEntry.TABLE_NAME

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }
}
