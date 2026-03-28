package com.xiaomi.mipush.sdk;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.lang.ref.WeakReference;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/BaseService.class */
public abstract class BaseService extends Service {
    private TimeoutHandler mHandler;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/BaseService$TimeoutHandler.class */
    public static class TimeoutHandler extends Handler {
        private static final long TIME_OUT = 1000;
        private static final int TIME_OUT_KILL_SELF = 1001;
        private WeakReference<BaseService> mWRService;

        public TimeoutHandler(WeakReference<BaseService> weakReference) {
            this.mWRService = weakReference;
        }

        @Override // android.os.Handler
        public void handleMessage(Message message) {
            BaseService baseService;
            switch (message.what) {
                case 1001:
                    WeakReference<BaseService> weakReference = this.mWRService;
                    if (weakReference != null && (baseService = weakReference.get()) != null) {
                        MyLog.v("TimeoutHandler" + baseService.toString() + "  kill self");
                        if (!baseService.hasJob()) {
                            baseService.stopSelf();
                        } else {
                            MyLog.v("TimeoutHandler has job");
                            sendEmptyMessageDelayed(1001, TIME_OUT);
                        }
                        break;
                    }
                    break;
            }
        }

        public void reSendTimeoutMessage() {
            if (hasMessages(1001)) {
                removeMessages(1001);
            }
            sendEmptyMessageDelayed(1001, TIME_OUT);
        }
    }

    protected abstract boolean hasJob();

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public void onStart(Intent intent, int i) {
        super.onStart(intent, i);
        if (this.mHandler == null) {
            this.mHandler = new TimeoutHandler(new WeakReference(this));
        }
        this.mHandler.reSendTimeoutMessage();
    }
}
