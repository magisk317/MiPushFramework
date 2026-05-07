package com.xiaomi.push.service

import android.content.Intent
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet

class BlobReceiveJob(
    private val service: IPushServiceAction,
    private val blob: Blob,
) : XMPushServiceJob(TYPE_RECEIVE_MSG) {
    override fun getDesc(): String = "receive a message."

    override fun process() {
        service.packetSync.onBlobReceive(blob)
    }
}

class PacketReceiveJob(
    private val service: IPushServiceAction,
    private val packet: Packet,
) : XMPushServiceJob(TYPE_RECEIVE_MSG) {
    override fun getDesc(): String = "receive a message."

    override fun process() {
        service.packetSync.onPacketReceive(packet)
    }
}

class ConnectJob(
    private val service: IPushServiceAction,
) : XMPushServiceJob(TYPE_CONNECT) {
    override fun getDesc(): String = "do reconnect.."

    override fun process() {
        if (service.isConnected) {
            MyLog.w("already connected. skip connect job.")
            return
        }
        service.connect()
    }
}

class DisconnectJob(
    private val service: IPushServiceAction,
    val reason: Int,
    val e: Exception?,
) : XMPushServiceJob(TYPE_DISCONNECT) {
    override fun getDesc(): String = "disconnect the connection."

    override fun process() {
        service.disconnect(reason, e)
    }
}

class InitJob(
    private val service: IPushServiceAction,
) : XMPushServiceJob(TYPE_INIT) {
    override fun getDesc(): String = "Init Job"

    override fun process() {
        service.postOnCreate()
    }
}

class IntentJob(
    private val service: IPushServiceAction,
    private val intent: Intent,
) : XMPushServiceJob(TYPE_HANDLE_INTENT) {
    override fun getDesc(): String = "Handle intent action = ${intent.action}"

    override fun process() {
        service.handleIntent(intent)
    }
}

class KillJob(
    private val service: IPushServiceAction,
) : XMPushServiceJob(TYPE_QUIT) {
    override fun getDesc(): String = "ask the job queue to quit"

    override fun process() {
        service.jobController.quit()
    }
}

class ResetConnectionJob(
    private val service: IPushServiceAction,
) : XMPushServiceJob(TYPE_RESET_CONNECT) {
    override fun getDesc(): String = "reset the connection."

    override fun process() {
        service.disconnect(11, null)
        service.connect()
    }
}
