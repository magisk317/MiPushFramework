package com.xiaomi.push.service

object XMPushServicePingSupport {
    @JvmStatic
    fun onPong(pingCallBacks: ArrayList<PingCallBack>) {
        ArrayList(pingCallBacks).forEach { it.pingFollowUpAction() }
    }
}
