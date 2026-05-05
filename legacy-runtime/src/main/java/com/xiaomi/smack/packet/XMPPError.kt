package com.xiaomi.smack.packet

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import com.xiaomi.push.mpcd.Constants
import com.xiaomi.push.service.PushConstants
import java.util.Collections

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/packet/XMPPError.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class XMPPError {
    private var applicationExtensions: MutableList<CommonPacketExtension>? = null
    var code: Int
        private set
    private var condition: String? = null
    private var message: String? = null
    private var reason: String? = null
    private var type: String? = null

    class Condition constructor(private val value: String) {
        override fun toString(): String = value

        companion object {
            @JvmField val interna_server_error = Condition("internal-server-error")
            @JvmField val forbidden = Condition("forbidden")
            @JvmField val bad_request = Condition("bad-request")
            @JvmField val conflict = Condition("conflict")
            @JvmField val feature_not_implemented = Condition("feature-not-implemented")
            @JvmField val gone = Condition("gone")
            @JvmField val item_not_found = Condition("item-not-found")
            @JvmField val jid_malformed = Condition("jid-malformed")
            @JvmField val no_acceptable = Condition("not-acceptable")
            @JvmField val not_allowed = Condition("not-allowed")
            @JvmField val not_authorized = Condition("not-authorized")
            @JvmField val payment_required = Condition("payment-required")
            @JvmField val recipient_unavailable = Condition("recipient-unavailable")
            @JvmField val redirect = Condition("redirect")
            @JvmField val registration_required = Condition("registration-required")
            @JvmField val remote_server_error = Condition("remote-server-error")
            @JvmField val remote_server_not_found = Condition("remote-server-not-found")
            @JvmField val remote_server_timeout = Condition("remote-server-timeout")
            @JvmField val resource_constraint = Condition("resource-constraint")
            @JvmField val service_unavailable = Condition("service-unavailable")
            @JvmField val subscription_required = Condition("subscription-required")
            @JvmField val undefined_condition = Condition("undefined-condition")
            @JvmField val unexpected_request = Condition("unexpected-request")
            @JvmField val request_timeout = Condition("request-timeout")
        }
    }

    constructor(code: Int) {
        this.code = code
        message = null
    }

    constructor(code: Int, message: String?) {
        this.code = code
        this.message = message
    }

    constructor(
        code: Int,
        type: String?,
        reason: String?,
        condition: String?,
        message: String?,
        applicationExtensions: List<CommonPacketExtension>?
    ) {
        this.code = code
        this.type = type
        this.reason = reason
        this.condition = condition
        this.message = message
        this.applicationExtensions = applicationExtensions?.let { ArrayList(it) }
    }

    constructor(bundle: Bundle) {
        code = bundle.getInt(PushConstants.EXTRA_ERROR_CODE)
        if (bundle.containsKey(PushConstants.EXTRA_ERROR_TYPE)) {
            type = bundle.getString(PushConstants.EXTRA_ERROR_TYPE)
        }
        condition = bundle.getString(PushConstants.EXTRA_ERROR_CONDITION)
        reason = bundle.getString(PushConstants.EXTRA_ERROR_REASON)
        message = bundle.getString(PushConstants.EXTRA_ERROR_MESSAGE)
        val parcelableArray = BundleCompat.getParcelableArray(bundle, PushConstants.EXTRA_EXTENSIONS, Parcelable::class.java)
        if (parcelableArray != null) {
            applicationExtensions = ArrayList(parcelableArray.size)
            for (parcelable in parcelableArray) {
                val fromBundle = CommonPacketExtension.parseFromBundle(parcelable as Bundle)
                if (fromBundle != null) {
                    applicationExtensions?.add(fromBundle)
                }
            }
        }
    }

    constructor(condition: Condition) {
        code = 0
        init(condition)
        message = null
    }

    constructor(condition: Condition, message: String?) {
        code = 0
        init(condition)
        this.message = message
    }

    private fun init(condition: Condition) {
        this.condition = condition.toString()
    }

    fun addExtension(commonPacketExtension: CommonPacketExtension) {
        synchronized(this) {
            if (applicationExtensions == null) {
                applicationExtensions = ArrayList()
            }
            applicationExtensions?.add(commonPacketExtension)
        }
    }

    fun getCondition(): String? = condition

    fun getExtension(str: String?, str2: String?): PacketExtension? = synchronized(this) {
        val list = applicationExtensions
        if (list == null || str == null || str2 == null) {
            return@synchronized null
        }
        for (commonPacketExtension in list) {
            if (str == commonPacketExtension.getElementName() && str2 == commonPacketExtension.getNamespace()) {
                return@synchronized commonPacketExtension
            }
        }
        null
    }

    fun getExtensions(): List<CommonPacketExtension> = synchronized(this) {
        val list = applicationExtensions
        if (list == null) {
            Collections.emptyList()
        } else {
            Collections.unmodifiableList(list)
        }
    }

    fun getMessage(): String? = message

    fun getReason(): String? = reason

    fun getType(): String? = type

    fun setExtension(list: List<CommonPacketExtension>?) {
        synchronized(this) {
            applicationExtensions = list?.let { ArrayList(it) }
        }
    }

    fun toBundle(): Bundle {
        val bundle = Bundle()
        type?.let { bundle.putString(PushConstants.EXTRA_ERROR_TYPE, it) }
        bundle.putInt(PushConstants.EXTRA_ERROR_CODE, code)
        reason?.let { bundle.putString(PushConstants.EXTRA_ERROR_REASON, it) }
        condition?.let { bundle.putString(PushConstants.EXTRA_ERROR_CONDITION, it) }
        message?.let { bundle.putString(PushConstants.EXTRA_ERROR_MESSAGE, it) }
        val list = applicationExtensions
        if (list != null) {
            val bundleArr = arrayOfNulls<Bundle>(list.size)
            var i = 0
            for (extension in list) {
                val extensionBundle = extension.toBundle()
                if (extensionBundle != null) {
                    bundleArr[i] = extensionBundle
                    i++
                }
            }
            bundle.putParcelableArray(PushConstants.EXTRA_EXTENSIONS, bundleArr)
        }
        return bundle
    }

    override fun toString(): String {
        val sb = StringBuilder()
        condition?.let { sb.append(it) }
        sb.append(Constants.SEPARATOR_LEFT_PARENTESIS)
        sb.append(code)
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS)
        if (message != null) {
            sb.append(" ")
            sb.append(message)
        }
        return sb.toString()
    }

    fun toXML(): String {
        val sb = StringBuilder()
        sb.append("<error code=\"")
        sb.append(code)
        sb.append("\"")
        if (type != null) {
            sb.append(" type=\"")
            sb.append(type)
            sb.append("\"")
        }
        if (reason != null) {
            sb.append(" reason=\"")
            sb.append(reason)
            sb.append("\"")
        }
        sb.append(">")
        if (condition != null) {
            sb.append("<")
            sb.append(condition)
            sb.append(" xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>")
        }
        if (message != null) {
            sb.append("<text xml:lang=\"en\" xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\">")
            sb.append(message)
            sb.append("</text>")
        }
        for (extension in getExtensions()) {
            sb.append(extension.toXML())
        }
        sb.append("</error>")
        return sb.toString()
    }
}
