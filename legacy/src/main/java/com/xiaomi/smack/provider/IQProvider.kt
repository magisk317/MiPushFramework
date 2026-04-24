package com.xiaomi.smack.provider
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.smack.packet.IQ
import org.xmlpull.v1.XmlPullParser

interface IQProvider {
    @Throws(Exception::class)
    fun parseIQ(xmlPullParser: XmlPullParser): IQ
}
