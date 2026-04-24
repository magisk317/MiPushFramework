package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.smack.packet.CommonPacketExtension
import com.xiaomi.smack.provider.PacketExtensionProvider
import com.xiaomi.smack.provider.ProviderManager
import com.xiaomi.smack.util.StringUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

class CommonPacketExtensionProvider : PacketExtensionProvider {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun parseExtensionFromStartTag(parser: XmlPullParser): CommonPacketExtension? {
            if (parser.eventType != XmlPullParser.START_TAG) {
                return null
            }
            val name = parser.name
            val namespace = parser.namespace
            var text: String? = null
            var children: ArrayList<CommonPacketExtension>? = null

            val attributeNames: Array<String>?
            val attributeValues: Array<String>?
            if (parser.attributeCount > 0) {
                attributeNames = Array(parser.attributeCount) { index -> parser.getAttributeName(index) }
                attributeValues = Array(parser.attributeCount) { index ->
                    StringUtils.unescapeFromXML(parser.getAttributeValue(index))
                }
            } else {
                attributeNames = null
                attributeValues = null
            }

            while (true) {
                when (parser.next()) {
                    XmlPullParser.END_TAG -> {
                        return CommonPacketExtension(name, namespace, attributeNames, attributeValues, text, children)
                    }

                    XmlPullParser.TEXT -> text = parser.text.trim()
                    XmlPullParser.START_TAG -> {
                        if (children == null) {
                            children = arrayListOf()
                        }
                        parseExtensionFromStartTag(parser)?.let(children::add)
                    }
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun parseExtensionFromXpp(content: String): CommonPacketExtension? {
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(StringReader(content))
            }
            while (true) {
                when (parser.eventType) {
                    XmlPullParser.END_DOCUMENT -> return null
                    XmlPullParser.START_TAG -> return parseExtensionFromStartTag(parser)
                    else -> parser.next()
                }
            }
        }
    }

    @Throws(Exception::class)
    override fun parseExtension(xmlPullParser: XmlPullParser): CommonPacketExtension? {
        while (true) {
            when (xmlPullParser.eventType) {
                XmlPullParser.END_DOCUMENT -> return null
                XmlPullParser.START_TAG -> return parseExtensionFromStartTag(xmlPullParser)
                else -> xmlPullParser.next()
            }
        }
    }

    fun register() {
        ProviderManager.getInstance().addExtensionProvider(
            PushServiceConstants.EXTENSION_ELE_NAME_ALL,
            PushServiceConstants.XM_CHAT_NAMESPACE,
            this,
        )
    }
}
