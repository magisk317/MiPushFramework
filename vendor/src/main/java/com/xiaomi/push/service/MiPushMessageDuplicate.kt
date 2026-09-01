package com.xiaomi.push.service

/**
 * Stock-facing adapter for the product duplicate callback. It delegates cache ownership to the
 * runtime observer and intentionally does not maintain a second message-ID cache.
 */
object MiPushMessageDuplicate {
    const val MAX_MSG_CACHE_COUNT = 25

    @JvmStatic
    fun isDuplicateMessage(pkg: String, messageId: String, observer: IPushRuntimeObserver): Boolean {
        return observer.isDuplicate(pkg, messageId)
    }
}
