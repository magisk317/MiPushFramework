package com.xiaomi.push.service

import com.magisk317.push.hook.HookTraceCompat
import com.magisk317.service.XMPushServiceLifecycleBridge
import com.magisk317.XMPushUtils
import com.xiaomi.xmpush.thrift.XmPushActionContainer

class XmPushActionOperator(
    private val xmPushService: XMPushService
) {
    fun sendMessage(sendMsgContainer: XmPushActionContainer, packageName: String) {
        XMPushServiceLifecycleBridge.ensureCreated(xmPushService)
        val msgBytes = XMPushUtils.packToBytes(sendMsgContainer)
        HookTraceCompat.onSendMessage(packageName, msgBytes.size)
        xmPushService.sendMessage(packageName, msgBytes, false)
    }
}
