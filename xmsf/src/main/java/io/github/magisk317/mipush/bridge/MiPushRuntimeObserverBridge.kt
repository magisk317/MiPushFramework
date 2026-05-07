package io.github.magisk317.mipush.bridge

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.misc.BuildSettings
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.network.HostFilter
import com.xiaomi.network.HostManager
import com.xiaomi.network.HostManagerRuntime
import com.xiaomi.push.service.ConnectionStatus
import com.xiaomi.push.service.PushChannelState
import com.xiaomi.push.service.PushConnectionState
import com.xiaomi.push.service.PushRegistrationState
import com.xiaomi.push.service.HostRefreshTargetsPlan
import com.xiaomi.push.service.HostRequestThrottlePlan
import com.xiaomi.push.service.HostRequestUrlsPlan
import com.xiaomi.push.service.IPendingPacketErrorNotifier
import com.xiaomi.push.service.IPendingPacketSender
import com.xiaomi.push.service.IPushNotificationHandler
import com.xiaomi.push.service.IPushRuntimeObserver
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.MIPushAccount
import com.xiaomi.push.service.MIPushAccountUtils
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PushBindResultPlan
import com.xiaomi.push.service.PushBucketFetchPlan
import com.xiaomi.push.service.PushBucketReconnectPlan
import com.xiaomi.push.service.PushChannelInfoUpdateResult
import com.xiaomi.push.service.PushChannelInfoUpdateTarget
import com.xiaomi.push.service.PushChannelOpenPlan
import com.xiaomi.push.service.PushCheckAlivePlan
import com.xiaomi.push.service.PushConnectionAttemptPlan
import com.xiaomi.push.service.PushConnectionListenerEvent
import com.xiaomi.push.service.PushConnectionStatusPlan
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushGslbRequest
import com.xiaomi.push.service.PushKickPlan
import com.xiaomi.push.service.PushReconnectAttemptPlan
import com.xiaomi.push.service.PushReconnectState
import com.xiaomi.push.service.PushRedirectPlan
import com.xiaomi.push.service.PushServiceClosePlan
import com.xiaomi.push.service.PushServiceMiPushAppPlan
import com.xiaomi.push.service.PushServiceMiPushPayloadDispatchPlan
import com.xiaomi.push.service.PushServiceRegisterAppPlan
import com.xiaomi.push.service.PushServiceRegisterAppAction
import com.xiaomi.push.service.PushServiceResetConnectionPlan
import com.xiaomi.push.service.PushShortConnectionPlan
import com.xiaomi.push.service.PushSlimHandshakePlan
import com.xiaomi.push.service.PushSlimInboundPlan
import com.xiaomi.push.service.PushSlimPingPlan
import com.xiaomi.push.service.PushSlimPayloadAction
import com.xiaomi.push.service.PushSlimPayloadPlan
import com.xiaomi.push.service.PushSlimWritePlan
import com.xiaomi.push.service.PushSocketFailurePlan
import com.xiaomi.push.service.PushSocketHostSelectionPlan
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.XMPushService
import com.xiaomi.push.service.XMPushServiceProxy
import com.xiaomi.slim.Blob
import com.xiaomi.smack.Connection
import com.xiaomi.smack.packet.Packet
import dagger.hilt.android.EntryPointAccessors
import io.github.magisk317.mipush.common.compat.NotificationCompatBridge
import io.github.magisk317.mipush.push.hook.HookTraceCompat
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import com.xiaomi.push.sdk.PushMessageProcessor
import com.xiaomi.push.sdk.PushMessageProcessorEntryPoint
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.service.ForegroundHelper
import io.github.magisk317.mipush.service.runtime.MIPushAccountUtilsRuntime
import io.github.magisk317.mipush.service.runtime.PushChannelInfoRuntime
import io.github.magisk317.mipush.service.runtime.PushChannelOpenRuntime
import io.github.magisk317.mipush.service.runtime.PushClientsStateSupport
import io.github.magisk317.mipush.service.runtime.PushHostRuntime
import io.github.magisk317.mipush.service.runtime.PushReconnectRuntime
import io.github.magisk317.mipush.service.runtime.PushServiceConnectionRuntime
import io.github.magisk317.mipush.service.runtime.PushServiceIntentRuntime
import io.github.magisk317.mipush.service.runtime.PushSlimConnectionRuntime
import io.github.magisk317.mipush.service.runtime.PushSlimStreamRuntime
import io.github.magisk317.mipush.service.runtime.PushSocketConnectionRuntime
import io.github.magisk317.mipush.service.runtime.RegistrationThrottle
import io.github.magisk317.mipush.service.runtime.NetworkCheckupRuntime
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.service.runtime.PushPacketSyncRuntime
import org.json.JSONException
import java.io.IOException

class MiPushRuntimeObserverBridge(private val context: Context) : IPushRuntimeObserver {
    private val appContext: Context = context.applicationContext ?: context

    init {
        XMPushService.observer = this
    }

    private fun frameworkProcessor(): PushMessageProcessor {
        return EntryPointAccessors.fromApplication(
            appContext,
            PushMessageProcessorEntryPoint::class.java
        ).pushMessageProcessor()
    }

    private fun toRuntimeConnectionState(stateName: String): PushConnectionState {
        return when (stateName) {
            "Connected", ConnectionStatus.connected.name -> PushConnectionState.Connected
            "Connecting", ConnectionStatus.connecting.name -> PushConnectionState.Connecting
            "Disconnecting" -> PushConnectionState.Disconnecting
            else -> PushConnectionState.Disconnected
        }
    }

    override fun onServiceCreated(service: android.app.Service) {
        if (service is com.xiaomi.push.service.XMPushService) {
            io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.ensureCreated(service)
        }
    }

    override fun onServiceDestroy() {
        io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.onDestroy(null)
    }

    override fun onConnectionStateChanged(stateName: String, reason: String, host: String?, message: String) {
        PushRuntime.observeConnectionState(
            state = toRuntimeConnectionState(stateName),
            source = reason.ifBlank { "MiPushRuntimeObserverBridge.onConnectionStateChanged" },
            host = host,
            reason = message
        )
    }

    override fun onConnectionStatusChanged(status: ConnectionStatus) {
        io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.onConnectionStatusChanged(status)
        PushRuntime.observeConnectionState(
            state = toRuntimeConnectionState(status.name),
            source = "MiPushRuntimeObserverBridge.onConnectionStatusChanged",
            host = null,
            reason = status.name
        )
    }

    override fun reconnectionFailed(connection: Connection, error: Exception) {
        PushRuntime.observeChannelEvent(null, "reconnect_failed", "MiPushRuntimeObserverBridge.reconnectionFailed")
        PushRuntime.observeConnectionState(
            state = PushConnectionState.Disconnected,
            source = "MiPushRuntimeObserverBridge.reconnectionFailed",
            host = connection.host,
            reason = error.message
        )
    }

    override fun reconnectionSuccessful(connection: Connection) {
        PushRuntime.observeChannelEvent(null, "reconnect_success", "MiPushRuntimeObserverBridge.reconnectionSuccessful")
        MyMIPushNotificationHelper.markNotificationSessionStarted("MiPushRuntimeObserverBridge.reconnectionSuccessful")
        PushRuntime.observeConnectionState(
            state = PushConnectionState.Connected,
            source = "MiPushRuntimeObserverBridge.reconnectionSuccessful",
            host = connection.host,
            reason = "reconnected"
        )
    }

    override fun connectionClosed(connection: Connection, reason: Int, error: Exception?) {
        PushRuntime.observeChannelEvent(null, "connection_closed", "MiPushRuntimeObserverBridge.connectionClosed")
        PushRuntime.observeConnectionState(
            state = PushConnectionState.Disconnected,
            source = "MiPushRuntimeObserverBridge.connectionClosed",
            host = connection.host,
            reason = error?.message ?: reason.toString()
        )
    }

    override fun connectionStarted(connection: Connection) {
        PushRuntime.observeChannelEvent(null, "connection_started", "MiPushRuntimeObserverBridge.connectionStarted")
        MyMIPushNotificationHelper.markNotificationSessionStarted("MiPushRuntimeObserverBridge.connectionStarted")
        PushRuntime.observeConnectionState(
            state = PushConnectionState.Connecting,
            source = "MiPushRuntimeObserverBridge.connectionStarted",
            host = connection.host,
            reason = "started"
        )
    }

    override fun notifyConnectionError(reason: Int, exc: Exception?) {
        PushRuntime.observeChannelEvent(null, "connection_error", "MiPushRuntimeObserverBridge.notifyConnectionError:$reason")
    }

    override fun requestConnection(source: String, reason: String) {
        PushRuntime.requestConnection(source, reason)
    }

    override fun startForegroundService() {
        (context as? Service)?.let { ForegroundHelper(it).startForeground() }
    }

    override fun getMIID(): String? = "0"

    override fun sendBroadcast(intent: Intent) {
        appContext.sendBroadcast(intent)
    }

    override fun applyStoredAccountEnvironment(context: Context): MIPushAccount? {
        val account = MIPushAccountUtils.getMIPushAccount(context.applicationContext) ?: return null
        BuildSettings.setEnvType(account.envType)
        PushRuntime.observeAccountEvent("account_env_applied", "MiPushRuntimeObserverBridge.applyStoredAccountEnvironment")
        return account
    }

    override fun envType(context: Context): Int {
        return MIPushAccountUtils.getMIPushAccount(context.applicationContext)?.envType ?: 0
    }

    override fun shouldRunConnectivityTest(activeCount: Int, lastCheckTimeMs: Long, testHostsCount: Int): Boolean {
        return NetworkCheckupRuntime.shouldRunConnectivityTest(
            activeCount = activeCount,
            nowMs = System.currentTimeMillis(),
            lastCheckTimeMs = lastCheckTimeMs,
            allowStats = true,
            testHostsCount = testHostsCount
        )
    }

    override fun createHostManager(context: Context, hostFilter: Any?, httpGet: Any?, userId: String): Any? {
        val filter = hostFilter as? HostFilter
        val getter = httpGet as? HostManager.HttpGet ?: return null
        return HostManager(context, filter, getter, userId)
    }

    override fun loadAccount(context: Context, source: String): MIPushAccount? {
        val account = MIPushAccountUtils.getMIPushAccount(context.applicationContext)
        PushRuntime.observeAccountEvent(
            action = if (account == null) "account_missing" else "account_loaded",
            source = source
        )
        return account
    }

    override fun registerAccount(
        context: Context,
        packageName: String,
        appId: String,
        appToken: String,
        source: String
    ): MIPushAccount? {
        return try {
            MIPushAccountUtils.register(context, packageName, appId, appToken, this).also { account ->
                PushRuntime.observeAccountEvent(
                    action = if (account == null) "account_register_empty" else "account_registered",
                    source = source
                )
            }
        } catch (e: IOException) {
            PushRuntime.observeAccountEvent("account_register_failed_io", source)
            null
        } catch (e: JSONException) {
            PushRuntime.observeAccountEvent("account_register_failed_json", source)
            null
        }
    }

    override fun resolveAccountUrl(region: String?, oneBoxBuild: Boolean, oneBoxHost: String, sandBoxBuild: Boolean): String {
        return MIPushAccountUtilsRuntime.resolveAccountUrl(region, oneBoxBuild, oneBoxHost, sandBoxBuild)
    }

    override fun onRegistrationStateChanged(packageName: String, state: PushRegistrationState, reason: String, message: String) {
        PushRuntime.observeRegistrationState(packageName, state, reason, message)
    }

    override fun onApplicationIntentReceived(intent: Intent) {
        MiPushRuntimeBridge.onApplicationIntentReceived(appContext, intent)
    }

    override fun onRegistrationResult(packageName: String, success: Boolean, source: String, reason: String) {
        PushRuntime.observeRegistrationResult(packageName, success, source, reason)
    }

    override fun cacheRegistrationRequest(packageName: String, payload: ByteArray) {
        PushRuntimePendingPacketStore.cacheRegistrationRequest(packageName, payload)
    }

    override fun clearAccount(context: Context, packageName: String) {
        MIPushAccountUtils.clearAccount(context)
        PushRuntime.observeAccountEvent("account_cleared", "MiPushRuntimeObserverBridge.clearAccount:$packageName")
    }

    override fun observeUnregistration(packageName: String, state: PushRegistrationState) {
        PushRuntime.observeUnregistration(packageName, "MiPushRuntimeObserverBridge.observeUnregistration", state.name)
    }

    override fun cacheRegistrationTask(packageName: String, intent: Intent, source: String, reason: String, timestampMs: Long) {
        PushRuntimeRegistrationTaskStore.cache(packageName, intent, source, reason, timestampMs)
    }

    override fun dispatchRegistrationTasks(source: String, dispatcher: Any?) {
        PushRuntimeRegistrationTaskStore.dispatchAll(source) { _, intent ->
            runCatching {
                appContext.startService(Intent(intent))
                true
            }.getOrDefault(false)
        }
    }

    override fun clearRegistrationTasks(packageName: String) {
        PushRuntimeRegistrationTaskStore.clear(packageName)
    }

    override fun onAccountEvent(packageName: String, event: String) {
        PushRuntime.observeAccountEvent(event, "MiPushRuntimeObserverBridge.onAccountEvent:$packageName")
    }

    override fun attachAccountClient(client: Any) {
        // Obsolete, managed dynamically by the product layer now
    }

    override fun onChannelEvent(packageName: String?, event: String, reason: String) {
        PushRuntime.observeChannelEvent(packageName, event, reason)
    }

    override fun onChannelStateChanged(
        packageName: String?,
        chid: String,
        userId: String?,
        session: String?,
        state: PushChannelState,
        reason: String,
        reasonCode: Int?,
        reasonMsg: String?
    ) {
        PushRuntime.observeChannelState(
            packageName = packageName,
            channelId = chid,
            userId = userId,
            session = session,
            state = state,
            source = reason,
            reasonCode = reasonCode,
            reasonMessage = reasonMsg
        )
    }

    override fun syncChannelTracker(reason: String) {
        PushRuntimeChannelTracker.syncNow(reason)
    }

    override fun notifyRegisterError(errorCode: Int, errorMessage: String, notifier: IPendingPacketErrorNotifier) {
        PushRuntimePendingPacketStore.notifyRegisterError(
            errorCode = errorCode,
            errorMessage = errorMessage,
            notifier = { packageName, payload, code, message ->
                com.xiaomi.push.service.MIPushClientManager.notifyError(appContext, packageName, payload, code, message)
                notifier.notifyError(code, message)
            }
        )
    }

    override fun cachePendingMessage(packageName: String, payload: ByteArray) {
        PushRuntimePendingPacketStore.addPendingMessage(packageName, payload)
    }

    override fun addPendingMessage(packageName: String, payload: ByteArray) {
        PushRuntimePendingPacketStore.addPendingMessage(packageName, payload)
    }

    override fun shouldNotifyClient(
        client: PushClientsManager.ClientLoginInfo,
        type: Int,
        reasonCode: Int,
        reasonMessage: String?,
        errorType: String?
    ): Boolean {
        return io.github.magisk317.mipush.service.runtime.PushClientStatusSupport.shouldNotifyClient(
            client,
            type,
            reasonCode,
            errorType
        )
    }

    override fun computeNotifyDelay(
        client: PushClientsManager.ClientLoginInfo,
        type: Int,
        reasonCode: Int,
        reasonMessage: String?,
        errorType: String?
    ): Long {
        return io.github.magisk317.mipush.service.runtime.PushClientStatusSupport.computeNotifyDelay(client).toLong()
    }

    override fun onClientStatusChanged(client: Any, type: Int, reasonCode: Int, reasonMessage: String?, errorType: String?) {
        val info = client as? PushClientsManager.ClientLoginInfo ?: return
        io.github.magisk317.mipush.service.runtime.PushClientStatusSupport.notifyClientStatus(
            info,
            type,
            reasonCode,
            reasonMessage,
            errorType
        )
    }

    override fun onPayloadReceived(context: Context, payload: ByteArray?, size: Long, source: String) {
        payload?.let { MiPushRuntimeBridge.onPayloadFromServer(context, it, size, source) }
    }

    override fun processMIPushMessage(payload: ByteArray, trafficBytes: Long) {
        // Don't re-enter onPayloadFromServer here — it was already called by onPayloadReceived
        // via ClientEventDispatcher.notifyPacketArrival, which marks the message as seen.
        // Re-entering would cause shouldProcessPayloadIdentity to return false (duplicate),
        // so notifyPushMessage would never be called.
        MyMIPushNotificationHelper.notifyPushMessage(appContext, payload)
    }

    override fun postProcessMIPushMessage(targetPackage: String, payload: ByteArray, intent: Intent) {
        HookTraceCompat.processIntent(intent)
        frameworkProcessor().forwardToTargetApplication(appContext, payload)
    }

    override fun notifyPacketArrival(chid: String, blob: Blob) {
        HookTraceCompat.processIntent(Intent("blob:$chid"))
    }

    override fun notifyPacketArrival(chid: String, packet: Packet) {
        HookTraceCompat.processIntent(Intent("packet:$chid"))
    }

    override fun constructBindBlob(client: Any): Blob? {
        // Blob construction is handled internally inside legacy-runtime now, no delegation needed
        return null
    }

    override fun constructUnbindBlob(chid: String, userId: String): Blob? {
        // Blob construction is handled internally inside legacy-runtime now, no delegation needed
        return null
    }

    override fun processPendingMessages(source: String, sender: IPendingPacketSender) {
        val pushAction = XMPushServiceProxy.get() ?: return
        PushRuntimePendingPacketStore.processPendingMessages(source) { packageName, payload ->
            MIPushHelper.sendPacket(pushAction, appContext, packageName, payload)
        }
    }

    override fun processPendingRegistrationRequests(source: String, sender: IPendingPacketSender) {
        val pushAction = XMPushServiceProxy.get() ?: return
        PushRuntimePendingPacketStore.processPendingRegistrationRequests(source) { packageName, payload ->
            MIPushHelper.sendPacket(pushAction, appContext, packageName, payload)
        }
    }

    override fun removeCachedMsgId(msgId: String) {
        // Managed dynamically via Dispatcher
    }

    override fun packToContainer(payload: ByteArray): Any? {
        return XMPushUtils.packToContainer(payload)
    }

    override fun shouldSendBroadcast(context: Context, packageName: String, container: Any, metaInfo: Any?): Boolean {
        val pushService = io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.peekService() ?: return true
        val xmContainer = container as? com.xiaomi.xmpush.thrift.XmPushActionContainer ?: return true
        val xmMetaInfo = metaInfo as? com.xiaomi.xmpush.thrift.PushMetaInfo ?: return true
        return io.github.magisk317.mipush.push.hook.ExplicitHookBridge.shouldSendBroadcast(pushService, packageName, xmContainer, xmMetaInfo)
    }

    override fun isDuplicate(packageName: String, msgId: String): Boolean {
        val pushService = io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.peekService() ?: return false
        return io.github.magisk317.mipush.push.hook.ExplicitHookBridge.isDuplicateMessage(pushService, packageName, msgId)
    }

    override fun processMIPushIntent(intent: Intent): Any? {
        // Intent processing is routed differently in XMPushUtils now
        return null
    }

    override val notificationHandler: IPushNotificationHandler = object : IPushNotificationHandler {
        override fun handleNotification(packageName: String, payload: ByteArray): Boolean {
            MyMIPushNotificationHelper.notifyPushMessage(appContext, payload)
            return true
        }

        override fun clearNotification(packageName: String, notifyId: Int) {
            // Current product layer clears notifications via runtime bridge + controller.
        }
    }

    override fun onNotificationEvent(packageName: String?, event: String, source: String) {
        PushRuntime.observeNotificationEvent(packageName, event, source)
    }

    override fun rebuildRestoredNotification(context: Context, notification: Notification): Notification? {
        return NotificationCompatBridge.buildSilencedRestoredNotification(context, notification)
    }

    override fun resolveKick(kickType: String?, kickReason: String?): PushKickPlan {
        return PushPacketSyncRuntime.resolveKick(kickType, kickReason)
    }

    override fun resolveBindResult(success: Boolean, errorType: String?, errorReason: String?): PushBindResultPlan {
        return PushPacketSyncRuntime.resolveBindResult(success, errorType, errorReason)
    }

    override fun resolveRedirect(hostsText: String?): PushRedirectPlan {
        return PushPacketSyncRuntime.resolveRedirect(hostsText)
    }

    override fun resolveCloseChannelPlan(
        request: com.xiaomi.push.service.PushServiceCloseRequest,
        packageChannelIds: List<String>
    ): PushServiceClosePlan {
        return PushServiceIntentRuntime.resolveCloseChannelPlan(request, packageChannelIds)
    }

    override fun resolveResetConnectionPlan(
        channelId: String?,
        requestedSecurity: String?,
        client: PushClientsManager.ClientLoginInfo?,
        connectionReadable: Boolean
    ): PushServiceResetConnectionPlan {
        return PushServiceIntentRuntime.decideResetConnection(channelId, requestedSecurity, client, connectionReadable)
    }

    override fun resolveRegisterAppPlan(
        packageName: String?,
        payload: ByteArray?,
        envChanged: Boolean,
        envType: Int,
        servicePackageName: String
    ): PushServiceRegisterAppPlan {
        if (packageName != null && io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb.isBlocked(packageName)) {
            return PushServiceRegisterAppPlan(action = PushServiceRegisterAppAction.Ignore)
        }
        // Throttle registration requests when channel is not bound to prevent registration storms
        if (packageName != null) {
            val allClients = PushClientsManager.getInstance().getAllClients()
            val channelBound = allClients.any { it.status == PushClientsManager.ClientStatus.binded }
            if (RegistrationThrottle.shouldThrottle(packageName, channelBound)) {
                return PushServiceRegisterAppPlan(action = PushServiceRegisterAppAction.Ignore)
            }
        }
        return PushServiceIntentRuntime.resolveRegisterAppPlan(packageName, payload, envChanged, envType, servicePackageName)
    }

    override fun resolveMiPushAppPlan(
        action: String?,
        packageName: String?,
        payload: ByteArray?,
        cacheMessage: Boolean
    ): PushServiceMiPushAppPlan {
        return PushServiceIntentRuntime.resolveMiPushAppPlan(action, packageName, payload, cacheMessage)
    }

    override fun resolveMiPushPayloadDispatch(
        hasActiveChannel: Boolean,
        clientStatus: PushClientsManager.ClientStatus?,
        cacheIfUnavailable: Boolean
    ): PushServiceMiPushPayloadDispatchPlan {
        return PushServiceIntentRuntime.decideMiPushPayloadDispatch(hasActiveChannel, clientStatus, cacheIfUnavailable)
    }

    override fun resolveChannelOpenPlan(
        hasNetwork: Boolean,
        isConnected: Boolean,
        clientStatus: PushClientsManager.ClientStatus?,
        shouldRebind: Boolean,
        request: com.xiaomi.push.service.PushChannelOpenRequest
    ): PushChannelOpenPlan {
        return PushChannelOpenRuntime.decideOpenPlan(hasNetwork, isConnected, clientStatus, shouldRebind)
    }

    override fun resolveConnectionAttemptPlan(isConnected: Boolean, isConnecting: Boolean): PushConnectionAttemptPlan {
        return PushServiceConnectionRuntime.planConnect(isConnecting, isConnected)
    }

    override fun resolveCheckAlivePlan(isConnected: Boolean, hasNetwork: Boolean): PushCheckAlivePlan {
        return PushServiceConnectionRuntime.planCheckAlive(isConnected, hasNetwork)
    }

    override fun resolveReconnectAttemptPlan(
        state: com.xiaomi.push.service.PushReconnectState,
        force: Boolean,
        isConnected: Boolean,
        hasReconnectionJob: Boolean
    ): PushReconnectAttemptPlan {
        val hasNetwork = Network.hasNetwork(appContext)
        val hasActiveClients = PushClientsManager.getInstance().getActiveClientCount() > 0
        val allowedByPolicy = hasNetwork && (hasActiveClients || force)

        return PushReconnectRuntime.planReconnect(
            state = state,
            forceImmediate = force,
            currentlyConnected = isConnected,
            allowedByPolicy = allowedByPolicy,
            hasPendingConnectJob = hasReconnectionJob,
            nowMs = System.currentTimeMillis()
        )
    }

    override fun resolveCandidateHosts(targetHost: String, fallbackHosts: List<String>): PushSocketHostSelectionPlan {
        return PushSocketConnectionRuntime.resolveCandidateHosts(targetHost, fallbackHosts)
    }

    override fun planFailureRetry(oldConnPoint: String?, newConnPoint: String?): PushSocketFailurePlan {
        return PushSocketConnectionRuntime.planFailureRetry(oldConnPoint, newConnPoint)
    }

    override fun evaluateShortConnection(
        nowElapsed: Long,
        lastConnectedTime: Long,
        hasNetwork: Boolean,
        curShortConnCount: Int,
        networkInterval: Long,
        maxShortConnCount: Int
    ): PushShortConnectionPlan {
        return PushSocketConnectionRuntime.evaluateShortConnection(
            nowElapsedMs = nowElapsed,
            lastConnectedTime = lastConnectedTime,
            hasNetwork = hasNetwork,
            curShortConnCount = curShortConnCount,
            shortConnectionThresholdMs = networkInterval,
            maxShortConnCount = maxShortConnCount
        )
    }

    override fun planSlimHandshake(hasChallenge: Boolean, hasConfigMessage: Boolean): PushSlimHandshakePlan {
        return PushSlimStreamRuntime.planHandshake(hasChallenge, hasConfigMessage)
    }

    override fun resolveSlimInboundPlan(channelId: Int, cmd: String?): PushSlimInboundPlan {
        return PushSlimConnectionRuntime.planInboundBlob(channelId, cmd)
    }

    override fun resolveSlimSendPingPlan(): PushSlimPingPlan {
        return PushSlimConnectionRuntime.planSendPing()
    }

    override fun planSlimPayload(packageName: String?, chid: String?, chidStatus: String?, binderStatus: String?): PushSlimPayloadPlan {
        return PushSlimPayloadPlan(
            action = if (binderStatus.isNullOrBlank()) PushSlimPayloadAction.DeliverBlob else PushSlimPayloadAction.None,
            eventAction = "slim_payload_resolved"
        )
    }

    override fun planSlimWrite(serializedSize: Int, cmd: String?, currentCapacity: Int): PushSlimWritePlan {
        return PushSlimStreamRuntime.planWrite(serializedSize, cmd, currentCapacity)
    }

    override fun planConnectionEvent(event: PushConnectionListenerEvent): PushConnectionStatusPlan {
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
            connectionState = state
        )
    }

    override fun buildGslbRequest(baseUrl: String, sdkVersion: Int, droidVersion: Int, model: String, incremental: String, miuiType: Int): PushGslbRequest {
        return PushHostRuntime.buildGslbRequest(baseUrl, sdkVersion, droidVersion, model, incremental, miuiType)
    }

    override fun decideBucketFetch(fetchBucketRequested: Boolean, lastFetchTimeMs: Long, nowMs: Long, minBucketFetchDurationMs: Long): PushBucketFetchPlan {
        return PushHostRuntime.decideBucketFetch(fetchBucketRequested, lastFetchTimeMs, nowMs, minBucketFetchDurationMs)
    }

    override fun decideBucketReconnect(hasConnection: Boolean, currentHost: String?, candidateHosts: List<String>): PushBucketReconnectPlan {
        return PushHostRuntime.decideBucketReconnect(hasConnection, currentHost, candidateHosts)
    }

    override fun resolveChannelInfoUpdateTarget(
        packageChannelIds: List<String>,
        requestedChannelId: String?,
        requestedUserId: String?,
        pushClientsManager: PushClientsManager
    ): PushChannelInfoUpdateTarget {
        return PushChannelInfoRuntime.resolveUpdateTarget(packageChannelIds, requestedChannelId, requestedUserId, pushClientsManager)
    }

    override fun applyChannelInfoUpdate(
        target: PushChannelInfoUpdateTarget,
        hasClientAttr: Boolean,
        clientAttr: String?,
        hasCloudAttr: Boolean,
        cloudAttr: String?
    ): PushChannelInfoUpdateResult {
        return PushChannelInfoRuntime.applyUpdate(target, hasClientAttr, clientAttr, hasCloudAttr, cloudAttr)
    }

    override fun planRequestUrls(baseUrl: String, localUrls: List<String>?, reservedHosts: List<String>): HostRequestUrlsPlan {
        val plan = HostManagerRuntime.planRequestUrls(baseUrl, localUrls, reservedHosts)
        return HostRequestUrlsPlan(plan.urls)
    }

    override fun planRefreshTargets(allHosts: List<String>, hostsWithFallback: Set<String>): HostRefreshTargetsPlan {
        val plan = HostManagerRuntime.planRefreshTargets(allHosts, hostsWithFallback)
        return HostRefreshTargetsPlan(plan.targetHosts)
    }

    override fun planRemoteFallbackRequest(nowMs: Long, lastRequestTimeMs: Long, failureCount: Long): HostRequestThrottlePlan {
        val plan = HostManagerRuntime.planRemoteFallbackRequest(nowMs, lastRequestTimeMs, failureCount)
        return HostRequestThrottlePlan(plan.shouldRequest, plan.nextTimestampMs)
    }

    override fun notifyConnectionFailed(activeClients: Any) {
        @Suppress("UNCHECKED_CAST")
        PushClientsStateSupport.notifyConnectionFailed(activeClients as Iterable<HashMap<String?, PushClientsManager.ClientLoginInfo>>)
    }

    override fun resetAllClients(clients: Any, reason: Int) {
        @Suppress("UNCHECKED_CAST")
        PushClientsStateSupport.resetAllClients(clients as Iterable<HashMap<String?, PushClientsManager.ClientLoginInfo>>, reason)
    }
}
