package com.xiaomi.push.service

enum class PacketSendFailCause {
    EXCEPTION_OCCUR,
    SERVICE_DOWN,
    CONNECTION_DOWN,
    ANSWER_ERROR,
    ANSWER_TIMEOUT,
}
