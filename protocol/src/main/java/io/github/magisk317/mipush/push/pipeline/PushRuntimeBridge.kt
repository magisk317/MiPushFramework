package io.github.magisk317.mipush.push.pipeline

import android.content.Context

object PushRuntimeBridge {
    @JvmStatic
    var observer: Observer? = null

    interface Observer {
        fun observeChannelEvent(packageName: String?, action: String, source: String)
        fun observeNotificationEvent(packageName: String?, action: String, source: String)
        fun onPayloadFromServer(context: Context, payload: ByteArray, size: Long, source: String)
    }

    @JvmStatic
    fun observeChannelEvent(packageName: String?, action: String, source: String) {
        observer?.observeChannelEvent(packageName, action, source)
    }

    @JvmStatic
    fun observeNotificationEvent(packageName: String?, action: String, source: String) {
        observer?.observeNotificationEvent(packageName, action, source)
    }

    @JvmStatic
    fun onPayloadFromServer(context: Context, payload: ByteArray, size: Long, source: String) {
        observer?.onPayloadFromServer(context, payload, size, source)
    }
}
