package io.github.magisk317.mipush.bridge

import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.service.MIPushAccountUtils
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushReconnectAttemptPlan
import com.xiaomi.push.service.PushReconnectState
import com.xiaomi.push.service.PushShouldReconnectPlan
import com.xiaomi.push.service.ReconnectDebugLog
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge

internal class MiPushRuntimeReconnectCoordinator(
    private val appContext: android.content.Context,
    private val observerState: MiPushRuntimeObserverState,
    private val accountClientCoordinator: MiPushRuntimeAccountClientCoordinator,
) {
    fun resolveShouldReconnectPlan(
        hasNetwork: Boolean,
        activeClientCount: Int,
        pushDisabled: Boolean,
        pushEnabled: Boolean,
        superPowerMode: Boolean,
        extremePowerMode: Boolean,
    ): PushShouldReconnectPlan {
        val account = MIPushAccountUtils.getMIPushAccount(appContext)
        val hasAccount = account != null
        val effectiveCount = if (hasAccount && activeClientCount == 0) 1 else activeClientCount
        val plan = MiPushRuntimePolicyExecutionAdapter.planShouldReconnect(
            hasNetwork,
            effectiveCount,
            pushDisabled,
            pushEnabled,
            superPowerMode,
            extremePowerMode,
        )
        ReconnectDebugLog.w(
            "should_reconnect_policy result=${plan.shouldReconnect} hasNetwork=$hasNetwork " +
                "hasAccount=$hasAccount activeClients=$activeClientCount " +
                "effectiveClients=$effectiveCount pushDisabled=$pushDisabled " +
                "pushEnabled=$pushEnabled superPowerMode=$superPowerMode " +
                "extremePowerMode=$extremePowerMode"
        )
        return plan
    }

    fun resolveReconnectAttemptPlan(
        state: PushReconnectState,
        force: Boolean,
        isConnected: Boolean,
        hasReconnectionJob: Boolean,
    ): PushReconnectAttemptPlan {
        val hasNetwork = Network.hasNetwork(appContext)
        val account = MIPushAccountUtils.getMIPushAccount(appContext)
        val hasAccount = account != null
        val activeClientCount = PushClientsManager.getInstance().getActiveClientCount()
        val effectiveClients = activeClientCount > 0 || hasAccount
        val allowedByPolicy = hasNetwork && (effectiveClients || force)
        ReconnectDebugLog.w(
            "reconnect_policy hasNetwork=$hasNetwork hasAccount=$hasAccount " +
                "activeClients=$activeClientCount effectiveClients=$effectiveClients " +
                "allowed=$allowedByPolicy force=$force connected=$isConnected " +
                "pendingJob=$hasReconnectionJob attempts=${state.attempts} " +
                "shortLive=${state.shortLiveConnCount} curDelay=${state.curDelay}"
        )

        if (hasAccount && activeClientCount == 0) {
            val service = observerState.service() ?: XMPushServiceLifecycleBridge.peekService()
            if (service != null) {
                accountClientCoordinator.attachAccount(account, service, PushClientsManager.getInstance())
            }
        }

        return MiPushRuntimePolicyExecutionAdapter.planReconnect(
            state = state,
            forceImmediate = force,
            currentlyConnected = isConnected,
            allowedByPolicy = allowedByPolicy,
            hasPendingConnectJob = hasReconnectionJob,
            nowMs = System.currentTimeMillis(),
        )
    }
}
