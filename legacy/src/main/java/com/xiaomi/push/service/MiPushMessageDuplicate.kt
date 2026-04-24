package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

object MiPushMessageDuplicate {
    const val MAX_MSG_CACHE_COUNT = 25

    @JvmStatic
    fun isDuplicateMessage(pkg: String, messageId: String, observer: IPushRuntimeObserver): Boolean {
        return observer.isDuplicate(pkg, messageId)
    }
}
