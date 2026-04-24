package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils

class XmPushActionOperator(
    private val xmPushService: XMPushService
) {
    val context: Context? get() = xmPushService.applicationContext

    fun sendMessage(sendMsgContainer: XmPushActionContainer, packageName: String) {
        val msgBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(sendMsgContainer)
        if (msgBytes == null || msgBytes.isEmpty()) {
            MyLog.w("failed to serialize container")
            return
        }
        xmPushService.sendMessage(packageName, msgBytes, false)
    }
}
