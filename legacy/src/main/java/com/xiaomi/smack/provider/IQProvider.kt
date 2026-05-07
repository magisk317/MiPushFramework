package com.xiaomi.smack.provider

import com.xiaomi.smack.packet.IQ
import org.xmlpull.v1.XmlPullParser

interface IQProvider {
    @Throws(Exception::class)
    fun parseIQ(xmlPullParser: XmlPullParser): IQ
}
