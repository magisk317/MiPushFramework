package io.github.magisk317.mipush.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.smack.util.TrafficUtils
import io.github.magisk317.mipush.service.PushServiceStarter
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRuntimeComponents
import io.github.magisk317.xposed.logging.MagiskOtel

class NetworkStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val startedAt = System.nanoTime()
        val action = intent?.action ?: "android.net.conn.CONNECTIVITY_CHANGE"
        PushRuntime.observeChannelEvent(
            packageName = context.packageName,
            action = action,
            source = "NetworkStatusReceiver.onReceive"
        )
        val serviceIntent = PushRuntimeComponents.newCoreServiceIntent(
            context,
            "com.xiaomi.push.network_status_changed"
        )
        PushServiceStarter.start(context, serviceIntent)
        TrafficUtils.notifyNetworkChanage(context)
        val hasNetwork = Network.hasNetwork(context)
        if (hasNetwork) {
            PushRuntime.handleNetworkAvailable("NetworkStatusReceiver.onReceive")
        }
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "push.network",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to durationMs.toString(),
                "process" to "main",
                "action" to action,
                "network_available" to hasNetwork.toString(),
            ),
            statusOk = true,
        )
    }
}
