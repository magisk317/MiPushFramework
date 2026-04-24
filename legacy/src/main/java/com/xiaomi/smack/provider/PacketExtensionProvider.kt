package com.xiaomi.smack.provider
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.smack.packet.PacketExtension
import org.xmlpull.v1.XmlPullParser

interface PacketExtensionProvider {
    @Throws(Exception::class)
    fun parseExtension(xmlPullParser: XmlPullParser): PacketExtension?
}
