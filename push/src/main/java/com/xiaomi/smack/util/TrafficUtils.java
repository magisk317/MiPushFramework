package com.xiaomi.smack.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.push.providers.TrafficDatabaseHelper;
import com.xiaomi.push.providers.TrafficProvider;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/util/TrafficUtils.class */
public class TrafficUtils {
    private static final long IDLE_INTERVAL = 30000;
    private static final long TRAFFIC_INTERVAL = 5000;
    private static final long WRITE2DB_INTERVAL = 5000;
    private static SerializedAsyncTaskProcessor mAsyncProcessor = new SerializedAsyncTaskProcessor(true);
    private static volatile int networkType = -1;
    private static long lastRxTs = System.currentTimeMillis();
    private static final Object lock = new Object();
    private static List<TrafficInfo> trafficList = Collections.synchronizedList(new ArrayList<>());
    private static String imsi = "";
    private static TrafficDatabaseHelper dbHelper = null;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/util/TrafficUtils$TrafficInfo.class */
    static class TrafficInfo {
        public long bytes;
        public String imsi;
        public long messageTs;
        public int networkType;
        public String packageName;
        public int rcv;

        public TrafficInfo(String str, long j, int i, int i2, String str2, long j2) {
            this.packageName = "";
            this.messageTs = 0L;
            this.networkType = -1;
            this.rcv = -1;
            this.imsi = "";
            this.bytes = 0L;
            this.packageName = str;
            this.messageTs = j;
            this.networkType = i;
            this.rcv = i2;
            this.imsi = str2;
            this.bytes = j2;
        }

        public boolean canAccumulate(TrafficInfo trafficInfo) {
            return TextUtils.equals(trafficInfo.packageName, this.packageName) && TextUtils.equals(trafficInfo.imsi, this.imsi) && trafficInfo.networkType == this.networkType && trafficInfo.rcv == this.rcv && Math.abs(trafficInfo.messageTs - this.messageTs) <= 5000;
        }
    }

    public static void distributionTraffic(Context context, String str, long j, boolean z, boolean z2, long j2) throws Throwable {
        saveTraffic(context, str, getTraffic(getNetworkType(context), j, z, j2, z2), z, j2);
    }

    private static int getActiveNetworkType(Context context) {
        try {
            return Network.getActiveNetworkType(context);
        } catch (Exception e2) {
            return -1;
        }
    }

    private static String getIMSI(Context context) {
        synchronized (TrafficUtils.class) {
            try {
                if (TextUtils.isEmpty(imsi)) {
                    return "";
                }
                return imsi;
            } finally {
            }
        }
    }

    public static int getNetworkType(Context context) {
        if (networkType == -1) {
            networkType = getActiveNetworkType(context);
        }
        return networkType;
    }

    private static long getTraffic(int i, long j, boolean z, long j2, boolean z2) {
        if (z && z2) {
            long j3 = lastRxTs;
            lastRxTs = j2;
            if (j2 - j3 > 30000 && j > 1024) {
                return 2 * j;
            }
        }
        return (((long) (i == 0 ? 13 : 11)) * j) / 10;
    }

    private static TrafficDatabaseHelper getTrafficDatabaseHelper(Context context) {
        TrafficDatabaseHelper trafficDatabaseHelper = dbHelper;
        if (trafficDatabaseHelper != null) {
            return trafficDatabaseHelper;
        }
        TrafficDatabaseHelper trafficDatabaseHelper2 = new TrafficDatabaseHelper(context);
        dbHelper = trafficDatabaseHelper2;
        return trafficDatabaseHelper2;
    }

    public static int getTrafficFlow(String str) {
        try {
            return str.getBytes("UTF-8").length;
        } catch (UnsupportedEncodingException e) {
            return str.getBytes().length;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void insertTraffic(Context context, List<TrafficInfo> list) {
        try {
            synchronized (TrafficDatabaseHelper.DataBaseLock) {
                SQLiteDatabase writableDatabase = getTrafficDatabaseHelper(context).getWritableDatabase();
                writableDatabase.beginTransaction();
                try {
                    for (TrafficInfo trafficInfo : list) {
                        ContentValues contentValues = new ContentValues();
                        contentValues.put("package_name", trafficInfo.packageName);
                        contentValues.put(TrafficProvider.TrafficColumns.MESSAGE_TS, Long.valueOf(trafficInfo.messageTs));
                        contentValues.put(TrafficProvider.TrafficColumns.NETWORK_TYPE, Integer.valueOf(trafficInfo.networkType));
                        contentValues.put(TrafficProvider.TrafficColumns.BYTES, Long.valueOf(trafficInfo.bytes));
                        contentValues.put(TrafficProvider.TrafficColumns.RCV, Integer.valueOf(trafficInfo.rcv));
                        contentValues.put(TrafficProvider.TrafficColumns.IMSI, trafficInfo.imsi);
                        writableDatabase.insert("traffic", null, contentValues);
                    }
                    writableDatabase.setTransactionSuccessful();
                } finally {
                    writableDatabase.endTransaction();
                }
            }
        } catch (SQLiteException e) {
            MyLog.e(e);
        }
    }

    private static void insertTrafficInfo2List(TrafficInfo trafficInfo) {
        for (TrafficInfo trafficInfo2 : trafficList) {
            if (trafficInfo2.canAccumulate(trafficInfo)) {
                trafficInfo2.bytes += trafficInfo.bytes;
                return;
            }
        }
        trafficList.add(trafficInfo);
    }

    public static void notifyNetworkChanage(Context context) {
        networkType = getActiveNetworkType(context);
    }

    private static void saveTraffic(final Context context, String str, long j, boolean z, long j2) throws Throwable {
        if (context == null || TextUtils.isEmpty(str)) {
            return;
        }
        if (!"com.xiaomi.xmsf".equals(context.getPackageName()) || "com.xiaomi.xmsf".equals(str)) {
            return;
        }
        int networkType2 = getNetworkType(context);
        if (networkType2 == -1) {
            return;
        }
        boolean zIsEmpty;
        synchronized (lock) {
            zIsEmpty = trafficList.isEmpty();
            insertTrafficInfo2List(new TrafficInfo(str, j2, networkType2, z ? 1 : 0, networkType2 == 0 ? getIMSI(context) : "", j));
        }
        if (zIsEmpty) {
            mAsyncProcessor.addNewTaskWithDelayed(new SerializedAsyncTaskProcessor.SerializedAsyncTask() { // from class: com.xiaomi.smack.util.TrafficUtils.1
                @Override // com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
                public void process() {
                    List<TrafficInfo> arrayList = new ArrayList<>();
                    synchronized (TrafficUtils.lock) {
                        if (!TrafficUtils.trafficList.isEmpty()) {
                            arrayList.addAll(TrafficUtils.trafficList);
                            TrafficUtils.trafficList.clear();
                        }
                    }
                    if (!arrayList.isEmpty()) {
                        TrafficUtils.insertTraffic(context, arrayList);
                    }
                }
            }, 5000L);
        }
    }

    public static void updateIMSI(String str) {
        synchronized (TrafficUtils.class) {
            try {
                if (!MIUIUtils.isGlobalRegion() && !TextUtils.isEmpty(str)) {
                    imsi = str;
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }
}
