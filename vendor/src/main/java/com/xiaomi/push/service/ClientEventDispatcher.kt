package com.xiaomi.push.service

import android.content.Context
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet
import io.github.magisk317.xposed.logging.MagiskOtel

class ClientEventDispatcher {
    private val pushEventProcessor = MIPushEventProcessor()

    fun notifyChannelClosed(
        pushAction: IPushServiceAction,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        reason: Int
    ) {
        ClientEventDispatcherChannelSupport.notifyChannelClosed(pushAction.context, pushAction.runtimeObserver, clientLoginInfo, reason)
        MagiskOtel.event(
            name = "push.network",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "push",
                "stage" to "channel_closed",
                "reason" to reason.toString(),
                "target_package" to clientLoginInfo.pkgName,
            ),
            statusOk = true,
        )
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
        MagiskOtel.event(
            name = "push.network",
            attributes = mapOf(
                "result" to if (succeeded) "ok" else "error",
                "duration_ms" to "0",
                "process" to "push",
                "stage" to "channel_open",
                "reason" to reason.toString(),
                "reason_token" to when (reasonMessage) {
                    null, "" -> "none"
                    "token-expired" -> "token_expired"
                    else -> "other"
                },
                "target_package" to clientLoginInfo.pkgName,
            ),
            statusOk = succeeded,
        )
    }

    fun notifyKickedByServer(
        pushAction: IPushServiceAction,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        kickType: String?,
        kickReason: String?
    ) {
        ClientEventDispatcherChannelSupport.notifyKickedByServer(pushAction.context, pushAction.runtimeObserver, clientLoginInfo, kickType, kickReason)
        MagiskOtel.event(
            name = "push.network",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to "0",
                "process" to "push",
                "stage" to "channel_kick",
                "reason" to (kickType?.takeIf { it.length <= 32 } ?: "kick"),
                "target_package" to clientLoginInfo.pkgName,
            ),
            statusOk = true,
        )
    }

    fun notifyPacketArrival(pushAction: IPushServiceAction, chid: String, blob: Blob) {
        ClientEventDispatcherPacketSupport.notifyPacketArrival(pushAction, chid, blob, pushEventProcessor)
    }

    fun notifyPacketArrival(pushAction: IPushServiceAction, chid: String, packet: Packet) {
        ClientEventDispatcherPacketSupport.notifyPacketArrival(pushAction, chid, packet, pushEventProcessor)
    }

    fun notifyServiceStarted(context: Context, observer: IPushRuntimeObserver) {
        ClientEventDispatcherChannelSupport.notifyServiceStarted(context, observer)
        MagiskOtel.event(
            name = "push.lifecycle",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "push",
                "stage" to "client_event_service_started",
                "package_name" to context.packageName.orEmpty(),
            ),
            statusOk = true,
        )
    }
}
