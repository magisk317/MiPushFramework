package com.xiaomi.push.service

import android.os.Bundle
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.profile.MessageProfiling
import com.xiaomi.smack.packet.CommonPacketExtension
import com.xiaomi.smack.packet.Message
import com.xiaomi.smack.packet.Packet

object ServiceClientPacketSupport {
    @JvmStatic
    fun buildMessageBundles(messages: Array<Message>): Array<Bundle?> {
        return Array(messages.size) { index ->
            val message = messages[index]
            attachProfiling(message)
            MyLog.v("SEND:${message.toXML()}")
            message.toBundle()
        }
    }

    @JvmStatic
    fun buildMessageBundle(message: Message): Bundle? {
        attachProfiling(message)
        MyLog.v("SEND:${message.toXML()}")
        return message.toBundle()
    }

    @JvmStatic
    fun buildPacketBundle(packet: Packet): Bundle? {
        return packet.toBundle()?.also {
            MyLog.v("SEND:${packet.toXML()}")
        }
    }

    private fun attachProfiling(message: Message) {
        val prefString = MessageProfiling.getPrefString()
        if (TextUtils.isEmpty(prefString)) {
            return
        }
        val attrs: Array<String>? = null
        val profilingExtension = CommonPacketExtension("pf", null, attrs, attrs)
        val sentExtension = CommonPacketExtension("sent", null, attrs, attrs)
        sentExtension.text = prefString
        profilingExtension.appendChild(sentExtension)
        message.addExtension(profilingExtension)
    }
}
