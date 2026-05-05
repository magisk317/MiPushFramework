package com.xiaomi.smack.util

import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.CommonPacketExtensionProvider
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.RC4Cryption
import com.xiaomi.smack.Connection
import com.xiaomi.smack.XMPPException
import com.xiaomi.smack.packet.CommonPacketExtension
import com.xiaomi.smack.packet.IQ
import com.xiaomi.smack.packet.Message
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.packet.Presence
import com.xiaomi.smack.packet.StreamError
import com.xiaomi.smack.packet.XMPPError
import com.xiaomi.smack.provider.ProviderManager
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.lang.reflect.Method
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/util/PacketParserUtils.java
 * This runtime keeps the local introspection parser and MiPush extension constants.
 */
object PacketParserUtils {
    private const val PROPERTIES_NAMESPACE = "http://www.jivesoftware.com/xmlns/xmpp/properties"
    private var sDecryptedMsgParser: XmlPullParser? = null

    @Throws(Exception::class)
    private fun decode(cls: Class<*>, str: String): Any? {
        return when (cls.name) {
            "java.lang.String" -> str
            "boolean" -> str.toBoolean()
            "int" -> str.toInt()
            "long" -> str.toLong()
            "float" -> str.toFloat()
            "double" -> str.toDouble()
            "java.lang.Class" -> Class.forName(str)
            else -> null
        }
    }

    private fun getLanguageAttribute(xmlPullParser: XmlPullParser): String? {
        for (i in 0 until xmlPullParser.attributeCount) {
            val attributeName = xmlPullParser.getAttributeName(i)
            if (
                "xml:lang" == attributeName ||
                ("lang" == attributeName && "xml" == xmlPullParser.getAttributePrefix(i))
            ) {
                return xmlPullParser.getAttributeValue(i)
            }
        }
        return null
    }

    @Throws(Exception::class)
    private fun parseContent(xmlPullParser: XmlPullParser): String {
        var content = ""
        val depth = xmlPullParser.depth
        while (true) {
            if (xmlPullParser.next() == XmlPullParser.END_TAG && xmlPullParser.depth == depth) {
                return content
            }
            content += xmlPullParser.text
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun parseError(xmlPullParser: XmlPullParser): XMPPError {
        var code = "-1"
        var type: String? = null
        var message: String? = null
        var condition: String? = null
        var reason: String? = null
        val extensions = ArrayList<CommonPacketExtension>()
        for (i in 0 until xmlPullParser.attributeCount) {
            when (xmlPullParser.getAttributeName(i)) {
                "code" -> code = xmlPullParser.getAttributeValue("", "code")
                "type" -> type = xmlPullParser.getAttributeValue("", "type")
                "reason" -> reason = xmlPullParser.getAttributeValue("", "reason")
            }
        }
        var done = false
        while (!done) {
            when (xmlPullParser.next()) {
                XmlPullParser.START_TAG -> {
                    if (xmlPullParser.name == "text") {
                        message = xmlPullParser.nextText()
                    } else {
                        val name = xmlPullParser.name
                        val namespace = xmlPullParser.namespace
                        if ("urn:ietf:params:xml:ns:xmpp-stanzas" == namespace) {
                            condition = name
                        } else {
                            parsePacketExtension(name, namespace, xmlPullParser)?.let { extensions.add(it) }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (xmlPullParser.name == Message.MSG_TYPE_ERROR) {
                        done = true
                    }
                }
                XmlPullParser.TEXT -> message = xmlPullParser.text
            }
        }
        return XMPPError(code.toInt(), type ?: "cancel", reason, condition, message, extensions)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun parseIQ(xmlPullParser: XmlPullParser, connection: Connection): IQ? {
        val id = xmlPullParser.getAttributeValue("", "id")
        val to = xmlPullParser.getAttributeValue("", "to")
        val from = xmlPullParser.getAttributeValue("", "from")
        val chid = xmlPullParser.getAttributeValue("", "chid")
        val type = IQ.Type.fromString(xmlPullParser.getAttributeValue("", "type"))
        val attributes = HashMap<String, String>()
        for (i in 0 until xmlPullParser.attributeCount) {
            val attributeName = xmlPullParser.getAttributeName(i)
            attributes[attributeName] = xmlPullParser.getAttributeValue("", attributeName)
        }
        var iq: IQ? = null
        var error: XMPPError? = null
        var done = false
        while (!done) {
            when (xmlPullParser.next()) {
                XmlPullParser.START_TAG -> {
                    val name = xmlPullParser.name
                    val namespace = xmlPullParser.namespace
                    if (name == Message.MSG_TYPE_ERROR) {
                        error = parseError(xmlPullParser)
                    } else {
                        iq = IQ()
                        parsePacketExtension(name, namespace, xmlPullParser)?.let { iq.addExtension(it) }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (xmlPullParser.name == "iq") {
                        done = true
                    }
                }
            }
        }
        val result = if (iq == null) {
            if (IQ.Type.GET == type || IQ.Type.SET == type) {
                val response = object : IQ() {
                    override fun getChildElementXML(): String? = null
                }
                response.packetID = id
                response.to = from
                response.from = to
                response.setType(IQ.Type.ERROR)
                response.channelId = chid
                response.error = XMPPError(XMPPError.Condition.feature_not_implemented)
                connection.sendPacket(response)
                MyLog.e("iq usage error. send packet in packet parser.")
                return null
            }
            object : IQ() {
                override fun getChildElementXML(): String? = null
            }
        } else {
            iq
        }
        result.packetID = id
        result.to = to
        result.channelId = chid
        result.from = from
        result.setType(type)
        result.error = error
        result.setAttributes(attributes)
        return result
    }

    @JvmStatic
    @Throws(Exception::class)
    fun parseMessage(xmlPullParser: XmlPullParser): Packet {
        if ("1" == xmlPullParser.getAttributeValue("", "s")) {
            val chid = xmlPullParser.getAttributeValue("", "chid")
            val id = xmlPullParser.getAttributeValue("", "id")
            val from = xmlPullParser.getAttributeValue("", "from")
            val to = xmlPullParser.getAttributeValue("", "to")
            val type = xmlPullParser.getAttributeValue("", "type")
            var clientInfo = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(chid, to)
            if (clientInfo == null) {
                clientInfo = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(chid, from)
            }
            if (clientInfo == null) {
                throw XMPPException("the channel id is wrong while receiving a encrypted message")
            }
            var packet: Packet? = null
            var done = false
            while (!done) {
                when (xmlPullParser.next()) {
                    XmlPullParser.START_TAG -> {
                        if ("s" != xmlPullParser.name) {
                            throw XMPPException("error while receiving a encrypted message with wrong format")
                        }
                        if (xmlPullParser.next() != XmlPullParser.TEXT) {
                            throw XMPPException("error while receiving a encrypted message with wrong format")
                        }
                        val text = xmlPullParser.text.orEmpty()
                        if ("5" == chid || "6" == chid) {
                            val emptyAttributes: Array<String>? = null
                            return Message().apply {
                                channelId = chid
                                encrypted = true
                                this.from = from
                                this.to = to
                                packetID = id
                                this.type = type
                                addExtension(CommonPacketExtension("s", null, emptyAttributes, emptyAttributes).apply { this.text = text })
                            }
                        }
                        resetDecryptedMsgParser(RC4Cryption.decrypt(RC4Cryption.generateKeyForRC4(clientInfo.security, id), text))
                        val decryptedParser = sDecryptedMsgParser ?: throw XmlPullParserException("decrypted parser is not initialized")
                        decryptedParser.next()
                        packet = parseMessage(decryptedParser)
                    }
                    XmlPullParser.END_TAG -> {
                        if (xmlPullParser.name == "message") {
                            done = true
                        }
                    }
                }
            }
            return packet ?: throw XMPPException("error while receiving a encrypted message with wrong format")
        }

        val message = Message()
        message.packetID = xmlPullParser.getAttributeValue("", "id") ?: Packet.ID_NOT_AVAILABLE
        message.to = xmlPullParser.getAttributeValue("", "to")
        message.from = xmlPullParser.getAttributeValue("", "from")
        message.channelId = xmlPullParser.getAttributeValue("", "chid")
        message.appId = xmlPullParser.getAttributeValue("", PushServiceConstants.EXTENSION_ATTRIBUTE_OPENPLATFORM_APPID)
        var transient: String? = null
        try {
            transient = xmlPullParser.getAttributeValue("", "transient")
        } catch (e: Exception) {
            // Keep the stock parser's best-effort attribute handling.
        }
        try {
            val seq = xmlPullParser.getAttributeValue("", "seq")
            if (!TextUtils.isEmpty(seq)) {
                message.seq = seq
            }
        } catch (e: Exception) {
        }
        try {
            val mSeq = xmlPullParser.getAttributeValue("", "mseq")
            if (!TextUtils.isEmpty(mSeq)) {
                message.mSeq = mSeq
            }
        } catch (e: Exception) {
        }
        try {
            val fSeq = xmlPullParser.getAttributeValue("", "fseq")
            if (!TextUtils.isEmpty(fSeq)) {
                message.fSeq = fSeq
            }
        } catch (e: Exception) {
        }
        try {
            val status = xmlPullParser.getAttributeValue("", "status")
            if (!TextUtils.isEmpty(status)) {
                message.status = status
            }
        } catch (e: Exception) {
        }
        message.setIsTransient(!TextUtils.isEmpty(transient) && transient.equals("true", ignoreCase = true))
        message.type = xmlPullParser.getAttributeValue("", "type")
        val language = getLanguageAttribute(xmlPullParser)
        if (language == null || "" == language.trim()) {
            Packet.getDefaultLanguage()
        } else {
            message.language = language
        }
        var done = false
        var thread: String? = null
        while (!done) {
            when (xmlPullParser.next()) {
                XmlPullParser.START_TAG -> {
                    val name = xmlPullParser.name
                    val namespace = xmlPullParser.namespace.takeUnless { TextUtils.isEmpty(it) }
                        ?: PushServiceConstants.XM_CHAT_GROUP_NAMESPACE
                    when (name) {
                        "subject" -> {
                            getLanguageAttribute(xmlPullParser)
                            message.subject = parseContent(xmlPullParser)
                        }
                        "body" -> {
                            val encode = xmlPullParser.getAttributeValue("", "encode")
                            val content = parseContent(xmlPullParser)
                            if (TextUtils.isEmpty(encode)) {
                                message.body = content
                            } else {
                                message.setBody(content, encode)
                            }
                        }
                        "thread" -> {
                            if (thread == null) {
                                thread = xmlPullParser.nextText()
                            }
                        }
                        Message.MSG_TYPE_ERROR -> message.error = parseError(xmlPullParser)
                        else -> parsePacketExtension(name, namespace, xmlPullParser)?.let { message.addExtension(it) }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (xmlPullParser.name == "message") {
                        done = true
                    }
                }
            }
        }
        message.thread = thread
        return message
    }

    @JvmStatic
    @Throws(Exception::class)
    fun parsePacketExtension(str: String?, str2: String?, xmlPullParser: XmlPullParser): CommonPacketExtension? {
        val extensionProvider = ProviderManager.getInstance().getExtensionProvider(
            PushServiceConstants.EXTENSION_ELE_NAME_ALL,
            PushServiceConstants.XM_CHAT_NAMESPACE
        )
        if (extensionProvider !is CommonPacketExtensionProvider) {
            return null
        }
        return extensionProvider.parseExtension(xmlPullParser) as? CommonPacketExtension
    }

    @JvmStatic
    @Throws(Exception::class)
    fun parsePresence(xmlPullParser: XmlPullParser): Presence {
        var type = Presence.Type.available
        val typeAttribute = xmlPullParser.getAttributeValue("", "type")
        if (typeAttribute != null && typeAttribute != "") {
            try {
                type = Presence.Type.valueOf(typeAttribute)
            } catch (e: IllegalArgumentException) {
                System.err.println("Found invalid presence type $typeAttribute")
            }
        }
        val presence = Presence(type)
        presence.to = xmlPullParser.getAttributeValue("", "to")
        presence.from = xmlPullParser.getAttributeValue("", "from")
        presence.channelId = xmlPullParser.getAttributeValue("", "chid")
        presence.packetID = xmlPullParser.getAttributeValue("", "id") ?: Packet.ID_NOT_AVAILABLE
        var done = false
        while (!done) {
            when (xmlPullParser.next()) {
                XmlPullParser.START_TAG -> {
                    val name = xmlPullParser.name
                    val namespace = xmlPullParser.namespace
                    when (name) {
                        "status" -> presence.setStatus(xmlPullParser.nextText())
                        "priority" -> {
                            try {
                                presence.setPriority(xmlPullParser.nextText().toInt())
                            } catch (e: NumberFormatException) {
                            } catch (e: IllegalArgumentException) {
                                presence.setPriority(0)
                            }
                        }
                        "show" -> {
                            val text = xmlPullParser.nextText()
                            try {
                                presence.setMode(Presence.Mode.valueOf(text))
                            } catch (e: IllegalArgumentException) {
                                System.err.println("Found invalid presence mode $text")
                            }
                        }
                        Message.MSG_TYPE_ERROR -> presence.error = parseError(xmlPullParser)
                        else -> parsePacketExtension(name, namespace, xmlPullParser)?.let { presence.addExtension(it) }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (xmlPullParser.name == "presence") {
                        done = true
                    }
                }
            }
        }
        return presence
    }

    @JvmStatic
    @Throws(Exception::class)
    fun parseStreamError(xmlPullParser: XmlPullParser): StreamError {
        var streamError: StreamError? = null
        var done = false
        while (!done) {
            when (xmlPullParser.next()) {
                XmlPullParser.START_TAG -> streamError = StreamError(xmlPullParser.name)
                XmlPullParser.END_TAG -> {
                    if (xmlPullParser.name == Message.MSG_TYPE_ERROR) {
                        done = true
                    }
                }
            }
        }
        return streamError ?: StreamError(null)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun parseWithIntrospection(str: String, cls: Class<*>, xmlPullParser: XmlPullParser): Any {
        var done = false
        val instance = cls.getDeclaredConstructor().newInstance()
        while (!done) {
            when (xmlPullParser.next()) {
                XmlPullParser.START_TAG -> {
                    val name = xmlPullParser.name
                    val text = xmlPullParser.nextText()
                    val suffix = name.replaceFirstChar { it.uppercaseChar() }
                    val getter: Method = instance.javaClass.getMethod("get$suffix")
                    val returnType = getter.returnType
                    val decoded = decode(returnType, text)
                    instance.javaClass.getMethod("set$suffix", returnType).invoke(instance, decoded)
                }
                XmlPullParser.END_TAG -> {
                    if (xmlPullParser.name == str) {
                        done = true
                    }
                }
            }
        }
        return instance
    }

    @Throws(XmlPullParserException::class)
    private fun resetDecryptedMsgParser(bytes: ByteArray) {
        if (sDecryptedMsgParser == null) {
            try {
                sDecryptedMsgParser = XmlPullParserFactory.newInstance().newPullParser().apply {
                    setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
                }
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
            }
        }
        sDecryptedMsgParser?.setInput(InputStreamReader(ByteArrayInputStream(bytes)))
    }
}
