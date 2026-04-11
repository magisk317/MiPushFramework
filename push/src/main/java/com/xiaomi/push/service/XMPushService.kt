package com.xiaomi.push.service

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.IBinder
import android.os.Messenger
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract
import com.xiaomi.network.HostManager
import com.xiaomi.push.service.timers.Alarm
import com.xiaomi.slim.Blob
import com.xiaomi.slim.SlimConnection
import com.xiaomi.smack.Connection
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.smack.ConnectionListener
import com.xiaomi.smack.PacketListener
import com.xiaomi.smack.XMPPException
import java.util.ArrayList
import java.util.Collections

@Suppress("MemberVisibilityCanBePrivate")
open class XMPushService : Service(), ConnectionListener {
    lateinit var connectionConfiguration: ConnectionConfiguration
    lateinit var clientEventDispatcher: ClientEventDispatcher
    var currentConnection: Connection? = null
    var extremePowerModeObserver: ContentObserver? = null
    lateinit var reconnectionManager: ReconnectionManager
    var regionName: String? = null
    var screenStateReceiver: ScreenStateReceiver? = null
    lateinit var slimConnection: SlimConnection
    var superPowerModeObserver: ContentObserver? = null
    protected var jobClazz: Class<*> = XMJobService::class.java
    lateinit var packetSync: PacketSync
    lateinit var jobController: JobScheduler
    lateinit var serviceMessenger: Messenger
    var connectionChangeReceiver: ConnectionChangeReceiver? = null
    var lastAliveAt: Long = 0L

    private val lifecycleDelegate = XMPushServiceLifecycleDelegate(this)
    private val connectionDelegate = XMPushServiceConnectionDelegate(this)
    private val packetDelegate = XMPushServicePacketDelegate(this)
    private val intentDelegate = XMPushServiceIntentDelegate(this, packetDelegate)
    private var falldownStart = 0
    private var falldownEnd = 0
    private val packetListener: PacketListener = XMPushServiceInboundPacketListener(this)
    private val networkListeners = Collections.synchronizedCollection(ArrayList<NetworkListener>())
    val pingCallBacks = ArrayList<PingCallBack>()

    abstract class Job(type: Int) : XMPushServiceJob(type)

    val servicePacketListener: PacketListener
        get() = packetListener

    val networkListenersSnapshot: Array<NetworkListener>
        get() = networkListeners.toTypedArray()

    val isConnected: Boolean
        get() = currentConnection?.isConnected == true

    val isConnecting: Boolean
        get() = currentConnection?.isConnecting == true

    fun broadcastNetworkAvailable(available: Boolean) {
        connectionDelegate.broadcastNetworkAvailable(available)
    }

    fun canOpenForegroundService(): Boolean {
        return XMPushServiceEnvironment.canOpenForegroundService(this)
    }

    fun checkAlive(force: Boolean) {
        connectionDelegate.checkAlive(force)
    }

    fun clearPingCallbacks() {
        synchronized(pingCallBacks) {
            pingCallBacks.clear()
        }
    }

    fun closeAllChannelByChid(chid: String, reason: Int) {
        XMPushServiceChannelSupport.closeAllChannelByChid(this, chid, reason)
    }

    fun connect() {
        connectionDelegate.connect()
    }

    fun doAWLogic(intent: Intent) {
        var cmd = 0
        try {
            com.xiaomi.push.service.awake.module.AwakeManager.getInstance(applicationContext).setSendDataIml(PushLayerProcessIml())
            val packageName = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
            val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD) ?: return
            val notification = com.xiaomi.xmpush.thrift.XmPushActionNotification()
            com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils.convertByteArrayToThriftObject(notification, payload)
            val appId = notification.appId
            val extra = notification.extra
            if (extra != null) {
                val awakeInfo = extra[PushConstants.EXTRA_AWAKE_APP_AWAKE_INFO]
                val onlineCmd = extra[PushConstants.EXTRA_AWAKE_APP_ONLINE_CMD]
                if (!onlineCmd.isNullOrEmpty()) {
                    cmd = onlineCmd.toIntOrNull() ?: 0
                    if (!packageName.isNullOrEmpty() && !appId.isNullOrEmpty() && !awakeInfo.isNullOrEmpty()) {
                        com.xiaomi.push.service.awake.module.AwakeManager.getInstance(applicationContext)
                            .wakeup(this, awakeInfo, cmd, packageName, appId)
                    }
                }
            }
        } catch (e: org.apache.thrift.TException) {
            MyLog.e("aw_logic: translate fail. ${e.message}")
        }
    }

    fun doAWPingCMD(intent: Intent, intervalSeconds: Int) {
        val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        val cacheMessage = intent.getBooleanExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, true)
        val notification = com.xiaomi.xmpush.thrift.XmPushActionNotification()
        try {
            com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils.convertByteArrayToThriftObject(notification, payload)
            com.xiaomi.channel.commonutils.misc.ScheduledJobManager.getInstance(applicationContext)
                .addRepeatJob(AwakeAppPingJob(notification, java.lang.ref.WeakReference(this), cacheMessage), intervalSeconds)
        } catch (_: org.apache.thrift.TException) {
            MyLog.e("aw_ping : send help app ping  error")
        }
    }

    fun enableForegroundService() = Unit

    fun ensureRegionAvaible(): String? {
        return XMPushServiceEnvironment.ensureRegionAvailable(this)
    }

    fun executeJobNow(job: Job) {
        jobController.executeJobNow(job)
    }

    fun getFalldownTimeRange(): IntArray {
        return XMPushServiceEnvironment.getFalldownTimeRange(this) ?: intArrayOf()
    }

    fun handleIntent(intent: Intent?) {
        intentDelegate.handleIntent(intent)
    }

    fun isExtremePowerSaveMode(): Boolean {
        return XMPushServiceEnvironment.isExtremePowerSaveMode(this)
    }

    private fun isInFalldownTimeRange(): Boolean {
        return XMPushServiceEnvironment.isInFalldownTimeRange(falldownStart, falldownEnd)
    }

    fun isPushEnabled(): Boolean {
        return PushConstants.PUSH_SERVICE_PACKAGE_NAME == packageName || !MIPushAppInfo.getInstance(this).isPushDisabled(packageName)
    }

    fun isSuperPowerModeEnable(): Boolean {
        return XMPushServiceEnvironment.isSuperPowerModeEnable(this)
    }

    fun networkChanged() {
        lifecycleDelegate.networkChanged()
    }

    fun postOnCreate() {
        lifecycleDelegate.postOnCreate()
    }

    fun preparePacket(packet: com.xiaomi.smack.packet.Packet, packageName: String, session: String?): com.xiaomi.smack.packet.Packet? {
        return PushPacketRuntime.preparePacket(packet, packageName, session, PushClientsManager.getInstance(), isConnected).packet
    }

    fun shouldCheckAlive(): Boolean {
        if (System.currentTimeMillis() - lastAliveAt < MessageInfoContract.TIMEOUT) {
            return false
        }
        return Network.isConnected(this)
    }

    fun shouldFalldown(): Boolean {
        return XMPushServiceEnvironment.shouldFalldown(this, falldownStart, falldownEnd)
    }

    fun unregisterReceiverSafely(receiver: BroadcastReceiver?) {
        if (receiver != null) {
            try {
                unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                MyLog.e(e)
            }
        }
    }

    fun updateAlarmTimer() {
        if (!shouldReconnect()) {
            Alarm.stop()
        } else if (!Alarm.isAlive()) {
            Alarm.registerPing(true)
        }
    }

    fun addPingCallBack(pingCallBack: PingCallBack) {
        synchronized(pingCallBacks) {
            pingCallBacks.add(pingCallBack)
        }
    }

    @Throws(XMPPException::class)
    fun batchSendPacket(blobs: Array<Blob>) {
        connectionDelegate.batchSendPacket(blobs)
    }

    @Throws(XMPPException::class)
    fun batchSendPacket(packets: Array<com.xiaomi.smack.packet.Packet>) {
        connectionDelegate.batchSendPacket(packets)
    }

    fun closeChannel(chid: String?, userId: String?, reason: Int, reasonMessage: String?, errorType: String?) {
        if (chid == null || userId == null) return
        connectionDelegate.closeChannel(chid, userId, reason, reasonMessage.orEmpty(), errorType.orEmpty())
    }

    override fun connectionClosed(connection: Connection, reason: Int, error: Exception?) {
        lifecycleDelegate.connectionClosed(connection, reason, error)
    }

    override fun connectionStarted(connection: Connection) {
        lifecycleDelegate.connectionStarted(connection)
    }

    open fun createClientEventDispatcher(): ClientEventDispatcher {
        return ClientEventDispatcher()
    }

    fun disconnect(reason: Int, error: Exception?) {
        connectionDelegate.disconnect(reason, error)
    }

    fun executeJob(job: Job) {
        executeJobDelayed(job, 0L)
    }

    fun executeJobDelayed(job: Job, delayMs: Long) {
        try {
            jobController.executeJobDelayed(job, delayMs)
        } catch (e: IllegalStateException) {
            MyLog.w("can't execute job err = ${e.message}")
        }
    }

    fun clearCurrentConnection() {
        currentConnection = null
    }

    fun clearConnectionChangeReceiver() {
        connectionChangeReceiver = null
    }

    fun clearScreenStateReceiver() {
        screenStateReceiver = null
    }

    fun setFalldownWindow(start: Int, end: Int) {
        falldownStart = start
        falldownEnd = end
    }

    fun addNetworkListener(networkListener: NetworkListener) {
        networkListeners.add(networkListener)
    }

    fun clearNetworkListeners() {
        networkListeners.clear()
    }

    fun hasJob(type: Int): Boolean {
        return jobController.hasJob(type)
    }

    fun hasJob(job: Job): Boolean {
        return jobController.hasJob(job.type, job)
    }

    fun isConnectAllowed(): Boolean {
        return shouldReconnect()
    }

    fun isPushDisabled(): Boolean {
        return XMPushServiceStateSupport.isPushDisabled(this)
    }

    override fun onBind(intent: Intent): IBinder {
        return serviceMessenger.binder
    }

    fun ensureConnectionChangeReceiver() {
        if (connectionChangeReceiver == null) {
            connectionChangeReceiver = ConnectionChangeReceiver(this)
            registerReceiver(connectionChangeReceiver, IntentFilter(CONNECTIVITY_ACTION))
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleDelegate.onCreate()
    }

    override fun onDestroy() {
        lifecycleDelegate.onDestroy()
        super.onDestroy()
        MyLog.w("Service destroyed")
    }

    fun onPong() {
        XMPushServicePingSupport.onPong(pingCallBacks)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return lifecycleDelegate.onStartCommand(intent, flags, startId)
    }

    override fun reconnectionFailed(connection: Connection, error: Exception) {
        lifecycleDelegate.reconnectionFailed(connection, error)
    }

    override fun reconnectionSuccessful(connection: Connection) {
        lifecycleDelegate.reconnectionSuccessful(connection)
    }

    fun registerForMiPushApp(payload: ByteArray?, packageName: String?) {
        packetDelegate.registerForMiPushApp(payload, packageName)
    }

    fun removeJobs(type: Int) {
        jobController.removeJobs(type)
    }

    fun removeJobs(job: JobScheduler.Job) {
        jobController.removeJobs(job.type, job)
    }

    fun removePingCallBack(pingCallBack: PingCallBack) {
        synchronized(pingCallBacks) {
            pingCallBacks.remove(pingCallBack)
        }
    }

    fun scheduleConnect(forceReconnect: Boolean) {
        reconnectionManager.tryReconnect(forceReconnect)
    }

    fun scheduleRebindChannel(clientLoginInfo: PushClientsManager.ClientLoginInfo?) {
        connectionDelegate.scheduleRebindChannel(clientLoginInfo)
    }

    fun sendMessage(packageName: String?, payload: ByteArray?, cacheIfUnavailable: Boolean) {
        packetDelegate.sendMessage(packageName, payload, cacheIfUnavailable)
    }

    @Throws(XMPPException::class)
    fun sendPacket(blob: Blob) {
        connectionDelegate.sendPacket(blob)
    }

    @Throws(XMPPException::class)
    fun sendPacket(packet: com.xiaomi.smack.packet.Packet) {
        connectionDelegate.sendPacket(packet)
    }

    fun sendPongIfNeed() {
        connectionDelegate.sendPongIfNeed()
    }

    fun setConnectingTimeout() {
        connectionDelegate.setConnectingTimeout()
    }

    fun shouldReconnect(): Boolean {
        return XMPushServiceStateSupport.shouldReconnect(this)
    }

    companion object {
        const val ACTION_CONNECTIVITY_INFO = "com.xiaomi.channel.CONNECTIVITY_INFO"
        const val ACTION_MILIAO_PUSH_STARTED = "com.xiaomi.channel.PUSH_STARTED"
        const val CHECK_ALIVE_INTERVAL = 30000
        const val CONNECTING_TIMEOUT = 15000
        private const val CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE"
        const val LOGIN_TIMEOUT = 30000
        const val MSG_DATA_KEY_HOST = "msg_data_hots"
        const val TIMER_RESET_CONNECTION = 30000

        init {
            HostManager.addReservedHost(ConnectionConfiguration.XMPP_SERVER_CHINA_HOST_P, ConnectionConfiguration.XMPP_SERVER_CHINA_HOST_P)
        }

        @JvmStatic
        fun getPushServiceNotification(context: android.content.Context): Notification {
            return XMPushServiceEnvironment.getPushServiceNotification(context)
        }
    }
}
