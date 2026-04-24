package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.stats.StatsHandler

class ReconnectionManager(
    private val pushAction: IPushServiceAction,
) {
    private var state = PushReconnectState(0, 0, 500, 0L)

    fun onConnectSucceeded() {
        state = PushReconnectState(0, 0, 500, System.currentTimeMillis())
        pushAction.removeJobs(1)
        pushAction.runtimeObserver.onChannelEvent(null, "reconnect_succeeded", "ReconnectionManager.onConnectSucceeded")
    }

    fun tryReconnect(forceReconnect: Boolean) {
        val reconnectPlan = pushAction.runtimeObserver.resolveReconnectAttemptPlan(
            state,
            forceReconnect,
            pushAction.isConnected,
            pushAction.hasJob(1),
        )
        state = reconnectPlan.nextState
        pushAction.runtimeObserver.onChannelEvent(null, reconnectPlan.eventAction, "ReconnectionManager.tryReconnect")

        when (reconnectPlan.action) {
            PushReconnectAction.SkipNoReconnect -> {
                MyLog.v("should not reconnect as no client or network.")
                return
            }

            PushReconnectAction.SkipExistingJob -> return

            PushReconnectAction.Immediate -> {
                pushAction.removeJobs(1)
                pushAction.executeJob(ConnectJob(pushAction))
                return
            }

            PushReconnectAction.Schedule -> {
                // Continue to schedule logic below
            }

            else -> return
        }

        MyLog.w("schedule reconnect in ${reconnectPlan.delayMs}ms")
        pushAction.executeJobDelayed(ConnectJob(pushAction), reconnectPlan.delayMs)
        if (reconnectPlan.shouldDumpNativeNetInfo && StatsHandler.getInstance().isAllowStats()) {
            NetworkCheckup.dumpNativeNetInfo()
        }
        if (reconnectPlan.shouldRunConnectivityTest) {
            NetworkCheckup.connectivityTest(pushAction.runtimeObserver)
        }
    }
}
