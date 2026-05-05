package com.xiaomi.stats

import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.XMPushServiceJob
import com.xiaomi.push.thrift.ChannelStatsType

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/oa/a.java
 * Stock class name is obfuscated as oa.a; this file keeps the deobfuscated com.xiaomi.stats.BindTracker API.
 */
internal class BindTracker(
    private val pushAction: IPushServiceAction,
    private val client: PushClientsManager.ClientLoginInfo
) : PushClientsManager.ClientLoginInfo.ClientStatusListener {
    private var connection = pushAction.currentConnection
    private var reason = 0
    private var tracked = false
    private var status = PushClientsManager.ClientStatus.binding

    private fun done() {
        untrack()
        if (tracked && reason != 11) {
            var statsEvent: com.xiaomi.push.thrift.StatsEvent? = StatsHandler.getInstance().createStatsEvent()
            when (status) {
                PushClientsManager.ClientStatus.unbind -> {
                    when (reason) {
                        17 -> statsEvent?.setType(ChannelStatsType.BIND_TCP_READ_TIMEOUT.value)
                        21 -> statsEvent?.setType(ChannelStatsType.BIND_TIMEOUT.value)
                        else -> {
                            try {
                                val typeWrapper = StatsAnalyser.fromBind(StatsHandler.getContext()?.getCaughtException())
                                statsEvent?.apply {
                                    setType(typeWrapper.type?.value ?: 0)
                                    annotation = typeWrapper.annotation
                                }
                            } catch (e: NullPointerException) {
                                statsEvent = null
                            }
                        }
                    }
                }
                PushClientsManager.ClientStatus.binded -> {
                    statsEvent?.setType(ChannelStatsType.BIND_SUCCESS.value)
                }
                else -> {}
            }
            statsEvent?.let { event ->
                val conn = connection
                if (conn != null) {
                    event.host = conn.host ?: ""
                }
                event.user = client.userId
                event.value = 1
                try {
                    event.chid = client.chid.toByte()
                } catch (e: NumberFormatException) {
                    // Ignore
                }
                StatsHandler.getInstance().add(event)
            }
        }
    }

    private fun untrack() {
        client.removeClientStatusListener(this)
    }

    override fun onChange(previousStatus: PushClientsManager.ClientStatus, currentStatus: PushClientsManager.ClientStatus, reason: Int) {
        if (!tracked && previousStatus == PushClientsManager.ClientStatus.binding) {
            status = currentStatus
            this.reason = reason
            tracked = true
        }
        pushAction.executeJob(object : XMPushServiceJob(4) {
            override fun getDesc(): String = "Handling bind stats"
            override fun process() = done()
        })
    }

    fun track() {
        client.addClientStatusListener(this)
        connection = pushAction.currentConnection
    }
}
