package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

enum class PacketSendFailCause {
    EXCEPTION_OCCUR,
    SERVICE_DOWN,
    CONNECTION_DOWN,
    ANSWER_ERROR,
    ANSWER_TIMEOUT,
}
