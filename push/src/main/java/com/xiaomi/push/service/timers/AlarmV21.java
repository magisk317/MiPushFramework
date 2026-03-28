package com.xiaomi.push.service.timers;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.XMJobService;
import com.xiaomi.push.service.timers.Alarm;
import com.xiaomi.smack.SmackConfiguration;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/timers/AlarmV21.class */
public class AlarmV21 implements Alarm.IAlarm {
    Context mContext;
    JobScheduler mJobScheduler;
    private boolean mStarted = false;

    AlarmV21(Context context) {
        this.mContext = context;
        this.mJobScheduler = (JobScheduler) context.getSystemService("jobscheduler");
    }

    @Override // com.xiaomi.push.service.timers.Alarm.IAlarm
    public boolean isAlive() {
        return this.mStarted;
    }

    void register(long j) {
        JobInfo.Builder builder = new JobInfo.Builder(1, new ComponentName(this.mContext.getPackageName(), XMJobService.class.getName()));
        builder.setMinimumLatency(j);
        builder.setOverrideDeadline(j);
        builder.setRequiredNetworkType(1);
        builder.setPersisted(false);
        MyLog.v("schedule Job = " + builder.build().getId() + " in " + j);
        this.mJobScheduler.schedule(builder.build());
    }

    @Override // com.xiaomi.push.service.timers.Alarm.IAlarm
    public void registerPing(boolean z) {
        if (z || this.mStarted) {
            long pingInteval = SmackConfiguration.getPingInteval();
            long jElapsedRealtime = pingInteval;
            if (z) {
                stop();
                jElapsedRealtime = pingInteval - (SystemClock.elapsedRealtime() % pingInteval);
            }
            this.mStarted = true;
            register(jElapsedRealtime);
        }
    }

    @Override // com.xiaomi.push.service.timers.Alarm.IAlarm
    public void stop() {
        this.mStarted = false;
        this.mJobScheduler.cancel(1);
    }
}
