package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob
import com.xiaomi.smack.XMPPException

class BatchSendMessageJob(
    private val pushService: XMPushService,
    private val blobs: Array<Blob>?,
) : XMPushService.Job(4) {
    override fun getDesc(): String = "batch send message."

    override fun process() {
        try {
            blobs?.let(pushService::batchSendPacket)
        } catch (e: XMPPException) {
            MyLog.e(e)
            pushService.disconnect(10, e)
        }
    }
}
