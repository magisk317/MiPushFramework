package com.xiaomi.xmsf.push.service.receivers
import io.github.magisk317.mipush.common.Constants

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.smack.util.TrafficUtils
import io.github.magisk317.mipush.service.PushServiceStarter
import io.github.magisk317.mipush.runtime.PushRuntime

class NetworkStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PushRuntime.observeChannelEvent(
            packageName = context.packageName,
            action = intent?.action ?: "android.net.conn.CONNECTIVITY_CHANGE",
            source = "NetworkStatusReceiver.onReceive"
        )
        val serviceIntent = Intent("com.xiaomi.push.network_status_changed").apply {
            setClassName(Constants.SERVICE_APP_NAME, Constants.XM_PUSH_SERVICE_CLASS)
        }
        PushServiceStarter.start(context, serviceIntent)
        TrafficUtils.notifyNetworkChanage(context)
        if (Network.hasNetwork(context)) {
            PushRuntime.handleNetworkAvailable("NetworkStatusReceiver.onReceive")
        }
    }
}
