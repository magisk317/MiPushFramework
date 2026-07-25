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
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.log.LogUploader
import com.xiaomi.smack.util.TrafficUtils
import com.xiaomi.stats.StatsHandler
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.runtime.core.PushConnectionState
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime
import io.github.magisk317.xposed.logging.MagiskOtel

// removed PushAccountRuntime

class XMPushServiceLifecycleRuntime(
    private val service: XMPushServiceCore,
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
            logW("network changed,[type: $activeNetworkName]")
        } else {
            logW("network changed, no active network")
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
        logW("region of cache is $region")
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
            val prepareAccountJob = object : XMPushServiceCore.Job(XMPushServiceJob.TYPE_PREPARE_MIPUSH_ACCOUNT) {
                override fun getDesc(): String = "prepare the mi push account."

                override fun process() {
                    MIPushHelper.prepareMIPushAccount(service, service)
                    if (Network.hasNetwork(service)) {
                        service.scheduleConnect(true)
                    }
                }
            }
            service.executeJob(prepareAccountJob)
        }
        try {
            if (SystemUtils.isBootCompleted()) {
                service.clientEventDispatcher.notifyServiceStarted(service, service.runtimeObserver)
            }
        } catch (e: Exception) {
            logE("notify service started failed", e)
        }
    }

    fun connectionClosed(connection: Connection, reason: Int, error: Exception?) {
        StatsHandler.getContext()?.connectionClosed(connection, reason, error)
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.disconnected)
        val plan = PushServiceConnectionRuntime.planConnectionClosed(service.shouldFalldown())
        AndroidPushRuntime.observeChannelEvent(null, plan.eventAction, "XMPushServiceLifecycleRuntime.connectionClosed")
        MagiskOtel.event(
            name = "push.network",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "connection_closed",
                "reason" to plan.eventAction,
                "network_available" to "false",
            ),
            statusOk = true,
        )
        if (plan.shouldScheduleReconnect) {
            service.scheduleConnect(false)
        }
    }

    fun connectionStarted(connection: Connection) {
        logV("begin to connect...")
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.connecting)
        AndroidPushRuntime.observeConnectionState(PushConnectionState.Connecting, "XMPushServiceLifecycleRuntime.connectionStarted", connection.host, "listener_started")
        MagiskOtel.event(
            name = "push.network",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "connection_started",
                "reason" to "connecting",
            ),
            statusOk = true,
        )
        StatsHandler.getContext()?.connectionStarted(connection)
    }

    fun reconnectionFailed(connection: Connection, error: Exception) {
        StatsHandler.getContext()?.reconnectionFailed(connection, error)
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(XMPushServiceListener.ConnectionStatus.disconnected)
        val plan = PushServiceConnectionRuntime.planReconnectionFailure(service.shouldFalldown())
        AndroidPushRuntime.observeChannelEvent(null, plan.eventAction, "XMPushServiceLifecycleRuntime.reconnectionFailed")
        MagiskOtel.event(
            name = "push.network",
            attributes = mapOf(
                "result" to "error",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "reconnect_failed",
                "reason" to plan.eventAction,
                "error_class" to error.javaClass.simpleName,
            ),
            statusOk = false,
        )
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
        MagiskOtel.event(
            name = "push.network",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "reconnect_success",
                "reason" to plan.eventAction,
                "network_available" to "true",
            ),
            statusOk = true,
        )
        val resolvedIp = (connection as? com.xiaomi.smack.SocketConnection)?.resolvedIp
        AndroidPushRuntime.observeConnectionState(
            state = PushConnectionState.Connected,
            source = "XMPushServiceLifecycleRuntime.reconnectionSuccessful",
            host = connection.host,
            reason = "listener_connected",
            resolvedIp = resolvedIp,
        )
        if (plan.shouldBroadcastAvailable) {
            service.broadcastNetworkAvailable(true)
        }
        if (plan.shouldResetReconnectState) {
            service.reconnectionManager.onConnectSucceeded()
        }
        if (plan.shouldRegisterAlarm) {
            logW("reconnection successful, reactivate alarm.")
            Alarm.registerPing(true)
        }
        if (plan.shouldBindAllClients) {
            PushClientsManager.getInstance().getAllClients().forEach { client ->
                service.executeJob(BindJob(service, client))
            }
        }
    }
}
