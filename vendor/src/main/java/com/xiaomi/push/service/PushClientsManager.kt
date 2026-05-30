package com.xiaomi.push.service

import android.content.Context
import android.os.IBinder
import android.os.Messenger
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import java.util.ArrayList

class PushClientsManager private constructor() {
    private val clients = PushClientsCollection()

    fun interface ClientChangeListener {
        fun onChange()
    }

    class ClientLoginInfo() {
        @JvmField var authMethod: String = ""
        @JvmField var chid: String = ""
        @JvmField var clientExtra: String = ""
        @JvmField var cloudExtra: String = ""
        @JvmField var context: Context? = null
        @JvmField var kick: Boolean = false
        @JvmField var mClientEventDispatcher: ClientEventDispatcher? = null
        @JvmField var pkgName: String = ""
        @JvmField var security: String = ""
        @JvmField var session: String = ""
        @JvmField var token: String = ""
        @JvmField var userId: String = ""
        @JvmField var status: ClientStatus = ClientStatus.unbind
        @JvmField var currentRetrys: Int = 0
        @JvmField val statusChangeListeners: MutableList<ClientStatusListener> = ArrayList()
        @JvmField var notifiedStatus: ClientStatus? = null
        @JvmField var hasPeerSupport: Boolean = false
        @JvmField val timeOutJob: BindTimeoutJob = BindTimeoutJob(this)
        @JvmField var peerWatcher: IBinder.DeathRecipient? = null
        @JvmField val notifyClientJob: PushClientNotifyJob = PushClientNotifyJob(this)
        @JvmField var peer: Messenger? = null
        private var pushAction: IPushServiceAction? = null

        fun interface ClientStatusListener {
            fun onChange(previousStatus: ClientStatus, currentStatus: ClientStatus, reason: Int)
        }

        constructor(pushAction: IPushServiceAction) : this() {
            this.pushAction = pushAction
            addClientStatusListener { _, currentStatus, _ ->
                if (currentStatus == ClientStatus.binding) {
                    pushAction.executeJobDelayed(timeOutJob, 60000L)
                } else {
                    pushAction.removeJobs(timeOutJob)
                }
            }
        }

        fun getPushAction(): IPushServiceAction? = pushAction

        fun notifyClientStatus(type: Int, reasonCode: Int, reasonMessage: String?, errorType: String?) {
            pushAction?.runtimeObserver?.onClientStatusChanged(this, type, reasonCode, reasonMessage, errorType)
        }

        fun shouldNotifyClient(type: Int, reasonCode: Int, errorType: String?): Boolean {
            return pushAction?.runtimeObserver?.shouldNotifyClient(this, type, reasonCode, errorType) ?: true
        }

        fun addClientStatusListener(listener: ClientStatusListener) {
            synchronized(statusChangeListeners) {
                statusChangeListeners.add(listener)
            }
        }

        fun getDesc(type: Int): String {
            return when (type) {
                1 -> "OPEN"
                2 -> "CLOSE"
                3 -> "KICK"
                else -> "unknown"
            }
        }

        fun getNextRetryInterval(): Long {
            return (((Math.random() * 20.0) - 10.0).toLong() + ((currentRetrys + 1) * 15L)) * 1000L
        }

        fun removeClientStatusListener(listener: ClientStatusListener) {
            synchronized(statusChangeListeners) {
                statusChangeListeners.remove(listener)
            }
        }

        fun setStatus(clientStatus: ClientStatus, type: Int, reasonCode: Int, reasonMessage: String?, errorType: String?) {
            synchronized(statusChangeListeners) {
                statusChangeListeners.forEach { it.onChange(status, clientStatus, reasonCode) }
            }
            val previousStatus = status
            if (previousStatus != clientStatus) {
                MyLog.w(
                    String.format(
                        "update the client %7\$s status. %1\$s->%2\$s %3\$s %4\$s %5\$s %6\$s",
                        previousStatus,
                        clientStatus,
                        getDesc(type),
                        PushConstants.getErrorDesc(reasonCode),
                        reasonMessage,
                        errorType,
                        chid,
                    ),
                )
                status = clientStatus
            }
            val dispatcher = mClientEventDispatcher
            if (dispatcher == null) {
                MyLog.e("status changed while the client dispatcher is missing")
                return
            }
            if (clientStatus == ClientStatus.binding) {
                return
            }
            val notifyDelay = pushAction?.runtimeObserver?.computeNotifyDelay(this) ?: 0
            val action = pushAction ?: return
            action.removeJobs(notifyClientJob)
            if (pushAction?.runtimeObserver?.shouldNotifyClient(this, type, reasonCode, errorType) == true) {
                notifyClientStatus(type, reasonCode, reasonMessage, errorType)
            } else {
                action.executeJobDelayed(notifyClientJob.build(type, reasonCode, reasonMessage, errorType), notifyDelay)
            }
        }

        fun unwatch() {
            try {
                val currentPeer = peer
                val currentWatcher = peerWatcher
                if (currentPeer != null && currentWatcher != null) {
                    currentPeer.binder.unlinkToDeath(currentWatcher, 0)
                }
            } catch (_: Exception) {
            }
            notifiedStatus = null
        }

        fun watch(messenger: Messenger?) {
            unwatch()
            try {
                if (messenger != null) {
                    peer = messenger
                    hasPeerSupport = true
                    val watcher = PushClientPeerWatcher(this, messenger)
                    peerWatcher = watcher
                    messenger.binder.linkToDeath(watcher, 0)
                } else {
                    MyLog.i("peer linked with old sdk chid = $chid")
                }
            } catch (e: Exception) {
                MyLog.i("peer linkToDeath err: ${e.message}")
                peer = null
                hasPeerSupport = false
            }
        }

        fun onPeerDied(messenger: Messenger) {
            val action = pushAction ?: return
            action.executeJobDelayed(
                object : XMPushServiceJob(0) {
                    override fun getDesc(): String = "clear peer job"

                    override fun process() {
                        if (messenger == peer) {
                            MyLog.i("clean peer, chid = $chid")
                            peer = null
                        }
                    }
                },
                0L,
            )
            if (chid == "9") {
                action.executeJobDelayed(
                    object : XMPushServiceJob(0) {
                        override fun getDesc(): String = "check peer job"

                        override fun process() {
                            val current = getInstance().getClientLoginInfoByChidAndUserId(chid, userId)
                            if (current != null && current.peer == null) {
                                action.closeChannel(chid, userId, 2, null, null)
                            }
                        }
                    },
                    60000L,
                )
            }
        }

        companion object {
            const val TYPE_CHANNEL_NO_NOTIFY = 0
            const val TYPE_CHANNEL_OPEN_RESULT = 1
            const val TYPE_CHANNEL_CLOSE = 2
            const val TYPE_CHANNEL_SERVER_KICK = 3

            @JvmStatic
            fun getResource(userId: String?): String {
                if (TextUtils.isEmpty(userId)) {
                    return ""
                }
                val index = userId!!.lastIndexOf("/")
                return if (index != -1) userId.substring(index + 1) else userId
            }
        }
    }

    enum class ClientStatus {
        unbind,
        binding,
        binded,
    }

    fun addActiveClient(clientLoginInfo: ClientLoginInfo) {
        synchronized(this) {
            clients.addActiveClient(clientLoginInfo)
        }
    }

    fun addClientChangeListener(clientChangeListener: ClientChangeListener) {
        synchronized(this) {
            clients.addClientChangeListener(clientChangeListener)
        }
    }

    fun deactivateAllClientByChid(chid: String?) {
        if (chid == null) return
        synchronized(this) {
            clients.deactivateAllClientByChid(chid)
        }
    }

    fun deactivateClient(chid: String?, userId: String?) {
        if (chid == null || userId == null) return
        synchronized(this) {
            clients.deactivateClient(chid, userId)
        }
    }

    fun getActiveClientCount(): Int {
        synchronized(this) {
            return clients.getActiveClientCount()
        }
    }

    fun getAllClientLoginInfoByChid(chid: String?): Collection<ClientLoginInfo> {
        if (chid == null) return emptyList()
        synchronized(this) {
            return clients.getAllClientLoginInfoByChid(chid)
        }
    }

    fun getAllClients(): ArrayList<ClientLoginInfo> {
        synchronized(this) {
            return clients.getAllClients()
        }
    }

    fun getClientLoginInfoByChidAndUserId(chid: String?, userId: String?): ClientLoginInfo? {
        if (chid == null || userId == null) return null
        synchronized(this) {
            return clients.getClientLoginInfoByChidAndUserId(chid, userId)
        }
    }

    fun notifyConnectionFailed(context: Context) {
        synchronized(this) {
            val action = getAllClients().firstOrNull()?.getPushAction()
            action?.runtimeObserver?.notifyConnectionFailed(clients.getActiveClientMaps())
        }
    }

    fun queryChannelIdByPackage(packageName: String?): List<String> {
        if (packageName == null) return emptyList()
        synchronized(this) {
            val result = ArrayList<String>()
            clients.getActiveClientMaps().forEach { clients ->
                clients.values.forEach { client ->
                    if (packageName == client.pkgName) {
                        result.add(client.chid)
                    }
                }
            }
            return result
        }
    }

    fun resetAllClients(context: Context, reason: Int) {
        synchronized(this) {
            val action = getAllClients().firstOrNull()?.getPushAction()
            action?.runtimeObserver?.resetAllClients(clients.getActiveClientMaps(), reason)
        }
    }

    fun removeActiveClients() {
        synchronized(this) {
            clients.removeActiveClients()
        }
    }

    fun removeAllClientChangeListeners() {
        synchronized(this) {
            clients.removeAllClientChangeListeners()
        }
    }

    fun removeClientChangeListener(clientChangeListener: ClientChangeListener) {
        synchronized(this) {
            clients.removeClientChangeListener(clientChangeListener)
        }
    }

    companion object {
        @Volatile
        private var instance: PushClientsManager? = null

        @JvmStatic
        fun getInstance(): PushClientsManager {
            return instance ?: synchronized(PushClientsManager::class.java) {
                instance ?: PushClientsManager().also { instance = it }
            }
        }

        @JvmStatic
        fun getSmtpLocalPart(userId: String?): String? {
            if (TextUtils.isEmpty(userId)) {
                return null
            }
            val index = userId!!.indexOf("@")
            return if (index > 0) userId.substring(0, index) else userId
        }
    }
}
