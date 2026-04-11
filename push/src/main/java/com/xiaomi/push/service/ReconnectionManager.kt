package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.stats.StatsHandler
import com.xiaomi.xmsf.runtime.PushRuntime

class ReconnectionManager(
    private val pushService: XMPushService,
) {
    private var state = PushReconnectRuntime.initialState()

    fun onConnectSucceeded() {
        state = PushReconnectRuntime.onConnectSucceeded(System.currentTimeMillis())
        pushService.removeJobs(1)
        PushRuntime.observeChannelEvent(null, "reconnect_succeeded", "ReconnectionManager.onConnectSucceeded")
    }

    fun tryReconnect(forceReconnect: Boolean) {
        val reconnectPlan = PushReconnectRuntime.planReconnect(
            state,
            forceReconnect,
            pushService.shouldReconnect(),
            pushService.hasJob(1),
            System.currentTimeMillis(),
        )
        state = reconnectPlan.nextState
        PushRuntime.observeChannelEvent(null, reconnectPlan.eventAction, "ReconnectionManager.tryReconnect")

        when (reconnectPlan.action) {
            PushReconnectAction.SkipNoReconnect -> {
                MyLog.v("should not reconnect as no client or network.")
                return
            }

            PushReconnectAction.SkipExistingJob -> return

            PushReconnectAction.Immediate -> {
                pushService.removeJobs(1)
                pushService.executeJob(ConnectJob(pushService))
                return
            }

            else -> Unit
        }

        MyLog.w("schedule reconnect in ${reconnectPlan.delayMs}ms")
        pushService.executeJobDelayed(ConnectJob(pushService), reconnectPlan.delayMs.toLong())
        if (reconnectPlan.shouldDumpNativeNetInfo && StatsHandler.getInstance().isAllowStats) {
            NetworkCheckup.dumpNativeNetInfo()
        }
        if (reconnectPlan.shouldRunConnectivityTest) {
            NetworkCheckup.connectivityTest()
        }
    }
}
