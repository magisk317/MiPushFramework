package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob
import com.xiaomi.smack.XMPPException

class SendMessageJob(
    private val pushAction: IPushServiceAction,
    private val blob: Blob?,
) : XMPushServiceJob(4) {
    override fun getDesc(): String = "send a message."

    override fun process() {
        try {
            // Updated to use packetSync or direct action if available
            if (blob != null) {
                pushAction.sendMessage(blob.packageName, blob.payload, true)
            }
        } catch (e: Exception) {
            MyLog.e(e)
            pushAction.disconnect(10, e)
        }
    }
}
