package com.xiaomi.mipush.sdk
import io.github.magisk317.mipush.protocol.model.*

import android.text.TextUtils

class MessageBean {
    @JvmField
    var count: Int = 0
    @JvmField
    var messageId: String = ""

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MessageBean) {
            return false
        }
        return !TextUtils.isEmpty(other.messageId) && other.messageId == this.messageId
    }
}
