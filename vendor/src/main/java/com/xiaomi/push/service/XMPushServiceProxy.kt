package com.xiaomi.push.service

/**
 * Proxy to access XMPushService actions from the foundation layer.
 * Settable by the product layer.
 */
object XMPushServiceProxy {
    private var instance: IPushServiceAction? = null

    @JvmStatic
    fun set(action: IPushServiceAction) {
        instance = action
    }

    @JvmStatic
    fun get(): IPushServiceAction? = instance
}
