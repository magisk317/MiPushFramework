package io.github.magisk317.mipush.service.runtime
import com.xiaomi.push.service.*
import com.xiaomi.smack.packet.*
import com.xiaomi.smack.*
import com.xiaomi.slim.*
import com.xiaomi.push.service.timers.*
import com.xiaomi.push.service.*

import android.text.TextUtils
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import io.github.magisk317.mipush.service.XMPushServiceListener
import com.xiaomi.channel.commonutils.android.Region
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.log.LogUploader
import com.xiaomi.smack.util.TrafficUtils
import com.xiaomi.stats.StatsHandler
import io.github.magisk317.mipush.runtime.core.PushConnectionState
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime

// removed PushAccountRuntime

class XMPushServiceLifecycleRuntime(
    private val service: XMPushService,
) {
    fun configureClientChangeListener() {
        val clientsManager = PushClientsManager.getInstance()
        clientsManager.removeAllClientChangeListeners()
        clientsManager.addClientChangeListener(
            object : PushClientsManager.ClientChangeListener {
                override fun onChange() {
                    service.updateAlarmTimer()
                    if (PushClientsManager.getInstance().getActiveClientCount() <= 0) {
                        service.executeJob(DisconnectJob(service, 12, null))
                    }
                }
            },
        )
    }

    fun networkChanged() {
        val activeNetworkName = Network.getActiveNetworkName(service)
        if (!activeNetworkName.isNullOrEmpty() && activeNetworkName != "null") {
            MyLog.w("network changed,[type: $activeNetworkName]")
        } else {
            MyLog.w("network changed, no active network")
        }
        StatsHandler.getContext()?.statsChannelIfNeed()
        TrafficUtils.notifyNetworkChanage(service)
        service.slimConnection.clearCachedStatus()
        if (Network.hasNetwork(service)) {
            if (service.isConnected && service.shouldCheckAlive()) {
                service.checkAlive(false)
            }
            if (!service.isConnected && !service.isConnecting) {
                service.jobController.removeJobs(1)
                service.executeJob(ConnectJob(service))
            }
            LogUploader.getInstance(service).checkUpload()
        } else {
            service.executeJob(DisconnectJob(service, 2, null))
        }
        service.updateAlarmTimer()
    }

    fun postOnCreate() {
        val regionStorage = AppRegionStorage.getInstance(service.applicationContext)
        var region = regionStorage.getRegion()
        MyLog.w("region of cache is $region")
        if (TextUtils.isEmpty(region)) {
            region = service.ensureRegionAvaible()
        }
        if (TextUtils.isEmpty(region)) {
            service.regionName = Region.China.name
        } else {
            service.regionName = region
            regionStorage.setRegion(region)
            ConnectionConfiguration.setXmppServerHost(XMPushServiceEnvironment.resolveXmppRegionHost(service.regionName))
        }
        if (Region.China.name == service.regionName) {
            ConnectionConfiguration.setXmppServerHost(XMPushServiceEnvironment.resolveXmppRegionHost(service.regionName))
        }
        if (service.isPushEnabled()) {
            val prepareAccountJob = object : XMPushService.Job(XMPushServiceJob.TYPE_PREPARE_MIPUSH_ACCOUNT) {
                override fun getDesc(): String = "prepare the mi push account."

                override fun process() {
                    MIPushHelper.prepareMIPushAccount(service, service)
                    if (Network.hasNetwork(service)) {
                        service.scheduleConnect(true)
                    }
                }
            }
            service.executeJob(prepareAccountJob)
            service.executeJob(prepareAccountJob)
        }
        try {
            if (SystemUtils.isBootCompleted()) {
                service.clientEventDispatcher.notifyServiceStarted(service, service.runtimeObserver)
            }
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }

    fun connectionClosed(connection: Connection, reason: Int, error: Exception?) {
        StatsHandler.getContext()?.connectionClosed(connection, reason, error)
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.disconnected)
        val plan = PushServiceConnectionRuntime.planConnectionClosed(service.shouldFalldown())
        AndroidPushRuntime.observeChannelEvent(null, plan.eventAction, "XMPushServiceLifecycleRuntime.connectionClosed")
        if (plan.shouldScheduleReconnect) {
            service.scheduleConnect(false)
        }
    }

    fun connectionStarted(connection: Connection) {
        MyLog.v("begin to connect...")
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.connecting)
        AndroidPushRuntime.observeConnectionState(PushConnectionState.Connecting, "XMPushServiceLifecycleRuntime.connectionStarted", connection.host, "listener_started")
        StatsHandler.getContext()?.connectionStarted(connection)
    }

    fun reconnectionFailed(connection: Connection, error: Exception) {
        StatsHandler.getContext()?.reconnectionFailed(connection, error)
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.disconnected)
        val plan = PushServiceConnectionRuntime.planReconnectionFailure(service.shouldFalldown())
        AndroidPushRuntime.observeChannelEvent(null, plan.eventAction, "XMPushServiceLifecycleRuntime.reconnectionFailed")
        if (plan.shouldBroadcastUnavailable) {
            service.broadcastNetworkAvailable(false)
        }
        if (plan.shouldScheduleReconnect) {
            service.scheduleConnect(false)
        }
    }

    fun reconnectionSuccessful(connection: Connection) {
        StatsHandler.getContext()?.reconnectionSuccessful(connection)
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.connected)
        val plan = PushServiceConnectionRuntime.planReconnectionSuccess(Alarm.isAlive(), service.shouldFalldown())
        AndroidPushRuntime.observeChannelEvent(null, plan.eventAction, "XMPushServiceLifecycleRuntime.reconnectionSuccessful")
        AndroidPushRuntime.observeConnectionState(PushConnectionState.Connected, "XMPushServiceLifecycleRuntime.reconnectionSuccessful", connection.host, "listener_connected")
        if (plan.shouldBroadcastAvailable) {
            service.broadcastNetworkAvailable(true)
        }
        if (plan.shouldResetReconnectState) {
            service.reconnectionManager.onConnectSucceeded()
        }
        if (plan.shouldRegisterAlarm) {
            MyLog.w("reconnection successful, reactivate alarm.")
            Alarm.registerPing(true)
        }
        if (plan.shouldBindAllClients) {
            PushClientsManager.getInstance().getAllClients().forEach { client ->
                service.executeJob(BindJob(service, client))
            }
        }
    }
}
