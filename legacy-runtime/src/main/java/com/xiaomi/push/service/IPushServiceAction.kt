package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.os.Messenger
import com.xiaomi.smack.Connection

/**
 * Interface defining core XMPushService actions that legacy foundation components need to call.
 */
interface IPushServiceAction {
    val currentConnection: Connection?
    fun executeJob(job: XMPushServiceJob)
    fun executeJobDelayed(job: XMPushServiceJob, delayMs: Long)
    fun removeJobs(type: Int)
    fun removeJobs(job: XMPushServiceJob)
    fun hasJob(type: Int): Boolean
    val isConnected: Boolean
    val isConnecting: Boolean
    fun disconnect(reason: Int, error: Exception?)
    fun connect()
    fun closeChannel(chid: String?, userId: String?, reason: Int, reasonMessage: String?, errorType: String?)
    fun sendMessage(packageName: String?, payload: ByteArray?, cacheIfUnavailable: Boolean)
    fun registerForMiPushApp(payload: ByteArray?, packageName: String?)
    fun postOnCreate()
    fun scheduleConnect(forceReconnect: Boolean)
    fun scheduleRebindChannel(clientLoginInfo: PushClientsManager.ClientLoginInfo?)
    fun handleIntent(intent: Intent)
    fun sendPongIfNeed()
    fun onPong()
    fun stopSelf()
    fun sendBroadcast(intent: Intent, permission: String?)

    val packetSync: PacketSync
    val jobController: JobScheduler
    val context: Context
    val serviceMessenger: Messenger
    val pushPackageName: String
    val notificationHandler: IPushNotificationHandler?
    val runtimeObserver: IPushRuntimeObserver
}
