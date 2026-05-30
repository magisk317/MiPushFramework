package com.xiaomi.push.providers

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.xiaomi.channel.commonutils.logger.MyLog

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/aa/a.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/providers/TrafficDatabaseHelper.java
 * Stock class name is obfuscated as aa.a; this file keeps the deobfuscated com.xiaomi.push.providers.TrafficDatabaseHelper API.
 */
class TrafficDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private fun createTrafficTable(db: SQLiteDatabase) {
        val sb = StringBuilder("CREATE TABLE traffic(_id INTEGER  PRIMARY KEY ,")
        var i = 0
        while (i < TRAFFIC_Columns.size - 1) {
            if (i != 0) {
                sb.append(",")
            }
            sb.append(TRAFFIC_Columns[i])
            sb.append(" ")
            sb.append(TRAFFIC_Columns[i + 1])
            i += 2
        }
        sb.append(");")
        db.execSQL(sb.toString())
    }

    override fun onCreate(db: SQLiteDatabase) {
        synchronized(DataBaseLock) {
            try {
                createTrafficTable(db)
            } catch (e: SQLException) {
                MyLog.e(e)
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No upgrade logic needed
    }

    companion object {
        const val DATABASE_NAME = "traffic.db"
        const val TRAFFIC_TABLE = "traffic"
        private var DATABASE_VERSION = 1
        @JvmField
        val DataBaseLock = Any()
        private const val ZERO_BASED_LONG = " LONG DEFAULT 0 "
        private const val ZERO_BASED_INTEGER = " INT DEFAULT -1 "
        private val TRAFFIC_Columns = arrayOf(
            "package_name", "TEXT",
            "message_ts", ZERO_BASED_LONG,
            "bytes", ZERO_BASED_LONG,
            "network_type", ZERO_BASED_INTEGER,
            "rcv", ZERO_BASED_INTEGER,
            "imsi", "TEXT"
        )
    }
}
