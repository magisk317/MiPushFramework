package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.stats.StatsHandler

class ReconnectionManager(
    private val pushAction: IPushServiceAction,
) {
    private var state = PushReconnectState(0, 0, 500, 0L)

    fun onConnectSucceeded() {
        val previousState = state
        state = PushReconnectState(0, 0, 500, System.currentTimeMillis())
        pushAction.removeJobs(1)
        pushAction.runtimeObserver.onChannelEvent(null, "reconnect_succeeded", "ReconnectionManager.onConnectSucceeded")
        ReconnectDebugLog.w(
            "reconnect_state_reset previousAttempts=${previousState.attempts} " +
                "previousShortLive=${previousState.shortLiveConnCount} " +
                "previousCurDelay=${previousState.curDelay}"
        )
    }

    fun tryReconnect(forceReconnect: Boolean) {
        val previousState = state
        val reconnectPlan = pushAction.runtimeObserver.resolveReconnectAttemptPlan(
            state,
            forceReconnect,
            pushAction.isConnected,
            pushAction.hasJob(1),
        )
        state = reconnectPlan.nextState
        pushAction.runtimeObserver.onChannelEvent(null, reconnectPlan.eventAction, "ReconnectionManager.tryReconnect")
        ReconnectDebugLog.w(
            "reconnect_plan action=${reconnectPlan.action} event=${reconnectPlan.eventAction} " +
                "delayMs=${reconnectPlan.delayMs} force=$forceReconnect " +
                "connected=${pushAction.isConnected} pendingJob=${pushAction.hasJob(1)} " +
                "attempts=${previousState.attempts}->${reconnectPlan.nextState.attempts} " +
                "shortLive=${reconnectPlan.nextState.shortLiveConnCount} " +
                "curDelay=${reconnectPlan.nextState.curDelay}"
        )

        when (reconnectPlan.action) {
            PushReconnectAction.SkipNoReconnect -> {
                MyLog.v("should not reconnect as no client or network.")
                return
            }

            PushReconnectAction.SkipExistingJob -> return

            PushReconnectAction.Immediate -> {
                pushAction.replaceJobs(XMPushServiceJob.TYPE_CONNECT, ConnectJob(pushAction))
                return
            }

            PushReconnectAction.Schedule,
            PushReconnectAction.Delayed -> {
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
