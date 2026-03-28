package com.xiaomi.mipush.sdk.stat.db.base;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/base/BaseDbHelper.class */
public abstract class BaseDbHelper extends SQLiteOpenHelper {
    public BaseDbHelper(Context context, String str, SQLiteDatabase.CursorFactory cursorFactory, int i) {
        super(context, str, cursorFactory, i);
    }

    public abstract String getTableName();
}
