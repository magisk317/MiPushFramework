package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.mipush.sdk.PushServiceClient
import com.xiaomi.smack.util.TrafficUtils

class NetworkStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val serviceIntent = Intent(context, com.xiaomi.push.service.XMPushService::class.java)
        serviceIntent.action = "com.xiaomi.push.network_status_changed"
        ContextCompat.startForegroundService(context, serviceIntent)
        TrafficUtils.notifyNetworkChanage(context)
        if (Network.hasNetwork(context) && PushServiceClient.getInstance(context).isProvisioned) {
            PushServiceClient.getInstance(context).processRegisterTask()
        }
    }
}
