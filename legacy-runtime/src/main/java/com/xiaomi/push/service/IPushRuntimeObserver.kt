package com.xiaomi.push.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import com.xiaomi.slim.Blob
import com.xiaomi.smack.Connection
import com.xiaomi.smack.packet.Packet

/**
 * Interface defined for the foundation layer to notify the product layer.
 * This acts as the bridge for all runtime policy decisions and event reporting.
 */
interface IPushRuntimeObserver {
    // --- Connection Events ---
    fun onConnectionStateChanged(stateName: String, reason: String, host: String?, message: String)
    fun onConnectionStatusChanged(status: ConnectionStatus)
    fun reconnectionFailed(connection: Connection, error: Exception)
    fun reconnectionSuccessful(connection: Connection)
    fun connectionClosed(connection: Connection, reason: Int, error: Exception?)
    fun connectionStarted(connection: Connection)
    fun notifyConnectionError(reason: Int, exc: Exception?)
    fun requestConnection(source: String, reason: String)
    fun notifyConnectionFailed(activeClients: Any) {}
    
    // --- Lifecycle & System ---
    fun onServiceCreated(service: android.app.Service) {}
    fun postOnCreate() {}
    fun onServiceDestroy() {}
    fun networkChanged() {}
    fun startForegroundService() {}
    fun getMIID(): String?
    fun isMiuiStableVersion(): Boolean? = null
    fun isMiuiDevelopmentVersion(): Boolean? = null
    fun isMiuiGlobalBuild(): Boolean? = null
    fun sendBroadcast(intent: Intent) {}
    fun applyStoredAccountEnvironment(context: Context): MIPushAccount? = null
    fun envType(context: Context): Int = 0
    fun persistCreationLog(context: Context) {}
    fun shouldRunConnectivityTest(activeCount: Int, lastCheckTimeMs: Long, testHostsCount: Int): Boolean = false
    fun shouldDumpNativeNetInfo(connection: Connection?): Boolean = false
    fun createHostManager(context: Context, hostFilter: Any?, httpGet: Any?, userId: String): Any? = null

    // --- Account & Registration ---
    fun loadAccount(context: Context, source: String): MIPushAccount? = null
    fun registerAccount(context: Context, packageName: String, appId: String, appToken: String, source: String): MIPushAccount? = null
    fun resolveAccountUrl(region: String?, oneBoxBuild: Boolean, oneBoxHost: String, sandBoxBuild: Boolean): String
    fun onRegistrationStateChanged(packageName: String, state: PushRegistrationState, reason: String, message: String)
    fun onRegistrationResult(packageName: String, success: Boolean, source: String, reason: String) {}
    fun cacheRegistrationRequest(packageName: String, payload: ByteArray)
    fun clearAccount(context: Context, packageName: String) {}
    fun observeUnregistration(packageName: String, state: PushRegistrationState) {}
    fun cacheRegistrationTask(packageName: String, intent: Intent, source: String, reason: String, timestampMs: Long) {}
    fun dispatchRegistrationTasks(source: String, dispatcher: Any? = null) {}
    fun clearRegistrationTasks(packageName: String) {}
    fun onAccountEvent(packageName: String, event: String) {}
    fun attachAccountClient(client: Any) {}

    /**
     * Notifies the product layer about an intent received from an application.
     * This is used for recording events like registration requests and message sent events.
     */
    fun onApplicationIntentReceived(intent: Intent) {}
    
    // --- Channel & Message Management ---
    fun onChannelEvent(packageName: String?, event: String, reason: String)
    fun onChannelStateChanged(packageName: String?, chid: String, userId: String?, session: String?, state: PushChannelState, reason: String, reasonCode: Int?, reasonMsg: String?)
    fun syncChannelTracker(reason: String)
    fun notifyRegisterError(errorCode: Int, errorMessage: String, notifier: IPendingPacketErrorNotifier)
    fun cachePendingMessage(packageName: String, payload: ByteArray)
    fun addPendingMessage(packageName: String, payload: ByteArray) {}

    // --- Client Monitoring ---
    fun shouldNotifyClient(client: PushClientsManager.ClientLoginInfo, type: Int, reasonCode: Int, reasonMessage: String? = null, errorType: String? = null): Boolean = true
    fun computeNotifyDelay(client: PushClientsManager.ClientLoginInfo, type: Int = 0, reasonCode: Int = 0, reasonMessage: String? = null, errorType: String? = null): Long = 0L
    fun configureClientChangeListener(context: Context, manager: PushClientsManager) {}

    // --- Message Processing ---
    fun onPayloadReceived(context: Context, payload: ByteArray?, size: Long, source: String)
    fun processMIPushMessage(payload: ByteArray, trafficBytes: Long)
    fun postProcessMIPushMessage(targetPackage: String, payload: ByteArray, intent: Intent)
    fun onSendMessage(packageName: String, size: Int) = Unit
    fun notifyPacketArrival(chid: String, blob: Blob)
    fun notifyPacketArrival(chid: String, packet: Packet)
    fun constructBindBlob(client: Any): Blob? = null
    fun constructUnbindBlob(chid: String, userId: String): Blob? = null
    fun processPendingMessages(source: String, sender: IPendingPacketSender)
    fun processPendingRegistrationRequests(source: String, sender: IPendingPacketSender)
    fun removeCachedMsgId(msgId: String) {}
    fun isDuplicate(packageName: String, msgId: String): Boolean = false
    fun packToContainer(payload: ByteArray): Any? = null
    fun packToContainer(client: Any, packageName: String): Any? = null
    fun shouldSendBroadcast(context: Context, packageName: String, container: Any, metaInfo: Any?): Boolean = true
    
    fun processMIPushIntent(intent: Intent): Any?
    val notificationHandler: IPushNotificationHandler? get() = null
    fun onNotificationEvent(packageName: String?, event: String, source: String) {}
    fun rebuildRestoredNotification(context: Context, notification: Notification): Notification? = null

    // --- Policy Resolution Plans ---
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
    fun resolveSlimInboundPlan(channelId: Int, cmd: String?): PushSlimInboundPlan
    fun planSlimPayload(packageName: String?, chid: String?, chidStatus: String?, binderStatus: String?): PushSlimPayloadPlan
    fun resolveSlimSendPingPlan(): PushSlimPingPlan
    fun planSlimWrite(isPing: Boolean): PushSlimWritePlan
    
    fun planConnectionEvent(event: PushConnectionListenerEvent): PushConnectionStatusPlan
    fun buildGslbRequest(baseUrl: String, sdkVersion: Int, droidVersion: Int, model: String, incremental: String, miuiType: Int): PushGslbRequest
    fun decideBucketFetch(fetchBucketRequested: Boolean, lastFetchTimeMs: Long, nowMs: Long, minBucketFetchDurationMs: Long): PushBucketFetchPlan
    fun decideBucketReconnect(hasConnection: Boolean, currentHost: String?, candidateHosts: List<String>): PushBucketReconnectPlan
    
    fun resolveChannelInfoUpdateTarget(packageChannelIds: List<String>, requestedChannelId: String?, requestedUserId: String?, pushClientsManager: PushClientsManager): PushChannelInfoUpdateTarget
    fun applyChannelInfoUpdate(target: PushChannelInfoUpdateTarget, hasClientAttr: Boolean, clientAttr: String?, hasCloudAttr: Boolean, cloudAttr: String?): PushChannelInfoUpdateResult
    fun planRequestUrls(baseUrl: String, localUrls: List<String>?, reservedHosts: List<String>): HostRequestUrlsPlan
    fun planRefreshTargets(allHosts: List<String>, hostsWithFallback: Set<String>): HostRefreshTargetsPlan
    fun planRemoteFallbackRequest(nowMs: Long, lastRequestTimeMs: Long, failureCount: Long): HostRequestThrottlePlan
    
    fun resetAllClients(clients: Any, reason: Int) {}
    fun onClientStatusChanged(client: Any, type: Int, reasonCode: Int, reasonMessage: String?, errorType: String?) {}
    fun ping(connection: Connection): Boolean = false
    fun preparePacket(packet: Packet, packageName: String, session: String?, isConnected: Boolean): Packet? = packet
}
