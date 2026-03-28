package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.smack.util.TrafficUtils
import com.magisk317.service.PushServiceStarter
import com.xiaomi.xmsf.runtime.PushRuntime
import com.xiaomi.xmsf.runtime.PushRuntimeComponents

class NetworkStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PushRuntime.observeChannelEvent(
            packageName = context.packageName,
            action = intent?.action ?: "android.net.conn.CONNECTIVITY_CHANGE",
            source = "NetworkStatusReceiver.onReceive"
        )
        val serviceIntent = PushRuntimeComponents.newLegacyMainServiceIntent(
            context,
            "com.xiaomi.push.network_status_changed"
        )
        PushServiceStarter.start(context, serviceIntent)
        TrafficUtils.notifyNetworkChanage(context)
        if (Network.hasNetwork(context)) {
            PushRuntime.handleNetworkAvailable("NetworkStatusReceiver.onReceive")
        }
    }
}
