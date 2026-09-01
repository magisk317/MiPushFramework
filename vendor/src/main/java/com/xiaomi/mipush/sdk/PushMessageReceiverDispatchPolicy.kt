package com.xiaomi.mipush.sdk

internal object PushMessageReceiverDispatchPolicy {
    fun isCallMessage(message: MiPushMessage): Boolean =
        message.passThrough == 1 && message.extra?.get("notification_style_type") == "6"
}
