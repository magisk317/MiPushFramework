package com.xiaomi.push.service

import android.content.Intent
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.BuildSettings
import com.xiaomi.push.service.timers.Alarm
import com.xiaomi.slim.SlimConnection
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.stats.StatsHandler
import com.xiaomi.tinyData.TinyDataCacheProcessor
import com.xiaomi.tinyData.TinyDataManager

class XMPushServiceLifecycleDelegate(
    private val service: XMPushService,
) {
    private val infrastructure = XMPushServiceLifecycleInfrastructure(service)
    private val runtime = XMPushServiceLifecycleRuntime(service)

    fun onCreate() {
        MyLog.init(service.applicationContext)
        SystemUtils.initialize(service)
        val account = PushAccountRuntime.applyStoredAccountEnvironment(service, "XMPushService.onCreate")
        if (account != null) {
            BuildSettings.setEnvType(account.envType)
        }
        infrastructure.installMessenger()
        PushHostManagerFactory.init(service)
        val configuration = infrastructure.createConnectionConfiguration().apply {
            setDebuggerEnabled(true)
        }
        service.connectionConfiguration = configuration
        service.slimConnection = SlimConnection(service, configuration).also { connection ->
            connection.addConnectionListener(service)
        }
        service.clientEventDispatcher = service.createClientEventDispatcher()
        Alarm.initialize(service)
        service.packetSync = PacketSync(service)
        service.reconnectionManager = ReconnectionManager(service)
        CommonPacketExtensionProvider().register()
        StatsHandler.getInstance().init(service)
        service.jobController = JobScheduler("Connection Controller Thread")
        runtime.configureClientChangeListener()
        if (service.canOpenForegroundService()) {
            service.enableForegroundService()
        }
        TinyDataManager.getInstance(service).addUploader(LongConnUploader(service), TinyDataManager.UPLOADER_PUSH_CHANNEL)
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
        infrastructure.persistCreationLog(account)
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
        infrastructure.unregisterPowerModeObservers()
        service.clearNetworkListeners()
        service.jobController.removeAllJobs()
        service.executeJob(
            object : XMPushService.Job(XMPushServiceJob.TYPE_DISCONNECT) {
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
        runtime.networkChanged()
    }

    fun connectionClosed(connection: com.xiaomi.smack.Connection, reason: Int, error: Exception?) {
        runtime.connectionClosed(connection, reason, error)
    }

    fun connectionStarted(connection: com.xiaomi.smack.Connection) {
        runtime.connectionStarted(connection)
    }

    fun postOnCreate() {
        runtime.postOnCreate()
    }

    fun reconnectionFailed(connection: com.xiaomi.smack.Connection, error: Exception) {
        runtime.reconnectionFailed(connection, error)
    }

    fun reconnectionSuccessful(connection: com.xiaomi.smack.Connection) {
        runtime.reconnectionSuccessful(connection)
    }
}
