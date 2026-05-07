package com.xiaomi.smack.packet

import android.os.Bundle
import com.xiaomi.push.service.PushConstants
import com.xiaomi.smack.util.StringUtils
import java.util.HashMap

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/packet/IQ.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
open class IQ : Packet {
    private val attributes: MutableMap<String, String> = HashMap()
    private var type: Type? = Type.GET

    class Type private constructor(private val value: String) {
        override fun toString(): String = value

        companion object {
            @JvmField val GET = Type("get")
            @JvmField val SET = Type("set")
            @JvmField val RESULT = Type("result")
            @JvmField val ERROR = Type(Message.MSG_TYPE_ERROR)
            @JvmField val COMMAND = Type("command")

            @JvmStatic
            fun fromString(str: String?): Type? {
                if (str == null) {
                    return null
                }
                val lowerCase = str.lowercase()
                return when {
                    GET.toString() == lowerCase -> GET
                    SET.toString() == lowerCase -> SET
                    ERROR.toString() == lowerCase -> ERROR
                    RESULT.toString() == lowerCase -> RESULT
                    COMMAND.toString() == lowerCase -> COMMAND
                    else -> null
                }
            }
        }
    }

    constructor() : super()

    constructor(bundle: Bundle?) : super(bundle) {
        if (bundle != null && bundle.containsKey(PushConstants.EXTRA_IQ_TYPE)) {
            type = Type.fromString(bundle.getString(PushConstants.EXTRA_IQ_TYPE))
        }
    }

    fun getAttribute(str: String): String? = synchronized(this) {
        attributes[str]
    }

    open fun getChildElementXML(): String? = null

    fun getType(): Type? = type

    fun setAttribute(str: String, str2: String) {
        synchronized(this) {
            attributes[str] = str2
        }
    }

    fun setAttributes(map: Map<String, String>) {
        synchronized(this) {
            attributes.putAll(map)
        }
    }

    fun setType(type: Type?) {
        this.type = type ?: Type.GET
    }

    override fun toBundle(): Bundle {
        val bundle = super.toBundle()
        type?.let { bundle.putString(PushConstants.EXTRA_IQ_TYPE, it.toString()) }
        return bundle
    }

    override fun toXML(): String {
        val sb = StringBuilder()
        sb.append("<iq ")
        if (packetID != null) {
            sb.append("id=\"").append(packetID).append("\" ")
        }
        if (to != null) {
            sb.append("to=\"")
            sb.append(StringUtils.escapeForXML(to))
            sb.append("\" ")
        }
        if (from != null) {
            sb.append("from=\"")
            sb.append(StringUtils.escapeForXML(from))
            sb.append("\" ")
        }
        if (channelId != null) {
            sb.append("chid=\"")
            sb.append(StringUtils.escapeForXML(channelId))
            sb.append("\" ")
        }
        for ((key, value) in attributes) {
            sb.append(StringUtils.escapeForXML(key))
            sb.append("=\"")
            sb.append(StringUtils.escapeForXML(value))
            sb.append("\" ")
        }
        if (type == null) {
            sb.append("type=\"get\">")
        } else {
            sb.append("type=\"")
            sb.append(getType())
            sb.append("\">")
        }
        val childElementXML = getChildElementXML()
        if (childElementXML != null) {
            sb.append(childElementXML)
        }
        sb.append(extensionsXML)
        val error = error
        if (error != null) {
            sb.append(error.toXML())
        }
        sb.append("</iq>")
        return sb.toString()
    }

    companion object {
        @JvmStatic
        fun createErrorResponse(iq: IQ, xMPPError: XMPPError): IQ {
            if (iq.getType() != Type.GET && iq.getType() != Type.SET) {
                throw IllegalArgumentException("IQ must be of type 'set' or 'get'. Original IQ: " + iq.toXML())
            }
            val iq2 = object : IQ() {
                override fun getChildElementXML(): String? {
                    return iq.getChildElementXML()
                }
            }
            iq2.setType(Type.ERROR)
            iq2.packetID = iq.packetID
            iq2.from = iq.to
            iq2.to = iq.from
            iq2.error = xMPPError
            return iq2
        }

        @JvmStatic
        fun createResultIQ(iq: IQ): IQ {
            if (iq.getType() != Type.GET && iq.getType() != Type.SET) {
                throw IllegalArgumentException("IQ must be of type 'set' or 'get'. Original IQ: " + iq.toXML())
            }
            val iq2 = object : IQ() {
                override fun getChildElementXML(): String? = null
            }
            iq2.setType(Type.RESULT)
            iq2.packetID = iq.packetID
            iq2.from = iq.to
            iq2.to = iq.from
            return iq2
        }
    }
}
