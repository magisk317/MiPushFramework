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
import com.xiaomi.push.service.MIPushAppAbsentManager
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PushBindResultPlan
import com.xiaomi.push.service.PushBucketFetchPlan
import com.xiaomi.push.service.PushBucketReconnectPlan
import com.xiaomi.push.service.PushClientChangePlan
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
import com.xiaomi.push.service.PushRegistrationPayloadRepairResult
import com.xiaomi.push.service.PushShortConnectionPlan
import com.xiaomi.push.service.PushShouldReconnectPlan
import com.xiaomi.push.service.PushSlimHandshakePlan
import com.xiaomi.push.service.PushSlimInboundPlan
import com.xiaomi.push.service.PushSlimPingPlan
import com.xiaomi.push.service.PushSlimPayloadAction
import com.xiaomi.push.service.PushSlimPayloadPlan
import com.xiaomi.push.service.PushSlimWritePlan
import com.xiaomi.push.service.PushSocketFailurePlan
import com.xiaomi.push.service.PushSocketHostSelectionPlan
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.ReconnectDebugLog
import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.push.service.XMPushServiceProxy
import com.xiaomi.slim.Blob
import com.xiaomi.smack.Connection
import com.xiaomi.smack.packet.Packet
import io.github.magisk317.mipush.common.compat.NotificationCompatBridge
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.push.hook.HookTraceCompat
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.push.pipeline.PackageDataClearedCoordinator
import com.xiaomi.push.sdk.PushMessageProcessor
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntimeObservationAdapter
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntimeRegistrationChannelObservationAdapter
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntimeNotificationObservationAdapter
import io.github.magisk317.mipush.runtime.core.PushRuntimeObservationSink
import io.github.magisk317.mipush.runtime.core.PushRuntimeRegistrationChannelObservationSink
import io.github.magisk317.mipush.runtime.core.PushRuntimeNotificationObservationSink
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.service.ForegroundHelper
import io.github.magisk317.mipush.service.runtime.MIPushAccountUtilsRuntime
import io.github.magisk317.mipush.service.runtime.XMPushServiceLifecycleRuntime
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
import io.github.magisk317.mipush.runtime.core.RegistrationThrottle
import io.github.magisk317.mipush.service.runtime.RegistrationPayloadRepair
import io.github.magisk317.mipush.service.runtime.NetworkCheckupRuntime
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.service.runtime.PushPacketSyncRuntime
import java.io.IOException
import io.github.magisk317.xposed.logging.MagiskOtel

class MiPushRuntimeObserverBridge(private val context: Context) : IPushRuntimeObserver {
    private val appContext: Context = context.applicationContext ?: context
    private val runtimeObservationSink: PushRuntimeObservationSink = AndroidPushRuntimeObservationAdapter
    private val runtimeRegistrationChannelObservationSink: PushRuntimeRegistrationChannelObservationSink =
        AndroidPushRuntimeRegistrationChannelObservationAdapter
    private val runtimeNotificationObservationSink: PushRuntimeNotificationObservationSink =
        AndroidPushRuntimeNotificationObservationAdapter
    private val observerState = MiPushRuntimeObserverState()
    private val connectionLifecycleAdapter = MiPushRuntimeConnectionLifecycleAdapter(
        context = context,
        appContext = appContext,
        state = observerState,
        runtimeObservationSink = runtimeObservationSink,
        channelObservationSink = runtimeRegistrationChannelObservationSink,
        publishConnectionStatus = { status ->
            io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.onConnectionStatusChanged(status)
        },
    )
    private val registrationExecutionAdapter = MiPushRuntimeRegistrationExecutionAdapter(
        appContext = appContext,
        observationSink = runtimeRegistrationChannelObservationSink,
        isTrackedPackage = ::isTrackedPackage,
    )
    init {
        XMPushServiceCore.observer = this
    }

    companion object {
        fun ensureInstalled(context: Context): Boolean {
            return synchronized(XMPushServiceCore::class.java) {
                if (XMPushServiceCore.observer != null) return@synchronized false
                MiPushRuntimeObserverBridge(context)
                true
            }
        }

        fun getOrInstall(context: Context): IPushRuntimeObserver {
            return synchronized(XMPushServiceCore::class.java) {
                XMPushServiceCore.observer ?: MiPushRuntimeObserverBridge(context)
            }
        }
    }

    // The registration/channel observers below only want to track packages the framework serves,
    // which is why they filter out system packages. But xmsf itself is an updated system app
    // (flags carry SYSTEM | UPDATED_SYSTEM_APP), so a plain isUserApplication check also rejected
    // the push host's own registration. That silently dropped xmsf's cached registration payload,
    // leaving the request stranded with no way to reach the server after chid 5 bound.
    private fun isTrackedPackage(packageName: String): Boolean {
        return packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME ||
            packageName == appContext.packageName ||
            Utils.isUserApplication(appContext, packageName)
    }

    private fun frameworkProcessor(): PushMessageProcessor {
        return AppDependencies.get<PushMessageProcessor>(appContext)
    }

    override fun onServiceCreated(service: android.app.Service) =
        connectionLifecycleAdapter.onServiceCreated(service)

    override fun onServiceDestroy() = connectionLifecycleAdapter.onServiceDestroy()

    override fun configureClientChangeListener(context: Context, manager: PushClientsManager) =
        connectionLifecycleAdapter.configureClientChangeListener(context, manager)

    override fun onConnectionStateChanged(stateName: String, reason: String, host: String?, message: String) =
        connectionLifecycleAdapter.onConnectionStateChanged(stateName, reason, host, message)

    override fun onPingSent(atMs: Long) = connectionLifecycleAdapter.onPingSent(atMs)

    override fun onReadAlive(atMs: Long) = connectionLifecycleAdapter.onReadAlive(atMs)

    override fun onPingTimeout(atMs: Long) = connectionLifecycleAdapter.onPingTimeout(atMs)

    override fun onConnectionStatusChanged(status: ConnectionStatus) =
        connectionLifecycleAdapter.onConnectionStatusChanged(status)

    override fun reconnectionFailed(connection: Connection, error: Exception) =
        connectionLifecycleAdapter.reconnectionFailed(connection, error)

    override fun reconnectionSuccessful(connection: Connection) =
        connectionLifecycleAdapter.reconnectionSuccessful(connection)

    override fun connectionClosed(connection: Connection, reason: Int, error: Exception?) =
        connectionLifecycleAdapter.connectionClosed(connection, reason, error)

    override fun connectionStarted(connection: Connection) =
        connectionLifecycleAdapter.connectionStarted(connection)

    override fun notifyConnectionError(reason: Int, exc: Exception?) =
        connectionLifecycleAdapter.notifyConnectionError(reason, exc)

    override fun requestConnection(source: String, reason: String) =
        connectionLifecycleAdapter.requestConnection(source, reason)

    override fun startForegroundService() = connectionLifecycleAdapter.startForegroundService()

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

    override fun shouldRunConnectivityTest(activeCount: Int, lastCheckTimeMs: Long, testHostsCount: Int): Boolean =
        connectionLifecycleAdapter.shouldRunConnectivityTest(activeCount, lastCheckTimeMs, testHostsCount)

    override fun createHostManager(context: Context, hostFilter: Any?, httpGet: Any?, userId: String): Any? =
        connectionLifecycleAdapter.createHostManager(context, hostFilter, httpGet, userId)

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
        } catch (_: IOException) {
            PushRuntime.observeAccountEvent("account_register_failed_io", source)
            null
        } catch (_: Exception) {
            PushRuntime.observeAccountEvent("account_register_failed_runtime", source)
            null
        }
    }

    override fun resolveAccountUrl(region: String?, oneBoxBuild: Boolean, oneBoxHost: String, sandBoxBuild: Boolean): String {
        return MIPushAccountUtilsRuntime.resolveAccountUrl(region, oneBoxBuild, oneBoxHost, sandBoxBuild)
    }

    override fun onRegistrationStateChanged(
        packageName: String,
        state: PushRegistrationState,
        reason: String,
        message: String,
    ) = registrationExecutionAdapter.onRegistrationStateChanged(packageName, state, reason, message)

    override fun onApplicationIntentReceived(intent: Intent) {
        MiPushRuntimeBridge.onApplicationIntentReceived(appContext, intent)
    }

    override fun onPackageDataCleared(packageName: String) {
        PackageDataClearedCoordinator.handle(appContext, packageName)
    }

    override fun onRegistrationResult(packageName: String, success: Boolean, source: String, reason: String) =
        registrationExecutionAdapter.onRegistrationResult(packageName, success, source, reason)

    override fun repairRegistrationPayload(context: Context, packageName: String): PushRegistrationPayloadRepairResult? =
        registrationExecutionAdapter.repairRegistrationPayload(context, packageName)

    override fun rememberPendingRegistration(packageName: String, appId: String?) =
        registrationExecutionAdapter.rememberPendingRegistration(packageName, appId)

    override fun cacheRegistrationRequest(packageName: String, payload: ByteArray) =
        registrationExecutionAdapter.cacheRegistrationRequest(packageName, payload)

    override fun clearAccount(context: Context, packageName: String) {
        MIPushAccountUtils.clearAccount(context)
        PushRuntime.observeAccountEvent("account_cleared", "MiPushRuntimeObserverBridge.clearAccount:$packageName")
    }

    override fun observeUnregistration(packageName: String, state: PushRegistrationState) =
        registrationExecutionAdapter.observeUnregistration(packageName, state)

    override fun cacheRegistrationTask(
        packageName: String,
        intent: Intent,
        source: String,
        reason: String,
        timestampMs: Long,
    ) = registrationExecutionAdapter.cacheRegistrationTask(packageName, intent, source, reason, timestampMs)

    override fun dispatchRegistrationTasks(source: String, dispatcher: Any?) =
        registrationExecutionAdapter.dispatchRegistrationTasks(source, dispatcher)

    override fun clearRegistrationTasks(packageName: String) =
        registrationExecutionAdapter.clearRegistrationTasks(packageName)

    override fun onAccountEvent(packageName: String, event: String) {
        PushRuntime.observeAccountEvent(event, "MiPushRuntimeObserverBridge.onAccountEvent:$packageName")
    }

    override fun attachAccountClient(client: Any) {
        val account = client as? MIPushAccount ?: return
        val service = observerState.service()
            ?: io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.peekService()
            ?: return
        val created = attachMIPushAccountClient(
            account = account,
            service = service,
            manager = PushClientsManager.getInstance(),
        )
        PushRuntimeChannelTracker.syncNow(
            if (created) {
                "MiPushRuntimeObserverBridge.attachAccountClient:created"
            } else {
                "MiPushRuntimeObserverBridge.attachAccountClient:existing"
            },
        )
    }

    override fun onChannelEvent(packageName: String?, event: String, reason: String) {
        runtimeRegistrationChannelObservationSink.observeChannelEvent(packageName, event, reason)
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
        runtimeRegistrationChannelObservationSink.observeChannelState(
            packageName = packageName,
            channelId = chid,
            userId = userId,
            session = session,
            state = state,
            source = reason,
            reasonCode = reasonCode,
            reasonMessage = reasonMsg,
            nowMs = System.currentTimeMillis(),
        )
    }

    override fun syncChannelTracker(reason: String) {
        PushRuntimeChannelTracker.syncNow(reason)
    }

    override fun notifyRegisterError(
        errorCode: Int,
        errorMessage: String,
        notifier: IPendingPacketErrorNotifier,
    ) = registrationExecutionAdapter.notifyRegisterError(errorCode, errorMessage, notifier)

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

    override fun shouldAcceptProfile(container: Any): Boolean {
        val pushContainer = container as? com.xiaomi.xmpush.thrift.XmPushActionContainer ?: return true
        return StockSurfaceSupport.isProfileAllowed(appContext, pushContainer)
    }

    override fun processMIPushMessage(payload: ByteArray, trafficBytes: Long) {
        // Don't re-enter onPayloadFromServer here — it was already called by onPayloadReceived
        // via ClientEventDispatcher.notifyPacketArrival, which marks the message as seen.
        // Re-entering would cause shouldProcessPayloadIdentity to return false (duplicate),
        // so notifyPushMessage would never be called.
        MyMIPushNotificationHelper.notifyPushMessage(
            context = appContext,
            decryptedContent = payload,
            dispatchMessageArrived = true,
        )
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

    override fun processPendingRegistrationRequests(source: String, sender: IPendingPacketSender) =
        registrationExecutionAdapter.processPendingRegistrationRequests(source, sender)

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
        return com.xiaomi.mipush.sdk.PushMessageProcessor.getInstance(appContext).processIntent(intent)
    }

    override val notificationHandler: IPushNotificationHandler = object : IPushNotificationHandler {
        override fun handleNotification(packageName: String, payload: ByteArray): Boolean {
            val startedAt = System.nanoTime()
            fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
                val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                val attrs = mutableMapOf(
                    "result" to result,
                    "duration_ms" to durationMs.toString(),
                    "process" to "main",
                    "target_package" to packageName,
                    "payload_size" to payload.size.toString(),
                )
                if (reason != null) {
                    attrs["reason"] = reason
                }
                MagiskOtel.event(
                    name = "push.receive",
                    attributes = attrs,
                    statusOk = statusOk,
                )
            }

            return try {
                MyMIPushNotificationHelper.notifyPushMessage(appContext, payload)
                emit(result = "ok")
                true
            } catch (error: RuntimeException) {
                emit(
                    result = "error",
                    statusOk = false,
                    reason = error.javaClass.simpleName,
                )
                throw error
            }
        }

        override fun clearNotification(packageName: String, notifyId: Int) {
            // Current product layer clears notifications via runtime bridge + controller.
        }
    }

    override fun onNotificationEvent(packageName: String?, event: String, source: String) {
        runtimeNotificationObservationSink.observeNotificationEvent(packageName, event, source)
    }

    override fun rebuildRestoredNotification(context: Context, notification: Notification): Notification? {
        return NotificationCompatBridge.buildSilencedRestoredNotification(context, notification)
    }

    override fun resolveKick(kickType: String?, kickReason: String?): PushKickPlan {
        return PushPacketSyncRuntime.resolveKick(kickType, kickReason)
    }

    override fun resolveBindResult(success: Boolean, errorType: String?, errorReason: String?): PushBindResultPlan {
        val plan = PushPacketSyncRuntime.resolveBindResult(success, errorType, errorReason)
        if (plan.shouldReportInvalidSig) {
            logW("SMACK: channel bind failed due to invalid-sig, scheduling account refresh and reconnect")
            val service = observerState.service()
                ?: io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.peekService()
            if (service != null && Network.hasNetwork(appContext)) {
                service.executeJob(
                    object : com.xiaomi.push.service.XMPushServiceCore.Job(
                        com.xiaomi.push.service.XMPushServiceJob.TYPE_PREPARE_MIPUSH_ACCOUNT
                    ) {
                        override fun getDesc(): String = "refresh mi push account after invalid-sig"
                        override fun process() {
                            try {
                                val newAccount = MIPushAccountUtils.register(
                                    service,
                                    service.packageName,
                                    MIPushAccountUtils.MIPUSH_MIUI_APPID,
                                    MIPushAccountUtils.MIPUSH_MIUI_APP_TOKEN,
                                    this@MiPushRuntimeObserverBridge,
                                )
                                if (newAccount != null) {
                                    attachMIPushAccountClient(newAccount, service, PushClientsManager.getInstance())
                                    val client = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(
                                        PushConstants.MIPUSH_CHANNEL,
                                        newAccount.account
                                    )
                                    if (service.isConnected && client != null) {
                                        service.executeJob(com.xiaomi.push.service.BindJob(service, client))
                                    } else {
                                        service.scheduleConnect(true)
                                    }
                                } else {
                                    logW("register returned null after invalid-sig, scheduling reconnect to retry")
                                    PushRuntime.observeAccountEvent("refresh_failed_invalid_sig", "MiPushRuntimeObserverBridge.resolveBindResult")
                                    service.scheduleConnect(true)
                                }
                            } catch (e: Exception) {
                                logW("failed to register new account after invalid-sig", e)
                            }
                        }
                    }
                )
            }
        }
        return plan
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
        servicePackageName: String,
    ): PushServiceRegisterAppPlan = registrationExecutionAdapter.resolveRegisterAppPlan(
        packageName,
        payload,
        envChanged,
        envType,
        servicePackageName,
    )

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

    override fun resolveShouldReconnectPlan(
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
        val plan = PushServiceConnectionRuntime.planShouldReconnect(
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

    override fun resolveReconnectAttemptPlan(
        state: com.xiaomi.push.service.PushReconnectState,
        force: Boolean,
        isConnected: Boolean,
        hasReconnectionJob: Boolean
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
            val service = observerState.service()
                ?: io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.peekService()
            if (service != null) {
                attachMIPushAccountClient(account, service, PushClientsManager.getInstance())
            }
        }

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

    override fun notifyConnectionFailed(activeClients: Any) =
        connectionLifecycleAdapter.notifyConnectionFailed(activeClients)

    override fun resolveClientChangePlan(activeClientCount: Int, shouldUpdateAlarm: Boolean): PushClientChangePlan {
        // Under MiPushFramework with daemon keeper, never drop TCP connection when active client
        // count temporarily drops to 0 (e.g. during account re-registration or app unwatch).
        return PushClientChangePlan(
            shouldUpdateAlarm = shouldUpdateAlarm,
            shouldDisconnect = false,
            eventAction = if (activeClientCount <= 0) "client_change_keep_alive" else "client_change_update_alarm"
        )
    }

    override fun resetAllClients(clients: Any, reason: Int) {
        @Suppress("UNCHECKED_CAST")
        PushClientsStateSupport.resetAllClients(clients as Iterable<HashMap<String?, PushClientsManager.ClientLoginInfo>>, reason)
    }
}

internal fun attachMIPushAccountClient(
    account: MIPushAccount,
    service: XMPushServiceCore,
    manager: PushClientsManager,
): Boolean = synchronized(manager) {
    if (manager.getAllClientLoginInfoByChid(PushConstants.MIPUSH_CHANNEL).isNotEmpty()) {
        return@synchronized false
    }
    account.toClientLoginInfo(service, service).also { loginInfo ->
        MIPushHelper.prepareClientLoginInfo(service, loginInfo)
        manager.addActiveClient(loginInfo)
    }
    true
}
