package com.xiaomi.push.service;

import android.app.Service;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.push.service.timers.Alarm;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/XMJobService.class */
public class XMJobService extends Service {
    static Service sServiceInstance = null;
    private IBinder mJobBinder = null;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/XMJobService$JobServiceImpl.class */
    static class JobServiceImpl extends JobService {
        private static final int MSG_JOB_STARTED = 1;
        Binder binder;
        private Handler mHandler;

        /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/XMJobService$JobServiceImpl$JobHandler.class */
        private static class JobHandler extends Handler {
            JobService service;

            JobHandler(JobService jobService) {
                super(jobService.getMainLooper());
                this.service = jobService;
            }

            @Override // android.os.Handler
            public void handleMessage(Message message) {
                switch (message.what) {
                    case 1:
                        JobParameters jobParameters = (JobParameters) message.obj;
                        MyLog.w("Job finished " + jobParameters.getJobId());
                        this.service.jobFinished(jobParameters, false);
                        if (jobParameters.getJobId() == 1) {
                            Alarm.registerPing(false);
                        }
                        break;
                }
            }
        }

        JobServiceImpl(Service service) {
            this.binder = null;
            this.binder = (Binder) JavaCalls.callMethod(this, "onBind", new Intent());
            JavaCalls.callMethod(this, "attachBaseContext", service);
        }

        @Override // android.app.job.JobService
        public boolean onStartJob(JobParameters jobParameters) {
            MyLog.w("Job started " + jobParameters.getJobId());
            Intent intent = new Intent(this, (Class<?>) XMPushService.class);
            intent.setAction(PushServiceConstants.ACTION_TIMER);
            intent.setPackage(getPackageName());
            startService(intent);
            if (this.mHandler == null) {
                this.mHandler = new JobHandler(this);
            }
            Handler handler = this.mHandler;
            handler.sendMessage(Message.obtain(handler, 1, jobParameters));
            return true;
        }

        @Override // android.app.job.JobService
        public boolean onStopJob(JobParameters jobParameters) {
            MyLog.w("Job stop " + jobParameters.getJobId());
            return false;
        }
    }

    static Service getRunningService() {
        return sServiceInstance;
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        IBinder iBinder = this.mJobBinder;
        return iBinder != null ? iBinder : new Binder();
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 21) {
            this.mJobBinder = new JobServiceImpl(this).binder;
        }
        sServiceInstance = this;
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        sServiceInstance = null;
    }
}
