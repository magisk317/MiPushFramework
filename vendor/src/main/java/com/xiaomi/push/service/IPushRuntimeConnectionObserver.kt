package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import com.xiaomi.smack.Connection
import com.xiaomi.smack.packet.Packet

/**
 * Transport-domain slice of [IPushRuntimeObserver]: keep-alive pings, connection
 * lifecycle, service/system environment probes and client-login bookkeeping.
 */
interface IPushRuntimeConnectionObserver {
    fun onPingSent(atMs: Long) {}
    fun onReadAlive(atMs: Long) {}
    fun onPingTimeout(atMs: Long) {}
    fun onConnectionStateChanged(stateName: String, reason: String, host: String?, message: String)
    fun onConnectionStatusChanged(status: ConnectionStatus)
    fun reconnectionFailed(connection: Connection, error: Exception)
    fun reconnectionSuccessful(connection: Connection)
    fun connectionClosed(connection: Connection, reason: Int, error: Exception?)
    fun connectionStarted(connection: Connection)
    fun notifyConnectionError(reason: Int, exc: Exception?)
    fun requestConnection(source: String, reason: String)
    fun notifyConnectionFailed(activeClients: Any) {}
    
    fun onServiceCreated(service: android.app.Service) {}
    fun onServiceDestroy() {}
    fun onPackageDataCleared(packageName: String) {}
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

    fun shouldNotifyClient(client: PushClientsManager.ClientLoginInfo, type: Int, reasonCode: Int, reasonMessage: String? = null, errorType: String? = null): Boolean = true
    fun computeNotifyDelay(client: PushClientsManager.ClientLoginInfo, type: Int = 0, reasonCode: Int = 0, reasonMessage: String? = null, errorType: String? = null): Long = 0L
    /** Adds product bookkeeping after the stock service listener has been installed. */
    fun configureClientChangeListener(context: Context, manager: PushClientsManager) {}
    
    fun resetAllClients(clients: Any, reason: Int) {}
    fun onClientStatusChanged(client: Any, type: Int, reasonCode: Int, reasonMessage: String?, errorType: String?) {}
    fun ping(connection: Connection): Boolean = false
    fun preparePacket(packet: Packet, packageName: String, session: String?, isConnected: Boolean): Packet? = packet
}
