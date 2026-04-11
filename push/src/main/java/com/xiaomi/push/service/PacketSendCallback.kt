package com.xiaomi.push.service

interface PacketSendCallback {
    fun onAnswer(str: String, str2: String, str3: String, str4: String)

    fun onFail(packetSendFailCause: PacketSendFailCause, str: String)

    fun onSent()
}
