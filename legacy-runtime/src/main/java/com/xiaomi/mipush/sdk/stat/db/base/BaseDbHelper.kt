package com.xiaomi.mipush.sdk.stat.db.base

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

abstract class BaseDbHelper(
    context: Context,
    str: String,
    cursorFactory: SQLiteDatabase.CursorFactory?,
    i: Int,
) : SQLiteOpenHelper(context, str, cursorFactory, i) {
    abstract fun getTableName(): String
}
