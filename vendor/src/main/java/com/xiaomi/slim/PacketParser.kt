package com.xiaomi.slim

import com.xiaomi.smack.Connection
import com.xiaomi.smack.XMPPException
import com.xiaomi.smack.packet.Message
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.util.PacketParserUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/pa/g.java
 * Stock class name is obfuscated as pa.g; this file keeps the deobfuscated com.xiaomi.slim.PacketParser API.
 */
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
