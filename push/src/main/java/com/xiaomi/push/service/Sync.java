package com.xiaomi.push.service;

import android.content.Context;
import android.content.SharedPreferences;
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.DebugUtils;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.channel.commonutils.network.Network;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/Sync.class */
public final class Sync implements NetworkListener {
    private static final long MINIMUM_SYNC_DURATION = 3600000;
    private static final String PREF_NAME = "sync";
    private static final String PREF_TIMESTAMP = ":ts-";
    private static volatile Sync sSync;
    Context mContext;
    private long mLastSyncTime;
    private SharedPreferences mPrefs;
    private volatile boolean isSyncing = false;
    private ConcurrentHashMap<String, SyncJob> mCurJobs = new ConcurrentHashMap<>();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/Sync$SyncJob.class */
    public static abstract class SyncJob implements Runnable {
        String group;
        long period;

        SyncJob(String str, long j) {
            this.group = str;
            this.period = j;
        }

        @Override // java.lang.Runnable
        public void run() {
            if (Sync.sSync != null) {
                Context context = Sync.sSync.mContext;
                if (Network.isConnected(context)) {
                    if (System.currentTimeMillis() - Sync.sSync.mPrefs.getLong(Sync.PREF_TIMESTAMP + this.group, 0L) > this.period || DebugUtils.isTesting(context)) {
                        SharedPrefsCompat.apply(Sync.sSync.mPrefs.edit().putLong(Sync.PREF_TIMESTAMP + this.group, System.currentTimeMillis()));
                        sync(Sync.sSync);
                    }
                }
            }
        }

        abstract void sync(Sync sync);
    }

    private Sync(Context context) {
        this.mContext = context.getApplicationContext();
        this.mPrefs = context.getSharedPreferences(PREF_NAME, 0);
    }

    public static Sync getInstance(Context context) {
        if (sSync == null) {
            synchronized (Sync.class) {
                try {
                    if (sSync == null) {
                        sSync = new Sync(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sSync;
    }

    public String getString(String str, String str2) {
        return this.mPrefs.getString(str + ":" + str2, "");
    }

    @Override // com.xiaomi.push.service.NetworkListener
    public void onNetwrokAvaible() {
        if (this.isSyncing) {
            return;
        }
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (jCurrentTimeMillis - this.mLastSyncTime < MINIMUM_SYNC_DURATION) {
            return;
        }
        this.mLastSyncTime = jCurrentTimeMillis;
        this.isSyncing = true;
        ScheduledJobManager.getInstance(this.mContext).addOneShootJob(new Runnable() { // from class: com.xiaomi.push.service.Sync.1
            @Override // java.lang.Runnable
            public void run() {
                try {
                    Iterator it = Sync.this.mCurJobs.values().iterator();
                    while (it.hasNext()) {
                        ((SyncJob) it.next()).run();
                    }
                } catch (Exception e) {
                    MyLog.w("Sync job exception :" + e.getMessage());
                }
                Sync.this.isSyncing = false;
            }
        }, (int) (Math.random() * 10.0d));
    }

    public void put(String str, String str2, String str3) {
        SharedPrefsCompat.apply(sSync.mPrefs.edit().putString(str + ":" + str2, str3));
    }

    public void schedSync(SyncJob syncJob) {
        if (this.mCurJobs.putIfAbsent(syncJob.group, syncJob) == null) {
            ScheduledJobManager.getInstance(this.mContext).addOneShootJob(syncJob, ((int) (Math.random() * 30.0d)) + 10);
        }
    }
}
