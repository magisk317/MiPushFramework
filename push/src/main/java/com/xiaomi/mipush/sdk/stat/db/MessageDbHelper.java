package com.xiaomi.mipush.sdk.stat.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract;
import com.xiaomi.mipush.sdk.stat.db.base.BaseDbHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/MessageDbHelper.class */
public class MessageDbHelper extends BaseDbHelper {
    private static final String BLOB_TYPE = " BLOB";
    private static final String COMMA_SEP = ",";
    private static final int DATABASE_VERSION = 1;
    private static final String INTEGER_TYPE = " INTEGER";
    private static final String SQL_CREATE_ENTRIES = "CREATE TABLE IF NOT EXISTS messageTable (rowDataId INTEGER PRIMARY KEY AUTOINCREMENT,appId TEXT,messageId TEXT,messageItemId TEXT,messageItem BLOB,createTimeStamp INTEGER,uploadTimestamp INTEGER,status INTEGER,packageName TEXT )";
    private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS messageTable";
    private static final String TEXT_TYPE = " TEXT";

    public MessageDbHelper(Context context, String str) {
        super(context, str, null, 1);
    }

    public MessageDbHelper(Context context, String str, SQLiteDatabase.CursorFactory cursorFactory, int i) {
        super(context, str, cursorFactory, i);
    }

    public static MessageDbHelper newInstance(Context context, String str) {
        return new MessageDbHelper(context, str);
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public String getDatabaseName() {
        return DataBaseConfig.DATABASE_NAME;
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.BaseDbHelper
    public String getTableName() {
        return MessageInfoContract.MessageEntry.TABLE_NAME;
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onCreate(SQLiteDatabase sQLiteDatabase) {
        sQLiteDatabase.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onUpgrade(SQLiteDatabase sQLiteDatabase, int i, int i2) {
        sQLiteDatabase.execSQL(SQL_DELETE_ENTRIES);
        onCreate(sQLiteDatabase);
    }
}
