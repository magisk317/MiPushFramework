package com.xiaomi.push.service

import android.content.Intent
import com.xiaomi.channel.commonutils.logger.LogTag
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob
import com.xiaomi.smack.XMPPException
import com.xiaomi.smack.packet.Packet

class BlobReceiveJob(
    private val service: XMPushService,
    private val blob: Blob,
) : XMPushService.Job(XMPushServiceJob.TYPE_RECEIVE_MSG) {
    override fun getDesc(): String = "receive a message."

    override fun process() {
        service.packetSync.onBlobReceive(blob)
    }
}

class PacketReceiveJob(
    private val service: XMPushService,
    private val packet: Packet,
) : XMPushService.Job(XMPushServiceJob.TYPE_RECEIVE_MSG) {
    override fun getDesc(): String = "receive a message."

    override fun process() {
        service.packetSync.onPacketReceive(packet)
    }
}

class ConnectJob(
    private val service: XMPushService,
) : XMPushService.Job(XMPushServiceJob.TYPE_CONNECT) {
    override fun getDesc(): String = "do reconnect.."

    override fun process() {
        if (service.shouldReconnect()) {
            service.connect()
        } else {
            MyLog.w("should not connect. quit the job.")
        }
    }
}

class DisconnectJob(
    private val service: XMPushService,
    val reason: Int,
    val e: Exception?,
) : XMPushService.Job(XMPushServiceJob.TYPE_DISCONNECT) {
    override fun getDesc(): String = "disconnect the connection."

    override fun process() {
        service.disconnect(reason, e)
    }
}

class InitJob(
    private val service: XMPushService,
) : XMPushService.Job(XMPushServiceJob.TYPE_INIT) {
    override fun getDesc(): String = "Init Job"

    override fun process() {
        service.postOnCreate()
    }
}

class IntentJob(
    private val service: XMPushService,
    private val intent: Intent,
) : XMPushService.Job(XMPushServiceJob.TYPE_HANDLE_INTENT) {
    override fun getDesc(): String = "Handle intent action = ${intent.action}"

    override fun process() {
        service.handleIntent(intent)
    }
}

class KillJob(
    private val service: XMPushService,
) : XMPushService.Job(XMPushServiceJob.TYPE_QUIT) {
    override fun getDesc(): String = "ask the job queue to quit"

    override fun process() {
        service.jobController.quit()
    }
}

class ResetConnectionJob(
    private val service: XMPushService,
) : XMPushService.Job(XMPushServiceJob.TYPE_RESET_CONNECT) {
    override fun getDesc(): String = "reset the connection."

    override fun process() {
        service.disconnect(11, null)
        if (service.shouldReconnect()) {
            service.connect()
        }
    }
}
