package com.xiaomi.push.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import com.xiaomi.smack.util.TrafficUtils;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/providers/TrafficProvider.class */
public class TrafficProvider extends ContentProvider {
    public static final String AUTHORITY = "com.xiaomi.push.providers.TrafficProvider";
    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.xiaomi.push.traffic";
    public static final Uri CONTENT_URI = Uri.parse("content://com.xiaomi.push.providers.TrafficProvider/traffic");
    private static final int IMSI = 2;
    private static final int TRAFFICS = 1;
    public static final String TRAFFIC_TABLE_NAME = "traffic";
    public static final String UPDATE_IMSI = "update_imsi";
    private static final UriMatcher sUriMatcher;
    private SQLiteOpenHelper dbHelper;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/providers/TrafficProvider$TrafficColumns.class */
    public interface TrafficColumns extends BaseColumns {
        public static final String BYTES = "bytes";
        public static final String IMSI = "imsi";
        public static final String MESSAGE_TS = "message_ts";
        public static final String NETWORK_TYPE = "network_type";
        public static final String PACKAGE_NAME = "package_name";
        public static final String RCV = "rcv";
    }

    static {
        UriMatcher uriMatcher = new UriMatcher(-1);
        sUriMatcher = uriMatcher;
        uriMatcher.addURI(AUTHORITY, "traffic", 1);
        uriMatcher.addURI(AUTHORITY, UPDATE_IMSI, 2);
    }

    @Override // android.content.ContentProvider
    public int bulkInsert(Uri uri, ContentValues[] contentValuesArr) {
        return 0;
    }

    @Override // android.content.ContentProvider
    public int delete(Uri uri, String str, String[] strArr) {
        return 0;
    }

    @Override // android.content.ContentProvider
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case 1:
                return CONTENT_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override // android.content.ContentProvider
    public Uri insert(Uri uri, ContentValues contentValues) {
        return null;
    }

    @Override // android.content.ContentProvider
    public boolean onCreate() {
        this.dbHelper = new TrafficDatabaseHelper(getContext());
        return true;
    }

    @Override // android.content.ContentProvider
    public Cursor query(Uri uri, String[] strArr, String str, String[] strArr2, String str2) {
        synchronized (TrafficDatabaseHelper.DataBaseLock) {
            switch (sUriMatcher.match(uri)) {
                case 1:
                    return this.dbHelper.getReadableDatabase().query("traffic", strArr, str, strArr2, null, null, str2);
                default:
                    throw new IllegalArgumentException("Unknown URI " + uri);
            }
        }
    }

    @Override // android.content.ContentProvider
    public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
        switch (sUriMatcher.match(uri)) {
            case 2:
                if (contentValues != null && contentValues.containsKey(TrafficColumns.IMSI)) {
                    TrafficUtils.updateIMSI(contentValues.getAsString(TrafficColumns.IMSI));
                    break;
                }
                break;
        }
        return 0;
    }
}
