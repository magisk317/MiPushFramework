package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.string.XMStringUtils

class PacketHelper private constructor() {
    companion object {
        private var currentMsgId = 0L
        private var prefix = ""

        @JvmStatic
        fun generatePacketID(): String {
            if (prefix.isEmpty()) {
                prefix = XMStringUtils.generateRandomString(4)
            }
            return buildString {
                append(prefix)
                append(currentMsgId++)
            }
        }
    }
}
