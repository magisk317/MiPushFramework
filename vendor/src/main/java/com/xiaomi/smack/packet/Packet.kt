package com.xiaomi.smack.packet

import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import androidx.core.os.BundleCompat
import com.xiaomi.mipush.sdk.Constants
import com.xiaomi.push.service.PushConstants
import com.xiaomi.smack.util.StringUtils
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/packet/Packet.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
abstract class Packet {
    var channelId: String? = null
    var error: XMPPError? = null
    var from: String? = null
    var packageName: String? = null
    private var packetExtensions: MutableList<CommonPacketExtension> = CopyOnWriteArrayList()
    var packetID: String? = null
        get() {
            if (ID_NOT_AVAILABLE == field) {
                return null
            }
            if (field == null) {
                field = nextID()
            }
            return field
        }
    private val properties: MutableMap<String, Any> = HashMap()
    var to: String? = null
    var xmlns: String? = DEFAULT_XML_NS

    constructor()

    constructor(bundle: Bundle?) {
        if (bundle == null) {
            return
        }
        to = bundle.getString(PushConstants.EXTRA_TO)
        from = bundle.getString(PushConstants.EXTRA_FROM)
        channelId = bundle.getString(PushConstants.EXTRA_CHID)
        packetID = bundle.getString(PushConstants.EXTRA_PACKET_ID)
        val parcelableArray = BundleCompat.getParcelableArray(bundle, PushConstants.EXTRA_EXTENSIONS, Parcelable::class.java)
        if (parcelableArray != null) {
            packetExtensions = ArrayList(parcelableArray.size)
            for (parcelable in parcelableArray) {
                val fromBundle = CommonPacketExtension.parseFromBundle(parcelable as Bundle)
                if (fromBundle != null) {
                    packetExtensions.add(fromBundle)
                }
            }
        }
        val errorBundle = bundle.getBundle(PushConstants.EXTRA_ERROR)
        if (errorBundle != null) {
            error = XMPPError(errorBundle)
        }
    }

    fun addExtension(commonPacketExtension: CommonPacketExtension) {
        packetExtensions.add(commonPacketExtension)
    }

    fun deleteProperty(str: String) {
        synchronized(this) {
            properties.remove(str)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this == other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        other as Packet
        return error == other.error &&
            from == other.from &&
            packetExtensions == other.packetExtensions &&
            packetID == other.packetID &&
            channelId == other.channelId &&
            properties == other.properties &&
            to == other.to &&
            xmlns == other.xmlns
    }

    fun getExtension(str: String): CommonPacketExtension? {
        return getExtension(str, null)
    }

    fun getExtension(str: String, str2: String?): CommonPacketExtension? {
        for (commonPacketExtension in packetExtensions) {
            if (str2 == null || str2 == commonPacketExtension.getNamespace()) {
                if (str == commonPacketExtension.getElementName()) {
                    return commonPacketExtension
                }
            }
        }
        return null
    }

    fun getExtensions(): Collection<CommonPacketExtension> = synchronized(this) {
        Collections.unmodifiableList(ArrayList(packetExtensions))
    }

    protected val extensionsXML: String
        get() = synchronized(this) {
            val sb = StringBuilder()
            for (extension in getExtensions()) {
                sb.append(extension.toXML())
            }
            if (properties.isNotEmpty()) {
                sb.append("<properties xmlns=\"http://www.jivesoftware.com/xmlns/xmpp/properties\">")
                for (name in getPropertyNames()) {
                    val property = getProperty(name)
                    sb.append("<property>")
                    sb.append("<name>")
                    sb.append(StringUtils.escapeForXML(name))
                    sb.append("</name>")
                    sb.append("<value type=\"")
                    when (property) {
                        is Int -> {
                            sb.append("integer\">")
                            sb.append(property)
                            sb.append("</value>")
                        }
                        is Long -> {
                            sb.append("long\">")
                            sb.append(property)
                            sb.append("</value>")
                        }
                        is Float -> {
                            sb.append("float\">")
                            sb.append(property)
                            sb.append("</value>")
                        }
                        is Double -> {
                            sb.append("double\">")
                            sb.append(property)
                            sb.append("</value>")
                        }
                        is Boolean -> {
                            sb.append("boolean\">")
                            sb.append(property)
                            sb.append("</value>")
                        }
                        is String -> {
                            sb.append("string\">")
                            sb.append(StringUtils.escapeForXML(property))
                            sb.append("</value>")
                        }
                        else -> {
                            try {
                                val bytes = ByteArrayOutputStream()
                                ObjectOutputStream(bytes).use { it.writeObject(property) }
                                sb.append("java-object\">")
                                sb.append(StringUtils.encodeBase64(bytes.toByteArray()))
                                sb.append("</value>")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    sb.append("</property>")
                }
                sb.append("</properties>")
            }
            sb.toString()
        }

    fun getProperty(str: String): Any? = synchronized(this) {
        properties[str]
    }

    fun getPropertyNames(): Collection<String> = synchronized(this) {
        Collections.unmodifiableSet(HashSet(properties.keys))
    }

    override fun hashCode(): Int {
        var result = xmlns?.hashCode() ?: 0
        result = 31 * result + (packetID?.hashCode() ?: 0)
        result = 31 * result + (to?.hashCode() ?: 0)
        result = 31 * result + (from?.hashCode() ?: 0)
        result = 31 * result + (channelId?.hashCode() ?: 0)
        result = 31 * result + packetExtensions.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }

    fun removeExtension(commonPacketExtension: CommonPacketExtension) {
        packetExtensions.remove(commonPacketExtension)
    }

    fun setProperty(str: String, obj: Any) {
        synchronized(this) {
            if (obj !is Serializable) {
                throw IllegalArgumentException("Value must be serialiazble")
            }
            properties[str] = obj
        }
    }

    open fun toBundle(): Bundle {
        val bundle = Bundle()
        if (!TextUtils.isEmpty(xmlns)) {
            bundle.putString(PushConstants.EXTRA_EXTENSION_NAMESPACE, xmlns)
        }
        if (!TextUtils.isEmpty(from)) {
            bundle.putString(PushConstants.EXTRA_FROM, from)
        }
        if (!TextUtils.isEmpty(to)) {
            bundle.putString(PushConstants.EXTRA_TO, to)
        }
        if (!TextUtils.isEmpty(packetID)) {
            bundle.putString(PushConstants.EXTRA_PACKET_ID, packetID)
        }
        if (!TextUtils.isEmpty(channelId)) {
            bundle.putString(PushConstants.EXTRA_CHID, channelId)
        }
        error?.let { bundle.putBundle(PushConstants.EXTRA_ERROR, it.toBundle()) }
        val bundleArr = arrayOfNulls<Bundle>(packetExtensions.size)
        var i = 0
        for (extension in packetExtensions) {
            val extensionBundle = extension.toBundle()
            bundleArr[i] = extensionBundle
            i++
        }
        bundle.putParcelableArray(PushConstants.EXTRA_EXTENSIONS, bundleArr)
        return bundle
    }

    abstract fun toXML(): String

    companion object {
        @JvmField
        val DEFAULT_LANGUAGE: String = Locale.getDefault().language.lowercase()
        private var DEFAULT_XML_NS: String? = null
        const val ID_NOT_AVAILABLE = "ID_NOT_AVAILABLE"
        @JvmField
        val XEP_0082_UTC_FORMAT: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private var id = 0L
        private var prefix = StringUtils.randomString(5) + Constants.ACCEPT_TIME_SEPARATOR_SERVER

        @JvmStatic
        fun getDefaultLanguage(): String = DEFAULT_LANGUAGE

        @JvmStatic
        @Synchronized
        fun nextID(): String {
            val result = prefix + id.toString()
            id += 1
            return result
        }

        @JvmStatic
        fun setDefaultXmlns(str: String?) {
            DEFAULT_XML_NS = str
        }
    }
}
