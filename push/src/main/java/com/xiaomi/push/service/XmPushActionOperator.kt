package com.xiaomi.push.service

import com.nihility.XMPushUtils
import com.xiaomi.xmpush.thrift.XmPushActionContainer

class XmPushActionOperator(
    private val xmPushService: XMPushService
) {
    fun sendMessage(sendMsgContainer: XmPushActionContainer, packageName: String) {
        val msgBytes = XMPushUtils.packToBytes(sendMsgContainer)
        xmPushService.sendMessage(packageName, msgBytes, false)
    }
}
