package com.xiaomi.mipush.sdk

/**
 * Pure server-action classification used by [PushMessageProcessor]'s existing ACK branch.
 *
 * This intentionally accepts only metadata extras: container access and all ACK side effects
 * remain in the stock-facing processor façade.
 */
internal object PushMessageProcessorMessageClassification {
    fun isHybridMessage(extra: Map<String, String>?): Boolean {
        val action = extra?.get(Constants.EXTRA_KEY_PUSH_SERVER_ACTION)
        return action == Constants.EXTRA_VALUE_HYBRID_MESSAGE ||
            action == Constants.EXTRA_VALUE_PLATFORM_MESSAGE
    }
}
