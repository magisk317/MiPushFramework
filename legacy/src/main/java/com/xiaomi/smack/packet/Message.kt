package com.xiaomi.smack.packet

import android.os.Bundle
import android.text.TextUtils
import com.xiaomi.push.service.PushConstants
import com.xiaomi.smack.util.StringUtils

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/packet/Message.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 * This runtime keeps legacy MiPush message attributes such as seq/mseq/fseq/status and encryption.
 */
class Message : Packet {
    var appId: String? = null
    var body: String? = null
    var bodyEncoding: String? = null
        private set
    var encrypted: Boolean = false
    var fSeq: String? = ""
    var language: String? = null
    var mSeq: String? = ""
    var seq: String? = ""
    var status: String? = ""
    var subject: String? = null
    var thread: String? = null
    var type: String? = null
    private var mTransient: Boolean = false

    constructor() : super()

    constructor(bundle: Bundle?) : super(bundle) {
        if (bundle == null) {
            return
        }
        type = bundle.getString(PushConstants.EXTRA_MESSAGE_TYPE)
        language = bundle.getString(PushConstants.EXTRA_MESSAGE_LANGUAGE)
        thread = bundle.getString(PushConstants.EXTRA_MESSAGE_THREAD)
        subject = bundle.getString(PushConstants.EXTRA_MESSAGE_SUBJECT)
        body = bundle.getString(PushConstants.EXTRA_MESSAGE_BODY)
        bodyEncoding = bundle.getString(PushConstants.EXTRA_BODY_ENCODE)
        appId = bundle.getString(PushConstants.EXTRA_MESSAGE_APPID)
        mTransient = bundle.getBoolean(PushConstants.EXTRA_MESSAGE_TRANSIENT, false)
        encrypted = bundle.getBoolean(PushConstants.EXTRA_MESSAGE_ENCRYPT, false)
        seq = bundle.getString(PushConstants.EXTRA_MESSAGE_SEQ)
        mSeq = bundle.getString(PushConstants.EXTRA_MESSAGE_MSEQ)
        fSeq = bundle.getString(PushConstants.EXTRA_MESSAGE_FSEQ)
        status = bundle.getString(PushConstants.EXTRA_MESSAGE_STATUS)
    }

    constructor(str: String?) : super() {
        to = str
    }

    constructor(str: String?, str2: String?) : super() {
        to = str
        type = str2
    }

    override fun equals(other: Any?): Boolean {
        if (this == other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        other as Message
        if (!super.equals(other)) {
            return false
        }
        if (body != other.body) {
            return false
        }
        if (language != other.language) {
            return false
        }
        if (subject != other.subject) {
            return false
        }
        if (thread != other.thread) {
            return false
        }
        return type === other.type
    }

    fun setBody(str: String?, str2: String?) {
        body = str
        bodyEncoding = str2
    }

    fun setIsTransient(z: Boolean) {
        mTransient = z
    }

    override fun hashCode(): Int {
        var result = type?.hashCode() ?: 0
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + (thread?.hashCode() ?: 0)
        result = 31 * result + (language?.hashCode() ?: 0)
        result = 31 * result + (subject?.hashCode() ?: 0)
        return result
    }

    override fun toBundle(): Bundle {
        val bundle = super.toBundle()
        if (!TextUtils.isEmpty(type)) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_TYPE, type)
        }
        language?.let { bundle.putString(PushConstants.EXTRA_MESSAGE_LANGUAGE, it) }
        subject?.let { bundle.putString(PushConstants.EXTRA_MESSAGE_SUBJECT, it) }
        body?.let { bundle.putString(PushConstants.EXTRA_MESSAGE_BODY, it) }
        if (!TextUtils.isEmpty(bodyEncoding)) {
            bundle.putString(PushConstants.EXTRA_BODY_ENCODE, bodyEncoding)
        }
        thread?.let { bundle.putString(PushConstants.EXTRA_MESSAGE_THREAD, it) }
        appId?.let { bundle.putString(PushConstants.EXTRA_MESSAGE_APPID, it) }
        if (mTransient) {
            bundle.putBoolean(PushConstants.EXTRA_MESSAGE_TRANSIENT, true)
        }
        if (!TextUtils.isEmpty(seq)) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_SEQ, seq)
        }
        if (!TextUtils.isEmpty(mSeq)) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_MSEQ, mSeq)
        }
        if (!TextUtils.isEmpty(fSeq)) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_FSEQ, fSeq)
        }
        if (encrypted) {
            bundle.putBoolean(PushConstants.EXTRA_MESSAGE_ENCRYPT, true)
        }
        if (!TextUtils.isEmpty(status)) {
            bundle.putString(PushConstants.EXTRA_MESSAGE_STATUS, status)
        }
        return bundle
    }

    override fun toXML(): String {
        val sb = StringBuilder()
        sb.append("<message")
        if (xmlns != null) {
            sb.append(" xmlns=\"")
            sb.append(xmlns)
            sb.append("\"")
        }
        if (language != null) {
            sb.append(" xml:lang=\"")
            sb.append(language)
            sb.append("\"")
        }
        if (packetID != null) {
            sb.append(" id=\"")
            sb.append(packetID)
            sb.append("\"")
        }
        if (to != null) {
            sb.append(" to=\"")
            sb.append(StringUtils.escapeForXML(to))
            sb.append("\"")
        }
        if (!TextUtils.isEmpty(seq)) {
            sb.append(" seq=\"")
            sb.append(seq)
            sb.append("\"")
        }
        if (!TextUtils.isEmpty(mSeq)) {
            sb.append(" mseq=\"")
            sb.append(mSeq)
            sb.append("\"")
        }
        if (!TextUtils.isEmpty(fSeq)) {
            sb.append(" fseq=\"")
            sb.append(fSeq)
            sb.append("\"")
        }
        if (!TextUtils.isEmpty(status)) {
            sb.append(" status=\"")
            sb.append(status)
            sb.append("\"")
        }
        if (from != null) {
            sb.append(" from=\"")
            sb.append(StringUtils.escapeForXML(from))
            sb.append("\"")
        }
        if (channelId != null) {
            sb.append(" chid=\"")
            sb.append(StringUtils.escapeForXML(channelId))
            sb.append("\"")
        }
        if (mTransient) {
            sb.append(" transient=\"true\"")
        }
        if (!TextUtils.isEmpty(appId)) {
            sb.append(" appid=\"")
            sb.append(appId)
            sb.append("\"")
        }
        if (!TextUtils.isEmpty(type)) {
            sb.append(" type=\"")
            sb.append(type)
            sb.append("\"")
        }
        if (encrypted) {
            sb.append(" s=\"1\"")
        }
        sb.append(">")
        if (subject != null) {
            sb.append("<subject>")
            sb.append(StringUtils.escapeForXML(subject))
            sb.append("</subject>")
        }
        if (body != null) {
            sb.append("<body")
            if (!TextUtils.isEmpty(bodyEncoding)) {
                sb.append(" encode=\"")
                sb.append(bodyEncoding)
                sb.append("\"")
            }
            sb.append(">")
            sb.append(StringUtils.escapeForXML(body))
            sb.append("</body>")
        }
        if (thread != null) {
            sb.append("<thread>")
            sb.append(thread)
            sb.append("</thread>")
        }
        val error = error
        if (MSG_TYPE_ERROR.equals(type, ignoreCase = true) && error != null) {
            sb.append(error.toXML())
        }
        sb.append(extensionsXML)
        sb.append("</message>")
        return sb.toString()
    }

    companion object {
        const val MSG_TYPE_CHAT = "chat"
        const val MSG_TYPE_ERROR = "error"
        const val MSG_TYPE_GROUPCHAT = "groupchat"
        const val MSG_TYPE_HEADLINE = "hearline"
        const val MSG_TYPE_NORMAL = "normal"
        const val MSG_TYPE_PPL = "ppl"
    }
}
