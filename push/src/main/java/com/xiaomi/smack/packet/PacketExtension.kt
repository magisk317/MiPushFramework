package com.xiaomi.smack.packet

interface PacketExtension {
    fun getElementName(): String

    fun getNamespace(): String

    fun toXML(): String
}
