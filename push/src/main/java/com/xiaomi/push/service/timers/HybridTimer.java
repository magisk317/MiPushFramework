package com.xiaomi.push.service.timers;

import android.content.Context;
import com.xiaomi.channel.commonutils.misc.DateTimeHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/timers/HybridTimer.class */
class HybridTimer extends AlarmManagerTimer {
    private static int pingInterval = DateTimeHelper.HOUR_IN_MS;

    public HybridTimer(Context context) {
        super(context);
    }

    @Override // com.xiaomi.push.service.timers.AlarmManagerTimer
    public long getPingInteval() {
        return pingInterval;
    }
}
