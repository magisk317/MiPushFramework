package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.runtime.store.kmp.DuplicateMessagePolicy as KmpDuplicateMessagePolicy

/**
 * Android-side adapter delegating to the platform-neutral duplicate message policy.
 * Kept for source compatibility with existing callers.
 */
object DuplicateMessagePolicy {
    internal const val MAX_TRACKED_MESSAGES = 2_048

    private val policy = KmpDuplicateMessagePolicy(MAX_TRACKED_MESSAGES)

    @JvmStatic
    fun checkAndMark(messageId: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        return policy.checkAndMark(KmpDuplicateMessagePolicy.LEGACY_SCOPE, messageId, nowMs)
    }

    @JvmStatic
    fun checkAndMark(
        scope: String?,
        messageId: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = policy.checkAndMark(scope, messageId, nowMs)

    @JvmStatic
    fun clearAllForTests() {
        policy.clearAll()
    }

    internal fun trackedMessageCount(): Int = policy.trackedMessageCount()
}
