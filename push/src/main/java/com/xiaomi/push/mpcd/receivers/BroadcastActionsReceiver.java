package com.xiaomi.push.mpcd.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.xiaomi.push.mpcd.IntentHandler;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/receivers/BroadcastActionsReceiver.class */
public class BroadcastActionsReceiver extends BroadcastReceiver {
    private IntentHandler mHandler;

    public BroadcastActionsReceiver(IntentHandler intentHandler) {
        this.mHandler = intentHandler;
    }

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        IntentHandler intentHandler = this.mHandler;
        if (intentHandler != null) {
            intentHandler.handle(context, intent);
        }
    }
}
