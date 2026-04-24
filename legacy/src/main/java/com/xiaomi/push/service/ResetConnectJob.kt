package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.network.HostManager
import com.xiaomi.smack.ConnectionConfiguration

class ResetConnectJob(
    private val pushAction: IPushServiceAction
) : XMPushService.Job(XMPushServiceJob.TYPE_RESET_CONNECT) {

    override fun getDesc(): String = "reset connection"

    override fun process() {
        pushAction.postOnCreate()
        pushAction.runtimeObserver.onChannelEvent(
            packageName = null,
            event = "reset_connect_refresh",
            reason = "ResetConnectJob.process"
        )
        val fallback = HostManager.getInstance()
            .getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), false)
        if (fallback != null) {
            JavaCalls.setField(fallback, "timestamp", 0)
        }
        HostManager.getInstance().getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), true)
        pushAction.runtimeObserver.onConnectionStateChanged(
            stateName = "Disconnected",
            reason = "reset_connect_job",
            host = pushAction.currentConnection?.getHost() ?: "",
            message = "reset_connect_job"
        )
        pushAction.disconnect(11, null)
        pushAction.scheduleConnect(true)
    }
}
