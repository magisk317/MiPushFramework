package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.magisk317.service.XMPushServiceLifecycleBridge
import com.xiaomi.network.HostManager
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.xmsf.runtime.PushConnectionState
import com.xiaomi.xmsf.runtime.PushRuntime

class ResetConnectJob(
    private val xmPushService: XMPushService
) : XMPushService.Job(XMPushServiceJob.TYPE_RESET_CONNECT) {

    override fun getDesc(): String = "reset connection"

    override fun process() {
        XMPushServiceLifecycleBridge.ensureCreated(xmPushService)
        PushRuntime.observeChannelEvent(
            packageName = null,
            action = "reset_connect_refresh",
            source = "ResetConnectJob.process"
        )
        val fallback = HostManager.getInstance()
            .getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), false)
        JavaCalls.setField(fallback, "timestamp", 0)
        HostManager.getInstance().getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), true)
        PushRuntime.observeConnectionState(
            state = PushConnectionState.Disconnected,
            source = "ResetConnectJob.process",
            host = xmPushService.currentConnection?.host,
            reason = "reset_connect_job"
        )
        xmPushService.disconnect(11, null)
        xmPushService.scheduleConnect(true)
    }
}
