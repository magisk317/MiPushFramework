package com.xiaomi.push.service

import android.os.Message
import android.os.RemoteException
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob

internal object PushClientStatusSupport {
    @JvmStatic
    fun isSpecialError(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        type: Int,
        reason: Int,
        errorType: String?,
    ): Boolean {
        return when (type) {
            1 -> {
                if (clientLoginInfo.status == PushClientsManager.ClientStatus.binded ||
                    !clientLoginInfo.getPushService().isConnected ||
                    reason == 21
                ) {
                    false
                } else {
                    !(reason == 7 && errorType == "wait")
                }
            }

            2 -> clientLoginInfo.getPushService().isConnected
            3 -> errorType != "wait"
            else -> false
        }
    }

    @JvmStatic
    fun notifyClientStatus(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        type: Int,
        reason: Int,
        reasonMessage: String?,
        errorType: String?,
    ) {
        val currentStatus = clientLoginInfo.status
        clientLoginInfo.notifiedStatus = currentStatus
        when (type) {
            2 -> clientLoginInfo.mClientEventDispatcher.notifyChannelClosed(
                clientLoginInfo.context,
                clientLoginInfo,
                reason,
            )

            3 -> clientLoginInfo.mClientEventDispatcher.notifyKickedByServer(
                clientLoginInfo.context,
                clientLoginInfo,
                errorType,
                reasonMessage,
            )

            1 -> {
                val isBound = currentStatus == PushClientsManager.ClientStatus.binded
                if (!isBound && errorType == "wait") {
                    clientLoginInfo.currentRetrys++
                } else if (isBound) {
                    clientLoginInfo.currentRetrys = 0
                    if (clientLoginInfo.peer != null) {
                        try {
                            clientLoginInfo.peer.send(
                                Message.obtain(
                                    null,
                                    16,
                                    clientLoginInfo.getPushService().serviceMessenger,
                                ),
                            )
                        } catch (_: RemoteException) {
                        }
                    }
                }
                clientLoginInfo.mClientEventDispatcher.notifyChannelOpenResult(
                    clientLoginInfo.getPushService(),
                    clientLoginInfo,
                    isBound,
                    reason,
                    reasonMessage,
                )
            }
        }
    }

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun shouldNotifyClient(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        type: Int,
        reason: Int,
        errorType: String?,
    ): Boolean {
        val notifiedStatus = clientLoginInfo.notifiedStatus
        if (notifiedStatus == null || !clientLoginInfo.hasPeerSupport) {
            return true
        }
        if (notifiedStatus == clientLoginInfo.status) {
            logInfo(" status recovered, don't notify client:${clientLoginInfo.chid}")
            return false
        }
        if (clientLoginInfo.peer == null || !clientLoginInfo.hasPeerSupport) {
            logInfo("peer died, ignore notify ${clientLoginInfo.chid}")
            return false
        }
        logInfo("Peer alive notify status to client:${clientLoginInfo.chid}")
        return true
    }

    @JvmStatic
    fun getDesc(type: Int): String {
        return when (type) {
            1 -> "OPEN"
            2 -> Blob.CMD_CLOSE
            3 -> Blob.CMD_KICK
            else -> "unknown"
        }
    }

    @JvmStatic
    fun notifyStatusListeners(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        clientStatus: PushClientsManager.ClientStatus,
        notifyType: Int,
    ) {
        synchronized(clientLoginInfo.statusChangeListeners) {
            clientLoginInfo.statusChangeListeners.forEach {
                it.onChange(clientLoginInfo.status, clientStatus, notifyType)
            }
        }
    }

    @JvmStatic
    fun computeNotifyDelay(clientLoginInfo: PushClientsManager.ClientLoginInfo): Int {
        if (clientLoginInfo.notifiedStatus == null || !clientLoginInfo.hasPeerSupport) {
            return 0
        }
        return if (clientLoginInfo.peer != null && clientLoginInfo.hasPeerSupport) 1000 else 10100
    }

    private fun logInfo(message: String) {
        runCatching { MyLog.i(message) }
    }
}
