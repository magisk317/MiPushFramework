package com.xiaomi.mipush.sdk

/**
 * App-facing 7.12.4-C callback payload for call/VoIP messages.
 * It intentionally carries only the SDK's public message id and content.
 */
class CallMessage(
    private val msgId: String?,
    private val message: String?,
) {
    fun getMessage(): String? = message

    fun getMsgId(): String? = msgId
}
