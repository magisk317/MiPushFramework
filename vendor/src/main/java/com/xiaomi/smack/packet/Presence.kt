package com.xiaomi.smack.packet

import android.os.Bundle
import com.xiaomi.push.service.PushConstants
import com.xiaomi.smack.util.StringUtils

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/packet/Presence.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class Presence : Packet {
    enum class Mode {
        chat,
        available,
        away,
        xa,
        dnd
    }

    enum class Type {
        available,
        unavailable,
        subscribe,
        subscribed,
        unsubscribe,
        unsubscribed,
        error,
        probe
    }

    private var mode: Mode? = null
    private var priority = Int.MIN_VALUE
    private var status: String? = null
    private var type: Type = Type.available

    constructor(bundle: Bundle?) : super(bundle) {
        if (bundle == null) {
            return
        }
        if (bundle.containsKey(PushConstants.EXTRA_PRES_TYPE)) {
            type = Type.valueOf(bundle.getString(PushConstants.EXTRA_PRES_TYPE)!!)
        }
        if (bundle.containsKey(PushConstants.EXTRA_PRES_STATUS)) {
            status = bundle.getString(PushConstants.EXTRA_PRES_STATUS)
        }
        if (bundle.containsKey(PushConstants.EXTRA_PRES_PRIORITY)) {
            priority = bundle.getInt(PushConstants.EXTRA_PRES_PRIORITY)
        }
        if (bundle.containsKey(PushConstants.EXTRA_PRES_MODE)) {
            mode = Mode.valueOf(bundle.getString(PushConstants.EXTRA_PRES_MODE)!!)
        }
    }

    constructor(type: Type) : super() {
        setType(type)
    }

    constructor(type: Type, status: String?, priority: Int, mode: Mode?) : super() {
        setType(type)
        setStatus(status)
        setPriority(priority)
        setMode(mode)
    }

    fun getMode(): Mode? = mode

    fun getPriority(): Int = priority

    fun getStatus(): String? = status

    fun getType(): Type = type

    fun isAvailable(): Boolean = type == Type.available

    fun isAway(): Boolean = type == Type.available && (mode == Mode.away || mode == Mode.xa || mode == Mode.dnd)

    fun setMode(mode: Mode?) {
        this.mode = mode
    }

    fun setPriority(priority: Int) {
        if (priority >= -128 && priority <= 128) {
            this.priority = priority
            return
        }
        throw IllegalArgumentException("Priority value $priority is not valid. Valid range is -128 through 128.")
    }

    fun setStatus(status: String?) {
        this.status = status
    }

    fun setType(type: Type?) {
        if (type == null) {
            throw NullPointerException("Type cannot be null")
        }
        this.type = type
    }

    override fun toBundle(): Bundle {
        val bundle = super.toBundle()
        bundle.putString(PushConstants.EXTRA_PRES_TYPE, type.toString())
        status?.let { bundle.putString(PushConstants.EXTRA_PRES_STATUS, it) }
        if (priority != Int.MIN_VALUE) {
            bundle.putInt(PushConstants.EXTRA_PRES_PRIORITY, priority)
        }
        val mode = mode
        if (mode != null && mode != Mode.available) {
            bundle.putString(PushConstants.EXTRA_PRES_MODE, mode.toString())
        }
        return bundle
    }

    override fun toXML(): String {
        val sb = StringBuilder()
        sb.append("<presence")
        if (xmlns != null) {
            sb.append(" xmlns=\"")
            sb.append(xmlns)
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
        sb.append(" type=\"")
        sb.append(type)
        sb.append("\">")
        if (status != null) {
            sb.append("<status>")
            sb.append(StringUtils.escapeForXML(status))
            sb.append("</status>")
        }
        if (priority != Int.MIN_VALUE) {
            sb.append("<priority>")
            sb.append(priority)
            sb.append("</priority>")
        }
        val mode = mode
        if (mode != null && mode != Mode.available) {
            sb.append("<show>")
            sb.append(mode)
            sb.append("</show>")
        }
        sb.append(extensionsXML)
        val error = error
        if (error != null) {
            sb.append(error.toXML())
        }
        sb.append("</presence>")
        return sb.toString()
    }
}
