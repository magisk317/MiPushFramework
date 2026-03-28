package com.xiaomi.push.providers;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.providers.TrafficProvider;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/providers/TrafficDatabaseHelper.class */
public class TrafficDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "traffic.db";
    public static final String TRAFFIC_TABLE = "traffic";
    private static int DATABASE_VERSION = 1;
    public static final Object DataBaseLock = new Object();
    private static final String ZERO_BASED_LONG = " LONG DEFAULT 0 ";
    private static final String ZERO_BASED_INTEGER = " INT DEFAULT -1 ";
    private static final String[] TRAFFIC_Columns = {"package_name", "TEXT", TrafficProvider.TrafficColumns.MESSAGE_TS, ZERO_BASED_LONG, TrafficProvider.TrafficColumns.BYTES, ZERO_BASED_LONG, TrafficProvider.TrafficColumns.NETWORK_TYPE, ZERO_BASED_INTEGER, TrafficProvider.TrafficColumns.RCV, ZERO_BASED_INTEGER, TrafficProvider.TrafficColumns.IMSI, "TEXT"};

    public TrafficDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, (SQLiteDatabase.CursorFactory) null, DATABASE_VERSION);
    }

    private void createTrafficTable(SQLiteDatabase sQLiteDatabase) {
        StringBuilder sb = new StringBuilder("CREATE TABLE traffic(_id INTEGER  PRIMARY KEY ,");
        int i = 0;
        while (true) {
            String[] strArr = TRAFFIC_Columns;
            if (i >= strArr.length - 1) {
                sb.append(");");
                sQLiteDatabase.execSQL(sb.toString());
                return;
            }
            if (i != 0) {
                sb.append(",");
            }
            sb.append(strArr[i]);
            sb.append(" ");
            sb.append(strArr[i + 1]);
            i += 2;
        }
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onCreate(SQLiteDatabase sQLiteDatabase) {
        synchronized (DataBaseLock) {
            try {
                createTrafficTable(sQLiteDatabase);
            } catch (SQLException e) {
                MyLog.e(e);
            }
        }
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onUpgrade(SQLiteDatabase sQLiteDatabase, int i, int i2) {
    }
}
