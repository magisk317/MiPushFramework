package io.github.magisk317.mipush.bridge

import android.app.Service
import android.content.Context
import com.xiaomi.network.HostFilter
import com.xiaomi.network.HostManager
import com.xiaomi.push.service.ConnectionStatus
import com.xiaomi.push.service.ReconnectDebugLog
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushConnectionState
import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.push.service.timers.Alarm
import com.xiaomi.smack.Connection
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.core.PushRuntimeObservationSink
import io.github.magisk317.mipush.runtime.core.PushRuntimeRegistrationChannelObservationSink
import io.github.magisk317.mipush.service.ForegroundHelper
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import io.github.magisk317.mipush.service.runtime.NetworkCheckupRuntime
import io.github.magisk317.mipush.service.runtime.PushClientsStateSupport
import io.github.magisk317.mipush.service.runtime.PushServiceConnectionRuntime

internal class MiPushRuntimeConnectionLifecycleAdapter(
    private val context: Context,
    private val appContext: Context,
    private val state: MiPushRuntimeObserverState,
    private val runtimeObservationSink: PushRuntimeObservationSink,
    private val channelObservationSink: PushRuntimeRegistrationChannelObservationSink,
    private val publishConnectionStatus: (ConnectionStatus) -> Unit,
) {
    fun onServiceCreated(service: Service) {
        if (service !is XMPushServiceCore) return
        io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.ensureCreated(service)
        state.replaceService(service)
    }

    fun onServiceDestroy() {
        state.clearService()
        io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.onDestroy(null)
    }

    fun configureClientChangeListener(context: Context, manager: PushClientsManager) {
        state.currentServiceLifecycleRuntime(context as? XMPushServiceCore)
            ?.configureClientChangeListener(manager)
    }

    fun onConnectionStateChanged(stateName: String, reason: String, host: String?, message: String) {
        runtimeObservationSink.observeConnectionState(
            state = toRuntimeConnectionState(stateName),
            source = reason.ifBlank { "MiPushRuntimeObserverBridge.onConnectionStateChanged" },
            host = host,
            reason = message,
            nowMs = System.currentTimeMillis(),
        )
    }

    fun onPingSent(atMs: Long) = runtimeObservationSink.observePingSent(atMs)

    fun onReadAlive(atMs: Long) = runtimeObservationSink.observeReadAlive(atMs)

    fun onPingTimeout(atMs: Long) = runtimeObservationSink.observePingTimeout(atMs)

    fun onConnectionStatusChanged(status: ConnectionStatus) {
        io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.onConnectionStatusChanged(status)
        runtimeObservationSink.observeConnectionState(
            state = toRuntimeConnectionState(status.name),
            source = "MiPushRuntimeObserverBridge.onConnectionStatusChanged",
            host = null,
            reason = status.name,
            nowMs = System.currentTimeMillis(),
        )
    }

    fun reconnectionFailed(connection: Connection, error: Exception) {
        val service = state.activeServiceFor(connection)
        if (service == null) {
            logW("ignore reconnection failure from stale connection")
            return
        }
        val wasFalldown = service.shouldFalldown()
        val failPlan = PushServiceConnectionRuntime.planReconnectionFailure(wasFalldown)
        ReconnectDebugLog.w(
            "reconnect_failed host=${connection.host} errorType=${error.javaClass.name} " +
                "message=${error.message} falldown=$wasFalldown " +
                "schedule=${failPlan.shouldScheduleReconnect}"
        )
        state.releaseConnection(connection)
        publishConnectionStatus(ConnectionStatus.disconnected)
        channelObservationSink.observeChannelEvent(
            null,
            "reconnect_failed",
            "MiPushRuntimeObserverBridge.reconnectionFailed",
        )
        runtimeObservationSink.observeConnectionState(
            state = PushConnectionState.Disconnected,
            source = "MiPushRuntimeObserverBridge.reconnectionFailed",
            host = connection.host,
            reason = error.message,
            nowMs = System.currentTimeMillis(),
        )
        if (failPlan.shouldBroadcastUnavailable) {
            service.broadcastNetworkAvailable(false)
        }
        if (failPlan.shouldScheduleReconnect) {
            service.scheduleConnect(false)
        }
    }

    fun reconnectionSuccessful(connection: Connection) {
        val service = state.activeServiceFor(connection)
        if (service == null) {
            logW("ignore reconnect success from stale connection")
            return
        }
        runtimeObservationSink.observeReconnectConnected(System.currentTimeMillis())
        val wasFalldown = service.shouldFalldown()
        val successPlan = PushServiceConnectionRuntime.planReconnectionSuccess(
            alarmAlive = Alarm.isAlive(),
            shouldFalldown = wasFalldown,
        )
        ReconnectDebugLog.w(
            "reconnect_established host=${connection.host} falldown=$wasFalldown " +
                "alarmAlive=${Alarm.isAlive()}"
        )
        state.setActiveConnection(connection)
        publishConnectionStatus(ConnectionStatus.connected)
        channelObservationSink.observeChannelEvent(
            null,
            "reconnect_success",
            "MiPushRuntimeObserverBridge.reconnectionSuccessful",
        )
        MyMIPushNotificationHelper.markNotificationSessionStarted(
            "MiPushRuntimeObserverBridge.reconnectionSuccessful",
        )
        if (successPlan.shouldBroadcastAvailable) {
            service.broadcastNetworkAvailable(true)
        }
        if (successPlan.shouldResetReconnectState) {
            service.reconnectionManager.onConnectSucceeded()
        }
        if (successPlan.shouldRegisterAlarm) {
            Alarm.registerPing(true)
        }
        if (successPlan.shouldBindAllClients) {
            PushClientsManager.getInstance().getAllClients().forEach { client ->
                service.executeJob(com.xiaomi.push.service.BindJob(service, client))
            }
        }
        runtimeObservationSink.observeConnectionState(
            state = PushConnectionState.Connected,
            source = "MiPushRuntimeObserverBridge.reconnectionSuccessful",
            host = connection.host,
            reason = "reconnected",
            nowMs = System.currentTimeMillis(),
        )
    }

    fun connectionClosed(connection: Connection, reason: Int, error: Exception?) {
        val service = state.activeServiceFor(connection)
        if (service == null) {
            logW("ignore close from stale connection reason=$reason")
            return
        }
        val wasFalldown = service.shouldFalldown()
        val closePlan = PushServiceConnectionRuntime.planConnectionClosed(wasFalldown, reason, error)
        ReconnectDebugLog.w(
            "connection_closed reason=$reason host=${connection.host} " +
                "errorType=${error?.javaClass?.name} message=${error?.message} " +
                "falldown=$wasFalldown schedule=${closePlan.shouldScheduleReconnect}"
        )
        runtimeObservationSink.observeDisconnectReason(reason)
        state.releaseConnection(connection)
        publishConnectionStatus(ConnectionStatus.disconnected)
        channelObservationSink.observeChannelEvent(
            null,
            "connection_closed",
            "MiPushRuntimeObserverBridge.connectionClosed",
        )
        runtimeObservationSink.observeConnectionState(
            state = PushConnectionState.Disconnected,
            source = "MiPushRuntimeObserverBridge.connectionClosed",
            host = connection.host,
            reason = error?.message ?: reason.toString(),
            nowMs = System.currentTimeMillis(),
        )
        if (closePlan.shouldScheduleReconnect) {
            service.scheduleConnect(!wasFalldown)
        }
    }

    fun connectionStarted(connection: Connection) {
        if (state.activeServiceFor(connection) == null) {
            logW("ignore start from stale connection")
            return
        }
        state.setActiveConnection(connection)
        runtimeObservationSink.observeReconnectStarted(System.currentTimeMillis())
        publishConnectionStatus(ConnectionStatus.connecting)
        ReconnectDebugLog.w(
            "connection_started host=${connection.host}"
        )
        channelObservationSink.observeChannelEvent(
            null,
            "connection_started",
            "MiPushRuntimeObserverBridge.connectionStarted",
        )
        runtimeObservationSink.observeConnectionState(
            state = PushConnectionState.Connecting,
            source = "MiPushRuntimeObserverBridge.connectionStarted",
            host = connection.host,
            reason = "started",
            nowMs = System.currentTimeMillis(),
        )
    }

    fun notifyConnectionError(reason: Int, exc: Exception?) {
        channelObservationSink.observeChannelEvent(
            null,
            "connection_error",
            "MiPushRuntimeObserverBridge.notifyConnectionError:$reason",
        )
    }

    fun requestConnection(source: String, reason: String) {
        PushRuntime.requestConnection(source, reason)
    }

    fun notifyConnectionFailed(activeClients: Any) {
        @Suppress("UNCHECKED_CAST")
        PushClientsStateSupport.notifyConnectionFailed(
            activeClients as Iterable<HashMap<String?, PushClientsManager.ClientLoginInfo>>
        )
    }

    fun resetAllClients(clients: Any, reason: Int) {
        @Suppress("UNCHECKED_CAST")
        PushClientsStateSupport.resetAllClients(
            clients as Iterable<HashMap<String?, PushClientsManager.ClientLoginInfo>>,
            reason,
        )
    }

    fun startForegroundService() {
        (context as? Service)?.let { ForegroundHelper(it).startForeground() }
    }

    fun shouldRunConnectivityTest(activeCount: Int, lastCheckTimeMs: Long, testHostsCount: Int): Boolean {
        return NetworkCheckupRuntime.shouldRunConnectivityTest(
            activeCount = activeCount,
            nowMs = System.currentTimeMillis(),
            lastCheckTimeMs = lastCheckTimeMs,
            allowStats = true,
            testHostsCount = testHostsCount,
        )
    }

    fun createHostManager(context: Context, hostFilter: Any?, httpGet: Any?, userId: String): Any? {
        val filter = hostFilter as? HostFilter
        val getter = httpGet as? HostManager.HttpGet ?: return null
        return HostManager(context, filter, getter, userId)
    }

    private fun toRuntimeConnectionState(stateName: String): PushConnectionState {
        return when (stateName) {
            "Connected", ConnectionStatus.connected.name -> PushConnectionState.Connected
            "Connecting", ConnectionStatus.connecting.name -> PushConnectionState.Connecting
            "Disconnecting" -> PushConnectionState.Disconnecting
            else -> PushConnectionState.Disconnected
        }
    }
}
