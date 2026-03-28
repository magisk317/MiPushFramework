package com.xiaomi.push.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

final class ConnectionChangeReceiver extends BroadcastReceiver {
    private final XMPushService service;

    ConnectionChangeReceiver(XMPushService xMPushService) {
        this.service = xMPushService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        this.service.onStart(intent, 1);
    }
}

final class ScreenStateReceiver extends BroadcastReceiver {
    private final XMPushService service;

    ScreenStateReceiver(XMPushService xMPushService) {
        this.service = xMPushService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        this.service.onStart(intent, 1);
    }
}
