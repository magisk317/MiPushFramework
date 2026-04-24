package com.xiaomi.slim
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.smack.Connection
import com.xiaomi.smack.XMPPException
import com.xiaomi.smack.packet.Message
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.util.PacketParserUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

class PacketParser {
    private val mParser: XmlPullParser

    init {
        val factory = XmlPullParserFactory.newInstance()
        mParser = factory.newPullParser()
        mParser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
    }

    @Throws(Exception::class)
    fun parse(
        data: ByteArray,
        connection: Connection,
    ): Packet? {
        mParser.setInput(InputStreamReader(ByteArrayInputStream(data)))
        mParser.next()
        val eventType = mParser.eventType
        val name = mParser.name

        if (eventType != XmlPullParser.START_TAG) {
            return null
        }

        return when (name) {
            "message" -> PacketParserUtils.parseMessage(mParser)
            "iq" -> PacketParserUtils.parseIQ(mParser, connection)
            "presence" -> PacketParserUtils.parsePresence(mParser)
            "stream" -> null
            Message.MSG_TYPE_ERROR -> throw XMPPException(PacketParserUtils.parseStreamError(mParser))
            "warning" -> {
                mParser.next()
                mParser.name == "multi-login"
                null
            }
            "bind" -> null
            else -> null
        }
    }
}
