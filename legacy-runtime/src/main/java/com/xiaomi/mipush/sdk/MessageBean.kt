package com.xiaomi.mipush.sdk

import android.text.TextUtils

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/MessageBean.java
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
