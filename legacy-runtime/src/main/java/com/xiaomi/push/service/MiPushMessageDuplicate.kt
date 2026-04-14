package com.xiaomi.push.service

object MiPushMessageDuplicate {
    const val MAX_MSG_CACHE_COUNT = 25

    @JvmStatic
    fun isDuplicateMessage(pkg: String, messageId: String, observer: IPushRuntimeObserver): Boolean {
        return observer.isDuplicate(pkg, messageId)
    }
}
