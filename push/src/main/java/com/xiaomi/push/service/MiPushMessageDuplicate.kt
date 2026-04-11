package com.xiaomi.push.service

import com.xiaomi.xmsf.runtime.PushRuntimeDuplicateStore

object MiPushMessageDuplicate {
    const val MAX_MSG_CACHE_COUNT = 25

    @JvmStatic
    fun isDuplicateMessage(xmPushService: XMPushService, pkg: String, messageId: String): Boolean {
        return PushRuntimeDuplicateStore.isDuplicateMessage(xmPushService, pkg, messageId)
    }
}
