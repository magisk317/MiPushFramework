package io.github.magisk317.mipush.bridge

import com.xiaomi.push.service.HostRefreshTargetsPlan
import com.xiaomi.push.service.HostRequestThrottlePlan
import com.xiaomi.push.service.HostRequestUrlsPlan
import com.xiaomi.push.service.PushBucketFetchPlan
import com.xiaomi.push.service.PushBucketReconnectPlan
import com.xiaomi.push.service.PushChannelInfoUpdateResult
import com.xiaomi.push.service.PushChannelInfoUpdateTarget
import com.xiaomi.push.service.PushChannelOpenPlan
import com.xiaomi.push.service.PushCheckAlivePlan
import com.xiaomi.push.service.PushClientChangePlan
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushConnectionAttemptPlan
import com.xiaomi.push.service.PushConnectionListenerEvent
import com.xiaomi.push.service.PushConnectionStatusPlan
import com.xiaomi.push.service.PushGslbRequest
import com.xiaomi.push.service.PushKickPlan
import com.xiaomi.push.service.PushRedirectPlan
import com.xiaomi.push.service.PushServiceClosePlan
import com.xiaomi.push.service.PushServiceMiPushAppPlan
import com.xiaomi.push.service.PushServiceMiPushPayloadDispatchPlan
import com.xiaomi.push.service.PushServiceResetConnectionPlan
import com.xiaomi.push.service.PushShortConnectionPlan
import com.xiaomi.push.service.PushSlimHandshakePlan
import com.xiaomi.push.service.PushSlimInboundPlan
import com.xiaomi.push.service.PushSlimPayloadAction
import com.xiaomi.push.service.PushSlimPayloadPlan
import com.xiaomi.push.service.PushSlimPingPlan
import com.xiaomi.push.service.PushSlimWritePlan
import com.xiaomi.push.service.PushSocketFailurePlan
import com.xiaomi.push.service.PushSocketHostSelectionPlan
import com.xiaomi.network.HostManagerRuntime
import io.github.magisk317.mipush.service.runtime.PushChannelInfoRuntime
import io.github.magisk317.mipush.service.runtime.PushChannelOpenRuntime
import io.github.magisk317.mipush.service.runtime.PushHostRuntime
import io.github.magisk317.mipush.service.runtime.PushServiceConnectionRuntime
import io.github.magisk317.mipush.service.runtime.PushServiceIntentRuntime
import io.github.magisk317.mipush.service.runtime.PushSlimConnectionRuntime
import io.github.magisk317.mipush.service.runtime.PushSlimStreamRuntime
import io.github.magisk317.mipush.service.runtime.PushSocketConnectionRuntime
import io.github.magisk317.mipush.service.runtime.PushPacketSyncRuntime

internal object MiPushRuntimePolicyExecutionAdapter {
    fun resolveKick(kickType: String?, kickReason: String?): PushKickPlan =
        PushPacketSyncRuntime.resolveKick(kickType, kickReason)

    fun resolveRedirect(hostsText: String?): PushRedirectPlan =
        PushPacketSyncRuntime.resolveRedirect(hostsText)

    fun resolveCloseChannelPlan(
        request: com.xiaomi.push.service.PushServiceCloseRequest,
        packageChannelIds: List<String>,
    ): PushServiceClosePlan =
        PushServiceIntentRuntime.resolveCloseChannelPlan(request, packageChannelIds)

    fun resolveResetConnectionPlan(
        channelId: String?,
        requestedSecurity: String?,
        client: PushClientsManager.ClientLoginInfo?,
        connectionReadable: Boolean,
    ): PushServiceResetConnectionPlan =
        PushServiceIntentRuntime.decideResetConnection(channelId, requestedSecurity, client, connectionReadable)

    fun resolveMiPushAppPlan(
        action: String?,
        packageName: String?,
        payload: ByteArray?,
        cacheMessage: Boolean,
    ): PushServiceMiPushAppPlan =
        PushServiceIntentRuntime.resolveMiPushAppPlan(action, packageName, payload, cacheMessage)

    fun resolveMiPushPayloadDispatch(
        hasActiveChannel: Boolean,
        clientStatus: PushClientsManager.ClientStatus?,
        cacheIfUnavailable: Boolean,
    ): PushServiceMiPushPayloadDispatchPlan =
        PushServiceIntentRuntime.decideMiPushPayloadDispatch(hasActiveChannel, clientStatus, cacheIfUnavailable)

    fun resolveChannelOpenPlan(
        hasNetwork: Boolean,
        isConnected: Boolean,
        clientStatus: PushClientsManager.ClientStatus?,
        shouldRebind: Boolean,
        request: com.xiaomi.push.service.PushChannelOpenRequest,
    ): PushChannelOpenPlan =
        PushChannelOpenRuntime.decideOpenPlan(hasNetwork, isConnected, clientStatus, shouldRebind)

    fun resolveConnectionAttemptPlan(isConnected: Boolean, isConnecting: Boolean): PushConnectionAttemptPlan =
        PushServiceConnectionRuntime.planConnect(isConnecting, isConnected)

    fun resolveCheckAlivePlan(isConnected: Boolean, hasNetwork: Boolean): PushCheckAlivePlan =
        PushServiceConnectionRuntime.planCheckAlive(isConnected, hasNetwork)

    fun resolveCandidateHosts(targetHost: String, fallbackHosts: List<String>): PushSocketHostSelectionPlan =
        PushSocketConnectionRuntime.resolveCandidateHosts(targetHost, fallbackHosts)

    fun planFailureRetry(oldConnPoint: String?, newConnPoint: String?): PushSocketFailurePlan =
        PushSocketConnectionRuntime.planFailureRetry(oldConnPoint, newConnPoint)

    fun evaluateShortConnection(
        nowElapsed: Long,
        lastConnectedTime: Long,
        hasNetwork: Boolean,
        curShortConnCount: Int,
        networkInterval: Long,
        maxShortConnCount: Int,
    ): PushShortConnectionPlan =
        PushSocketConnectionRuntime.evaluateShortConnection(
            nowElapsedMs = nowElapsed,
            lastConnectedTime = lastConnectedTime,
            hasNetwork = hasNetwork,
            curShortConnCount = curShortConnCount,
            shortConnectionThresholdMs = networkInterval,
            maxShortConnCount = maxShortConnCount,
        )

    fun planSlimHandshake(hasChallenge: Boolean, hasConfigMessage: Boolean): PushSlimHandshakePlan =
        PushSlimStreamRuntime.planHandshake(hasChallenge, hasConfigMessage)

    fun resolveSlimInboundPlan(channelId: Int, cmd: String?): PushSlimInboundPlan =
        PushSlimConnectionRuntime.planInboundBlob(channelId, cmd)

    fun resolveSlimSendPingPlan(): PushSlimPingPlan =
        PushSlimConnectionRuntime.planSendPing()

    fun planSlimPayload(
        packageName: String?,
        chid: String?,
        chidStatus: String?,
        binderStatus: String?,
    ): PushSlimPayloadPlan =
        PushSlimPayloadPlan(
            action = if (binderStatus.isNullOrBlank()) PushSlimPayloadAction.DeliverBlob else PushSlimPayloadAction.None,
            eventAction = "slim_payload_resolved",
        )

    fun planSlimWrite(serializedSize: Int, cmd: String?, currentCapacity: Int): PushSlimWritePlan =
        PushSlimStreamRuntime.planWrite(serializedSize, cmd, currentCapacity)

    fun planConnectionEvent(event: PushConnectionListenerEvent): PushConnectionStatusPlan {
        val state = when (event) {
            PushConnectionListenerEvent.Connected,
            PushConnectionListenerEvent.ReconnectionSuccessful -> com.xiaomi.push.service.PushConnectionState.Connected
            PushConnectionListenerEvent.Connecting,
            PushConnectionListenerEvent.ConnectionStarted -> com.xiaomi.push.service.PushConnectionState.Connecting
            PushConnectionListenerEvent.Disconnected,
            PushConnectionListenerEvent.ReconnectionFailed,
            PushConnectionListenerEvent.ConnectionClosed -> com.xiaomi.push.service.PushConnectionState.Disconnected
            else -> null
        }
        return PushConnectionStatusPlan(
            eventAction = "connection_event_${event.name.lowercase()}",
            connectionState = state,
        )
    }

    fun buildGslbRequest(
        baseUrl: String,
        sdkVersion: Int,
        droidVersion: Int,
        model: String,
        incremental: String,
        miuiType: Int,
    ): PushGslbRequest =
        PushHostRuntime.buildGslbRequest(baseUrl, sdkVersion, droidVersion, model, incremental, miuiType)

    fun decideBucketFetch(
        fetchBucketRequested: Boolean,
        lastFetchTimeMs: Long,
        nowMs: Long,
        minBucketFetchDurationMs: Long,
    ): PushBucketFetchPlan =
        PushHostRuntime.decideBucketFetch(fetchBucketRequested, lastFetchTimeMs, nowMs, minBucketFetchDurationMs)

    fun decideBucketReconnect(
        hasConnection: Boolean,
        currentHost: String?,
        candidateHosts: List<String>,
    ): PushBucketReconnectPlan =
        PushHostRuntime.decideBucketReconnect(hasConnection, currentHost, candidateHosts)

    fun resolveChannelInfoUpdateTarget(
        packageChannelIds: List<String>,
        requestedChannelId: String?,
        requestedUserId: String?,
        pushClientsManager: PushClientsManager,
    ): PushChannelInfoUpdateTarget =
        PushChannelInfoRuntime.resolveUpdateTarget(
            packageChannelIds,
            requestedChannelId,
            requestedUserId,
            pushClientsManager,
        )

    fun applyChannelInfoUpdate(
        target: PushChannelInfoUpdateTarget,
        hasClientAttr: Boolean,
        clientAttr: String?,
        hasCloudAttr: Boolean,
        cloudAttr: String?,
    ): PushChannelInfoUpdateResult =
        PushChannelInfoRuntime.applyUpdate(target, hasClientAttr, clientAttr, hasCloudAttr, cloudAttr)

    fun planRequestUrls(baseUrl: String, localUrls: List<String>?, reservedHosts: List<String>): HostRequestUrlsPlan {
        val plan = HostManagerRuntime.planRequestUrls(baseUrl, localUrls, reservedHosts)
        return HostRequestUrlsPlan(plan.urls)
    }

    fun planRefreshTargets(allHosts: List<String>, hostsWithFallback: Set<String>): HostRefreshTargetsPlan {
        val plan = HostManagerRuntime.planRefreshTargets(allHosts, hostsWithFallback)
        return HostRefreshTargetsPlan(plan.targetHosts)
    }

    fun planRemoteFallbackRequest(
        nowMs: Long,
        lastRequestTimeMs: Long,
        failureCount: Long,
    ): HostRequestThrottlePlan {
        val plan = HostManagerRuntime.planRemoteFallbackRequest(nowMs, lastRequestTimeMs, failureCount)
        return HostRequestThrottlePlan(plan.shouldRequest, plan.nextTimestampMs)
    }

    fun resolveClientChangePlan(activeClientCount: Int, shouldUpdateAlarm: Boolean): PushClientChangePlan =
        PushClientChangePlan(
            shouldUpdateAlarm = shouldUpdateAlarm,
            shouldDisconnect = false,
            eventAction = if (activeClientCount <= 0) {
                "client_change_keep_alive"
            } else {
                "client_change_update_alarm"
            },
        )
}
