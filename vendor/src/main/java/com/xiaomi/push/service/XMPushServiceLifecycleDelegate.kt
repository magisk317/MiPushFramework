package com.xiaomi.push.service

import android.content.Intent
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.BuildSettings
import com.xiaomi.push.service.timers.Alarm
import com.xiaomi.slim.SlimConnection
import com.xiaomi.stats.StatsHandler
import com.xiaomi.tinyData.TinyDataCacheProcessor
import com.xiaomi.tinyData.TinyDataManager

class XMPushServiceLifecycleDelegate(
    private val service: XMPushServiceCore,
) {
    private val infrastructure = XMPushServiceLifecycleInfrastructure(service)
    private val stockLifecycle = XMPushServiceStockLifecycle(service)

    fun onCreate() {
        MyLog.init(service.applicationContext)
        SystemUtils.initialize(service)
        service.runtimeObserver.onServiceCreated(service)
        val account = service.runtimeObserver.applyStoredAccountEnvironment(service)
        if (account != null) {
            BuildSettings.setEnvType(service.runtimeObserver.envType(service))
        }
        infrastructure.installMessenger()
        PushHostManagerFactory.init(service)
        val configuration = infrastructure.createConnectionConfiguration().apply {
            setDebuggerEnabled(true)
        }
        service.connectionConfiguration = configuration
        service.slimConnection = SlimConnection(service, service, configuration).also { connection ->
            connection.addConnectionListener(service)
        }
        service.clientEventDispatcher = service.createClientEventDispatcher()
        Alarm.initialize(service)
        service.packetSync = PacketSync(service)
        service.reconnectionManager = ReconnectionManager(service)
        CommonPacketExtensionProvider().register()
        StatsHandler.getInstance().init(service)
        service.jobController = JobScheduler("Connection Controller Thread")
        val clientsManager = PushClientsManager.getInstance()
        stockLifecycle.configureClientChangeListener(clientsManager)
        // Product observers may append bookkeeping listeners after the stock listener is installed,
        // but must not replace or reimplement the stock transport listener.
        service.runtimeObserver.configureClientChangeListener(service, clientsManager)
        if (service.canOpenForegroundService()) {
            service.enableForegroundService()
        }
        TinyDataManager.getInstance(service)?.addUploader(LongConnUploader(service), TinyDataManager.UPLOADER_PUSH_CHANNEL)
        service.addPingCallBack(TinyDataCacheProcessor(service))
        service.executeJob(InitJob(service))
        service.addNetworkListener(Sync.getInstance(service))
        if (service.isPushEnabled()) {
            service.ensureConnectionChangeReceiver()
        }
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME == service.packageName) {
            infrastructure.installPowerModeObservers()
            infrastructure.installFalldownReceiver()
        }
        service.ensureStockHeartbeatReceivers()
        service.runtimeObserver.persistCreationLog(service)
    }

    fun onDestroy() {
        service.connectionChangeReceiver?.let {
            service.unregisterReceiverSafely(it)
            service.clearConnectionChangeReceiver()
        }
        service.screenStateReceiver?.let {
            service.unregisterReceiverSafely(it)
            service.clearScreenStateReceiver()
        }
        service.wifiDigestReceiver?.let {
            service.unregisterReceiverSafely(it)
        }
        service.intelligentHbReceiver?.let {
            service.unregisterReceiverSafely(it)
        }
        service.clearStockHeartbeatReceivers()
        infrastructure.unregisterPowerModeObservers()
        service.clearNetworkListeners()
        service.jobController.removeAllJobs()
        service.executeJob(
            object : XMPushServiceCore.Job(XMPushServiceJob.TYPE_DISCONNECT) {
                override fun getDesc(): String = "disconnect for service destroy."

                override fun process() {
                    service.currentConnection?.disconnect(15, null)
                    service.clearCurrentConnection()
                }
            },
        )
        service.executeJob(KillJob(service))
        PushClientsManager.getInstance().removeAllClientChangeListeners()
        PushClientsManager.getInstance().resetAllClients(service, 15)
        PushClientsManager.getInstance().removeActiveClients()
        service.slimConnection.removeConnectionListener(service)
        ServiceConfig.getInstance().clear()
        Alarm.stop()
        service.clearPingCallbacks()
        service.runtimeObserver.onServiceDestroy()
    }

    fun onStart(intent: Intent?, startId: Int) {
        val startedAt = System.currentTimeMillis()
        if (intent == null) {
            MyLog.e("onStart() with intent NULL")
        } else {
            MyLog.w(
                String.format(
                    "onStart() with intent.Action = %s, chid = %s, pkg = %s|%s",
                    intent.action,
                    intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID),
                    intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME),
                    intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE),
                ),
            )
        }
        intent?.action?.let { action ->
            if (PushServiceConstants.ACTION_TIMER.equals(action, ignoreCase = true) ||
                PushServiceConstants.ACTION_CHECK_ALIVE.equals(action, ignoreCase = true)
            ) {
                if (service.jobController.isBlocked()) {
                    MyLog.e("ERROR, the job controller is blocked.")
                    PushClientsManager.getInstance().resetAllClients(service, 14)
                    service.stopSelf()
                } else {
                    service.executeJob(IntentJob(service, intent))
                }
            } else if (!PushServiceConstants.ACTION_NETWORK_STATUS_CHANGED.equals(action, ignoreCase = true)) {
                service.executeJob(IntentJob(service, intent))
            }
        }
        val cost = System.currentTimeMillis() - startedAt
        if (cost > 50) {
            MyLog.v("[Prefs] spend $cost ms, too more times.")
        }
    }

    fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onStart(intent, startId)
        return 1
    }

    fun networkChanged() {
        stockLifecycle.networkChanged()
    }

    fun connectionClosed(connection: com.xiaomi.smack.Connection, reason: Int, error: Exception?) {
        service.runtimeObserver.connectionClosed(connection, reason, error)
        // MiPush SDK 3.7.9 XMPushService.connectionClosed and stock 7.4.67-C
        // XMPushService.a both reconnect unless the service is in its fall-down window.
        if (!service.shouldFalldown()) {
            service.scheduleConnect(false)
        }
    }

    fun connectionStarted(connection: com.xiaomi.smack.Connection) {
        service.runtimeObserver.connectionStarted(connection)
    }

    fun postOnCreate() {
        stockLifecycle.postOnCreate()
    }

    fun reconnectionFailed(connection: com.xiaomi.smack.Connection, error: Exception) {
        service.runtimeObserver.reconnectionFailed(connection, error)
        // Stock broadcasts the unavailable state before deciding whether fall-down suppresses the
        // next reconnect. This is service behavior, not an observer/product policy.
        service.broadcastNetworkAvailable(false)
        if (!service.shouldFalldown()) {
            service.scheduleConnect(false)
        }
    }

    fun reconnectionSuccessful(connection: com.xiaomi.smack.Connection) {
        service.runtimeObserver.reconnectionSuccessful(connection)
        // MiPush SDK 3.7.9 and stock XMSF 7.4.67-C perform these actions only after qa.b accepts
        // the server challenge: publish network availability, reset backoff, reactivate the alarm,
        // and enqueue one bind for every retained client.
        service.broadcastNetworkAvailable(true)
        service.reconnectionManager.onConnectSucceeded()
        if (!Alarm.isAlive() && !service.shouldFalldown()) {
            MyLog.w("reconnection successful, reactivate alarm.")
            Alarm.registerPing(true)
        }
        PushClientsManager.getInstance().getAllClients().forEach { client ->
            service.executeJob(BindJob(service, client))
        }
    }
}
