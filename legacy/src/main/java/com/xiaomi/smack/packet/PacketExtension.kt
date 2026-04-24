package com.xiaomi.smack.packet
import io.github.magisk317.mipush.protocol.model.*

interface PacketExtension {
    fun getElementName(): String

    fun getNamespace(): String

    fun toXML(): String
}
