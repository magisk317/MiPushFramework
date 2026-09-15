package com.xiaomi.push.service

/**
 * Policy-plan slice of [IPushRuntimeObserver]: the request/connection/slim
 * planning surface where vendor detects state and the product layer returns
 * the decision plan.
 */
interface IPushRuntimePolicyObserver {

    fun resolveKick(kickType: String?, kickReason: String?): PushKickPlan
    fun resolveBindResult(success: Boolean, errorType: String?, errorReason: String?): PushBindResultPlan
    fun resolveRedirect(hostsText: String?): PushRedirectPlan
    fun resolveCloseChannelPlan(request: PushServiceCloseRequest, packageChannelIds: List<String>): PushServiceClosePlan
    fun resolveResetConnectionPlan(channelId: String?, requestedSecurity: String?, client: PushClientsManager.ClientLoginInfo?, connectionReadable: Boolean): PushServiceResetConnectionPlan
    fun resolveRegisterAppPlan(packageName: String?, payload: ByteArray?, envChanged: Boolean, envType: Int, servicePackageName: String): PushServiceRegisterAppPlan
    fun resolveMiPushAppPlan(action: String?, packageName: String?, payload: ByteArray?, cacheMessage: Boolean): PushServiceMiPushAppPlan
    fun resolveMiPushPayloadDispatch(hasActiveChannel: Boolean, clientStatus: PushClientsManager.ClientStatus?, cacheIfUnavailable: Boolean): PushServiceMiPushPayloadDispatchPlan
    fun resolveChannelOpenPlan(hasNetwork: Boolean, isConnected: Boolean, clientStatus: PushClientsManager.ClientStatus?, shouldRebind: Boolean, request: PushChannelOpenRequest): PushChannelOpenPlan
    fun resolveConnectionAttemptPlan(isConnected: Boolean, isConnecting: Boolean): PushConnectionAttemptPlan
    fun resolveCheckAlivePlan(isConnected: Boolean, hasNetwork: Boolean): PushCheckAlivePlan
    fun resolveReconnectAttemptPlan(state: PushReconnectState, force: Boolean, isConnected: Boolean, hasReconnectionJob: Boolean): PushReconnectAttemptPlan
    
    fun resolveCandidateHosts(targetHost: String, fallbackHosts: List<String>): PushSocketHostSelectionPlan
    fun planFailureRetry(oldConnPoint: String?, newConnPoint: String?): PushSocketFailurePlan
    fun evaluateShortConnection(nowElapsed: Long, lastConnectedTime: Long, hasNetwork: Boolean, curShortConnCount: Int, networkInterval: Long, maxShortConnCount: Int): PushShortConnectionPlan
    
    fun planSlimHandshake(hasChallenge: Boolean, hasConfigMessage: Boolean): PushSlimHandshakePlan
    fun planSlimPayloadDispatch(payloadType: Int, cmd: String?, channelId: Int, subcmd: String?): PushSlimPayloadPlan
    fun resolveSlimInboundPlan(channelId: Int, cmd: String?): PushSlimInboundPlan
    fun planSlimPayload(packageName: String?, chid: String?, chidStatus: String?, binderStatus: String?): PushSlimPayloadPlan
    fun resolveSlimSendPingPlan(): PushSlimPingPlan
    fun planSlimWrite(serializedSize: Int, cmd: String?, currentCapacity: Int): PushSlimWritePlan
    
    fun planConnectionEvent(event: PushConnectionListenerEvent): PushConnectionStatusPlan
    fun buildGslbRequest(baseUrl: String, sdkVersion: Int, droidVersion: Int, model: String, incremental: String, miuiType: Int): PushGslbRequest


    fun resolveNetworkChangedPlan(
        hasNetwork: Boolean,
        isNetworkDeferred: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
        shouldResetOnWifi: Boolean,
        shouldCheckAlive: Boolean
    ): PushNetworkChangedPlan =
        PushConnectionPlanFactory.planNetworkChanged(hasNetwork, isNetworkDeferred, isConnected, isConnecting, shouldResetOnWifi, shouldCheckAlive)

    fun resolveScreenStatePlan(
        isScreenOn: Boolean,
        shouldFalldown: Boolean,
        alarmAlive: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean
    ): PushScreenStatePlan =
        PushConnectionPlanFactory.planScreenState(isScreenOn, shouldFalldown, alarmAlive, isConnected, isConnecting)

    fun resolveTimerPlan(
        shouldFalldown: Boolean,
        alarmAlive: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
        shouldCheckAlive: Boolean
    ): PushTimerPlan =
        PushConnectionPlanFactory.planTimer(shouldFalldown, alarmAlive, isConnected, isConnecting, shouldCheckAlive)

    fun resolveClientChangePlan(
        activeClientCount: Int,
        shouldUpdateAlarm: Boolean
    ): PushClientChangePlan =
        PushConnectionPlanFactory.planClientChange(activeClientCount, shouldUpdateAlarm)

    fun resolvePowerModePlan(
        isExtremePowerMode: Boolean,
        isSuperPowerMode: Boolean,
        isConnected: Boolean
    ): PushPowerModePlan =
        PushConnectionPlanFactory.planPowerModeChanged(isExtremePowerMode, isSuperPowerMode, isConnected)

    fun resolveShouldReconnectPlan(
        hasNetwork: Boolean,
        activeClientCount: Int,
        pushDisabled: Boolean,
        pushEnabled: Boolean,
        superPowerMode: Boolean,
        extremePowerMode: Boolean
    ): PushShouldReconnectPlan =
        PushConnectionPlanFactory.planShouldReconnect(
            hasNetwork, activeClientCount, pushDisabled, pushEnabled, superPowerMode, extremePowerMode,
        )
    fun decideBucketFetch(fetchBucketRequested: Boolean, lastFetchTimeMs: Long, nowMs: Long, minBucketFetchDurationMs: Long): PushBucketFetchPlan
    fun decideBucketReconnect(hasConnection: Boolean, currentHost: String?, candidateHosts: List<String>): PushBucketReconnectPlan
    
    fun resolveChannelInfoUpdateTarget(packageChannelIds: List<String>, requestedChannelId: String?, requestedUserId: String?, pushClientsManager: PushClientsManager): PushChannelInfoUpdateTarget
    fun applyChannelInfoUpdate(target: PushChannelInfoUpdateTarget, hasClientAttr: Boolean, clientAttr: String?, hasCloudAttr: Boolean, cloudAttr: String?): PushChannelInfoUpdateResult
    fun planRequestUrls(baseUrl: String, localUrls: List<String>?, reservedHosts: List<String>): HostRequestUrlsPlan
    fun planRefreshTargets(allHosts: List<String>, hostsWithFallback: Set<String>): HostRefreshTargetsPlan
    fun planRemoteFallbackRequest(nowMs: Long, lastRequestTimeMs: Long, failureCount: Long): HostRequestThrottlePlan
}
