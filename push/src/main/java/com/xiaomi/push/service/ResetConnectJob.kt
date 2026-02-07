package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.network.HostManager
import com.xiaomi.smack.ConnectionConfiguration

class ResetConnectJob(
    private val xmPushService: XMPushService
) : XMPushService.Job(XMPushService.Job.TYPE_RESET_CONNECT) {

    override fun getDesc(): String = "reset connection"

    override fun process() {
        val fallback = HostManager.getInstance()
            .getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), false)
        JavaCalls.setField(fallback, "timestamp", 0)
        HostManager.getInstance().getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), true)
        xmPushService.disconnect(11, null)
        xmPushService.scheduleConnect(true)
    }
}
