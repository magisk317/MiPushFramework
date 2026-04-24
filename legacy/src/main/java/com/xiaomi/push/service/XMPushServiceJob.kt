package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.LogTag
import com.xiaomi.channel.commonutils.logger.MyLog

abstract class XMPushServiceJob(type: Int) : JobScheduler.Job(type) {
    companion object {
        const val TYPE_BIND_TIMEOUT = 12
        const val TYPE_BIND_UNBIND = 9
        const val TYPE_CLEAR_ACCOUNT_CACHE = 14
        const val TYPE_CONNECT = 1
        const val TYPE_CONNECTING_TIMEOUT = 10
        const val TYPE_DISCONNECT = 2
        const val TYPE_HANDLE_INTENT = 15
        const val TYPE_INIT = 65535
        const val TYPE_MAX = 16
        const val TYPE_MIN = 1
        const val TYPE_NOTYPE_JOB = 0
        const val TYPE_PING_TIMEOUT = 13
        const val TYPE_PREPARE_MIPUSH_ACCOUNT = 11
        const val TYPE_QUIT = 5
        const val TYPE_RECEIVE_CHALLENGE = 7
        const val TYPE_RECEIVE_MSG = 8
        const val TYPE_RECEIVE_TIMEOUT = 6
        const val TYPE_RESET_CONNECT = 3
        const val TYPE_SEND_MSG = 4
    }

    abstract fun getDesc(): String

    abstract fun process()

    override fun run() {
        if (type != TYPE_SEND_MSG && type != TYPE_RECEIVE_MSG) {
            MyLog.w(LogTag.TAG_JOB, getDesc())
        }
        process()
    }
}
