package com.xiaomi.mipush.sdk

import android.text.TextUtils

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
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
