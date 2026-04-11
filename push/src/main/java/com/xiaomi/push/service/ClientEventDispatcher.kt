package com.xiaomi.push.service

import android.content.Context
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet
import io.github.magisk317.mipush.common.utils.Utils

class ClientEventDispatcher {
    private val pushEventProcessor = MIPushEventProcessor()

    fun notifyChannelClosed(
        context: Context?,
        clientLoginInfo: PushClientsManager.ClientLoginInfo?,
        reason: Int
    ) {
        if (clientLoginInfo == null) return
        val usedContext = context ?: Utils.getApplication() ?: return
        ClientEventDispatcherChannelSupport.notifyChannelClosed(usedContext, clientLoginInfo, reason)
    }

    fun notifyChannelOpenResult(
        context: Context?,
        clientLoginInfo: PushClientsManager.ClientLoginInfo?,
        succeeded: Boolean,
        reason: Int,
        reasonMessage: String?
    ) {
        if (clientLoginInfo == null) return
        val usedContext = context ?: Utils.getApplication() ?: return
        ClientEventDispatcherChannelSupport.notifyChannelOpenResult(
            usedContext,
            clientLoginInfo,
            succeeded,
            reason,
            reasonMessage,
            pushEventProcessor
        )
    }

    fun notifyKickedByServer(
        context: Context?,
        clientLoginInfo: PushClientsManager.ClientLoginInfo?,
        kickType: String?,
        kickReason: String?
    ) {
        if (clientLoginInfo == null) return
        val usedContext = context ?: Utils.getApplication() ?: return
        ClientEventDispatcherChannelSupport.notifyKickedByServer(usedContext, clientLoginInfo, kickType, kickReason)
    }

    fun notifyPacketArrival(pushService: XMPushService, chid: String, blob: Blob) {
        ClientEventDispatcherPacketSupport.notifyPacketArrival(pushService, chid, blob, pushEventProcessor)
    }

    fun notifyPacketArrival(pushService: XMPushService, chid: String, packet: Packet) {
        ClientEventDispatcherPacketSupport.notifyPacketArrival(pushService, chid, packet, pushEventProcessor)
    }

    fun notifyServiceStarted(context: Context) {
        ClientEventDispatcherChannelSupport.notifyServiceStarted(context)
    }
}
