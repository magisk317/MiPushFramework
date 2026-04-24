package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

interface PacketSendCallback {
    fun onAnswer(str: String, str2: String, str3: String, str4: String)

    fun onFail(packetSendFailCause: PacketSendFailCause, str: String)

    fun onSent()
}
