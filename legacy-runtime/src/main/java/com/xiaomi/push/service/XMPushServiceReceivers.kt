package com.xiaomi.push.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ConnectionChangeReceiver(
    private val service: XMPushService,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        service.onStartCommand(intent, 0, 1)
    }
}

class ScreenStateReceiver(
    private val service: XMPushService,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        service.onStartCommand(intent, 0, 1)
    }
}
