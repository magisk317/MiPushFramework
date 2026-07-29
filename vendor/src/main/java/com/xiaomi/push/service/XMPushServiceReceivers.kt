package com.xiaomi.push.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.xiaomi.push.service.heartbeat.HeartbeatStrategyManager
import com.xiaomi.push.service.heartbeat.StableIntelligentHeartbeatStrategy
import com.xiaomi.push.service.timers.Alarm

class ConnectionChangeReceiver(
    private val service: XMPushServiceCore,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Stock f5 network callbacks refresh active network identity; u.e then updates the HB net
        // id. Without this hook the stable strategy never left its prepare()-time network.
        runCatching {
            HeartbeatStrategyManager.getInstance(context).onNetworkChanged(
                StableIntelligentHeartbeatStrategy.currentNetworkSnapshot(context),
            )
            Alarm.refreshPingInterval()
        }
        service.onStartCommand(intent, 0, 1)
    }
}

class ScreenStateReceiver(
    private val service: XMPushServiceCore,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        service.onStartCommand(intent, 0, 1)
    }
}

/**
 * Stock XMSF 7.4.67-C XMPushService$d0: MIUI wifi digest updates feed the heartbeat strategy.
 * JADX path: combined-jadx-1.5.6/.../com/xiaomi/push/service/XMPushService.java
 * (DIGEST_INFORMATION_CHANGED branch calling v.m(digest)).
 */
class WifiDigestReceiver(
    private val service: XMPushServiceCore,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        service.onStartCommand(intent, 0, 1)
    }
}

/**
 * Stock XMSF 7.4.67-C XMPushService$u: temporary short-HB keep window from system callers.
 * JADX path: combined-jadx-1.5.6/.../com/xiaomi/push/service/XMPushService.java
 * (USE_INTELLIGENT_HB branch calling v.n(days)).
 */
class IntelligentHbReceiver(
    private val service: XMPushServiceCore,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        service.onStartCommand(intent, 0, 1)
    }
}
