package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob
import com.xiaomi.smack.XMPPException

class SendMessageJob(
    private val pushService: XMPushService,
    private val blob: Blob?,
) : XMPushService.Job(4) {
    override fun getDesc(): String = "send a message."

    override fun process() {
        try {
            blob?.let(pushService::sendPacket)
        } catch (e: XMPPException) {
            MyLog.e(e)
            pushService.disconnect(10, e)
        }
    }
}
