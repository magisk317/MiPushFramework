package com.xiaomi.push.mpcd;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.push.mpcd.job.AppIsInstalledCollectionJob;
import com.xiaomi.push.mpcd.job.BroadcastActionCollectionjob;
import com.xiaomi.push.mpcd.job.StorageCollectionJob;
import com.xiaomi.push.mpcd.job.UploadJob;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.xmpush.thrift.ConfigKey;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/JobController.class */
public class JobController {
    private static final int FIRST_COLLECT_JOB_DELAY = 172800000;
    private static final String KEY_FIRST_TRY_COLLECT_TIMESTAMP = "first_try_ts";
    private static final int MIN_DELAY = 0;
    private static volatile JobController instance;
    private Context context;

    private JobController(Context context) {
        this.context = context;
    }

    public static JobController getInstance(Context context) {
        if (instance == null) {
            synchronized (JobController.class) {
                try {
                    if (instance == null) {
                        instance = new JobController(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return instance;
    }

    private int makeSurePeriodNotTooSmall(int i) {
        return Math.max(60, i);
    }

    private boolean scheduleActivityTSJob() {
        if (Build.VERSION.SDK_INT < 14) {
            return false;
        }
        try {
            Context context = this.context;
            (context instanceof Application ? (Application) context : (Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacksImpl(this.context, String.valueOf(System.currentTimeMillis() / 1000)));
            return true;
        } catch (Exception e) {
            MyLog.e(e);
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void trySchedulerCollectJob() {
        ScheduledJobManager scheduledJobManager = ScheduledJobManager.getInstance(this.context);
        OnlineConfig onlineConfig = OnlineConfig.getInstance(this.context);
        SharedPreferences sharedPreferences = this.context.getSharedPreferences("mipush_extra", 0);
        long jCurrentTimeMillis = System.currentTimeMillis();
        long j = sharedPreferences.getLong(KEY_FIRST_TRY_COLLECT_TIMESTAMP, jCurrentTimeMillis);
        if (j == jCurrentTimeMillis) {
            sharedPreferences.edit().putLong(KEY_FIRST_TRY_COLLECT_TIMESTAMP, jCurrentTimeMillis).commit();
        }
        if (Math.abs(jCurrentTimeMillis - j) < 172800000) {
            return;
        }
        uploadDC(onlineConfig, scheduledJobManager, false);
        if (onlineConfig.getBooleanValue(ConfigKey.StorageCollectionSwitch.getValue(), true)) {
            int iMakeSurePeriodNotTooSmall = makeSurePeriodNotTooSmall(onlineConfig.getIntValue(ConfigKey.StorageCollectionFrequency.getValue(), 86400));
            scheduledJobManager.addRepeatJob(new StorageCollectionJob(this.context, iMakeSurePeriodNotTooSmall), iMakeSurePeriodNotTooSmall, 0);
        }
        boolean booleanValue = onlineConfig.getBooleanValue(ConfigKey.AppIsInstalledCollectionSwitch.getValue(), false);
        String stringValue = onlineConfig.getStringValue(ConfigKey.AppIsInstalledList.getValue(), null);
        if (booleanValue && !TextUtils.isEmpty(stringValue)) {
            int iMakeSurePeriodNotTooSmall2 = makeSurePeriodNotTooSmall(onlineConfig.getIntValue(ConfigKey.AppIsInstalledCollectionFrequency.getValue(), 86400));
            scheduledJobManager.addRepeatJob(new AppIsInstalledCollectionJob(this.context, iMakeSurePeriodNotTooSmall2, stringValue), iMakeSurePeriodNotTooSmall2, 0);
        }
        if (onlineConfig.getBooleanValue(ConfigKey.BroadcastActionCollectionSwitch.getValue(), true)) {
            int iMakeSurePeriodNotTooSmall3 = makeSurePeriodNotTooSmall(onlineConfig.getIntValue(ConfigKey.BroadcastActionCollectionFrequency.getValue(), 900));
            scheduledJobManager.addRepeatJob(new BroadcastActionCollectionjob(this.context, iMakeSurePeriodNotTooSmall3), iMakeSurePeriodNotTooSmall3, 0);
        }
        if (onlineConfig.getBooleanValue(ConfigKey.ActivityTSSwitch.getValue(), false)) {
            scheduleActivityTSJob();
        }
        uploadDC(onlineConfig, scheduledJobManager, true);
    }

    private void uploadDC(OnlineConfig onlineConfig, ScheduledJobManager scheduledJobManager, boolean z) {
        if (onlineConfig.getBooleanValue(ConfigKey.UploadSwitch.getValue(), true)) {
            UploadJob uploadJob = new UploadJob(this.context);
            if (z) {
                scheduledJobManager.addRepeatJob(uploadJob, makeSurePeriodNotTooSmall(onlineConfig.getIntValue(ConfigKey.UploadFrequency.getValue(), 86400)));
            } else {
                scheduledJobManager.addOneShootJob((ScheduledJobManager.Job) uploadJob);
            }
        }
    }

    public void schedulerJob() {
        ScheduledJobManager.getInstance(this.context).addOneShootJob(new Runnable() { // from class: com.xiaomi.push.mpcd.JobController.1
            @Override // java.lang.Runnable
            public void run() {
                JobController.this.trySchedulerCollectJob();
            }
        });
    }
}
