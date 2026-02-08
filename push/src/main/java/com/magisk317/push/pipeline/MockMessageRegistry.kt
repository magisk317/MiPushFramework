package com.magisk317.push.pipeline

import com.elvishew.xlog.XLog
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.util.concurrent.atomic.AtomicReference

object MockMessageRegistry {
    private val logger = XLog.tag("MockMessageRegistry").build()
    private val mockMessageId = AtomicReference<String?>(null)

    @JvmStatic
    fun mark(container: XmPushActionContainer?) {
        val id = MessageIdentity.fromContainer(container) ?: return
        mockMessageId.set(id)
        logger.d("marked mock message id=$id")
    }

    @JvmStatic
    fun consumeIfMatched(container: XmPushActionContainer?): Boolean {
        val id = MessageIdentity.fromContainer(container) ?: return false
        return consumeIfMatched(id)
    }

    @JvmStatic
    fun consumeIfMatched(messageId: String?): Boolean {
        if (messageId.isNullOrBlank()) return false
        val current = mockMessageId.get() ?: return false
        if (messageId != current) return false
        val consumed = mockMessageId.compareAndSet(current, null)
        if (consumed) {
            logger.i("consumed mock message id=$messageId")
        }
        return consumed
    }
}
