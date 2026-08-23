package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.network.HostManager
import com.xiaomi.smack.ConnectionConfiguration

class ResetConnectJob(
    private val pushAction: IPushServiceAction
) : XMPushServiceCore.Job(XMPushServiceJob.TYPE_RESET_CONNECT) {

    override fun getDesc(): String = "reset connection"

    override fun process() {
        pushAction.runtimeObserver.onChannelEvent(
            packageName = null,
            event = "reset_connect_refresh",
            reason = "ResetConnectJob.process"
        )
        val fallback = HostManager.getInstance()
            .getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), false)
        val fallbackHostCount = fallback?.getHosts()?.size ?: 0
        if (fallback != null) {
            JavaCalls.setField(fallback, "timestamp", 0)
        }
        val refreshedFallback = HostManager.getInstance()
            .getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), true)
        val refreshedHostCount = refreshedFallback?.getHosts()?.size ?: 0
        ReconnectDebugLog.w(
            "reset_connect_hosts fallbackPresent=${fallback != null} " +
                "fallbackHosts=$fallbackHostCount refreshedPresent=${refreshedFallback != null} " +
                "refreshedHosts=$refreshedHostCount"
        )
        pushAction.runtimeObserver.onConnectionStateChanged(
            stateName = "Disconnected",
            reason = "reset_connect_job",
            host = pushAction.currentConnection?.host ?: "",
            message = "reset_connect_job"
        )
        pushAction.disconnect(11, null)
        pushAction.scheduleConnect(true)
    }
}
