package com.xiaomi.channel.commonutils.misc;

import android.content.Context;
import android.content.SharedPreferences;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/ScheduledJobManager.class */
public class ScheduledJobManager {
    private static final int CORE_THREAD_POOL_SIZE = 1;
    private static final String SP_KEY_PREFIX = "last_job_time";
    private static final String SP_NAME = "mipush_extra";
    private static volatile ScheduledJobManager instance;
    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private Map<String, ScheduledFuture> jobFutureMap = new HashMap();
    private Object mapLock = new Object();
    private SharedPreferences preferences;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/ScheduledJobManager$Job.class */
    public static abstract class Job implements Runnable {
        public abstract String getJobId();
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/ScheduledJobManager$JobWrapper.class */
    private static class JobWrapper implements Runnable {
        Job job;

        public JobWrapper(Job job) {
            this.job = job;
        }

        void onJobDone() {
        }

        void onJobStart() {
        }

        @Override // java.lang.Runnable
        public void run() {
            onJobStart();
            this.job.run();
            onJobDone();
        }
    }

    private ScheduledJobManager(Context context) {
        this.preferences = context.getSharedPreferences("mipush_extra", 0);
    }

    private ScheduledFuture getFutureByJobId(Job job) {
        ScheduledFuture scheduledFuture;
        synchronized (this.mapLock) {
            scheduledFuture = this.jobFutureMap.get(job.getJobId());
        }
        return scheduledFuture;
    }

    public static ScheduledJobManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ScheduledJobManager.class) {
                try {
                    if (instance == null) {
                        instance = new ScheduledJobManager(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return instance;
    }

    private static String getJobKey(String str) {
        return SP_KEY_PREFIX + str;
    }

    public void addOneShootJob(Runnable runnable) {
        addOneShootJob(runnable, 0);
    }

    public void addOneShootJob(Runnable runnable, int i) {
        this.executor.schedule(runnable, i, TimeUnit.SECONDS);
    }

    public boolean addOneShootJob(Job job) {
        return addOneShootJob(job, 0);
    }

    public boolean addOneShootJob(Job job, int i) {
        if (job == null || getFutureByJobId(job) != null) {
            return false;
        }
        ScheduledFuture<?> scheduledFutureSchedule = this.executor.schedule(new JobWrapper(job) { // from class: com.xiaomi.channel.commonutils.misc.ScheduledJobManager.2
            @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.JobWrapper
            void onJobDone() {
                synchronized (ScheduledJobManager.this.mapLock) {
                    ScheduledJobManager.this.jobFutureMap.remove(this.job.getJobId());
                }
            }
        }, i, TimeUnit.SECONDS);
        synchronized (this.mapLock) {
            this.jobFutureMap.put(job.getJobId(), scheduledFutureSchedule);
        }
        return true;
    }

    public boolean addRepeatJob(Job job, int i) {
        return addRepeatJob(job, i, 0);
    }

    public boolean addRepeatJob(Job job, int i, int i2) {
        return addRepeatJob(job, i, i2, false);
    }

    public boolean addRepeatJob(Job job, int i, int i2, final boolean z) {
        if (job == null || getFutureByJobId(job) != null) {
            return false;
        }
        final String jobKey = getJobKey(job.getJobId());
        JobWrapper jobWrapper = new JobWrapper(job) { // from class: com.xiaomi.channel.commonutils.misc.ScheduledJobManager.1
            @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.JobWrapper
            void onJobDone() {
                if (z) {
                    return;
                }
                ScheduledJobManager.this.preferences.edit().putLong(jobKey, System.currentTimeMillis()).commit();
            }

            @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.JobWrapper
            void onJobStart() {
                super.onJobStart();
            }
        };
        if (!z) {
            long jAbs = Math.abs(System.currentTimeMillis() - this.preferences.getLong(jobKey, 0L)) / 1000;
            if (jAbs < i - i2) {
                i2 = (int) (((long) i) - jAbs);
            }
        }
        try {
            ScheduledFuture<?> scheduledFutureScheduleAtFixedRate = this.executor.scheduleAtFixedRate(jobWrapper, i2, i, TimeUnit.SECONDS);
            synchronized (this.mapLock) {
                this.jobFutureMap.put(job.getJobId(), scheduledFutureScheduleAtFixedRate);
            }
            return true;
        } catch (Exception e) {
            MyLog.e(e);
            return true;
        }
    }

    public boolean cancelJob(String str) {
        synchronized (this.mapLock) {
            ScheduledFuture scheduledFuture = this.jobFutureMap.get(str);
            if (scheduledFuture == null) {
                return false;
            }
            this.jobFutureMap.remove(str);
            return scheduledFuture.cancel(false);
        }
    }
}
