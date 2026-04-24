package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet

class ClientEventDispatcher {
    private val pushEventProcessor = MIPushEventProcessor()

    fun notifyChannelClosed(
        pushAction: IPushServiceAction,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        reason: Int
    ) {
        ClientEventDispatcherChannelSupport.notifyChannelClosed(pushAction.context, pushAction.runtimeObserver, clientLoginInfo, reason)
    }

    fun notifyChannelOpenResult(
        pushAction: IPushServiceAction,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        succeeded: Boolean,
        reason: Int,
        reasonMessage: String?
    ) {
        ClientEventDispatcherChannelSupport.notifyChannelOpenResult(
            pushAction,
            pushAction.runtimeObserver,
            clientLoginInfo,
            succeeded,
            reason,
            reasonMessage,
            pushEventProcessor
        )
    }

    fun notifyKickedByServer(
        pushAction: IPushServiceAction,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        kickType: String?,
        kickReason: String?
    ) {
        ClientEventDispatcherChannelSupport.notifyKickedByServer(pushAction.context, pushAction.runtimeObserver, clientLoginInfo, kickType, kickReason)
    }

    fun notifyPacketArrival(pushAction: IPushServiceAction, chid: String, blob: Blob) {
        ClientEventDispatcherPacketSupport.notifyPacketArrival(pushAction, chid, blob, pushEventProcessor)
    }

    fun notifyPacketArrival(pushAction: IPushServiceAction, chid: String, packet: Packet) {
        ClientEventDispatcherPacketSupport.notifyPacketArrival(pushAction, chid, packet, pushEventProcessor)
    }

    fun notifyServiceStarted(context: Context, observer: IPushRuntimeObserver) {
        ClientEventDispatcherChannelSupport.notifyServiceStarted(context, observer)
    }
}
