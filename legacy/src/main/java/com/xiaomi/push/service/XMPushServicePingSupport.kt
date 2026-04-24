package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

object XMPushServicePingSupport {
    @JvmStatic
    fun onPong(pingCallBacks: ArrayList<PingCallBack>) {
        ArrayList(pingCallBacks).forEach { it.pingFollowUpAction() }
    }
}
