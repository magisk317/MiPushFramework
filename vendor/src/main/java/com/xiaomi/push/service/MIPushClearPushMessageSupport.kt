package com.xiaomi.push.service

/**
 * Pure wire-parsing port of the stock 7.5.29 m0 dispatcher's clear_push_message
 * extras parsing and matcher selection (wc.d/wc.b/wc.c/wc.e). Both the vendor
 * consume path (ack fields) and the product layer (which clear to run) share
 * this single parse. Deliberately Android-free so plain JUnit can exercise it.
 */
object MIPushClearPushMessageSupport {
    /** Stock default when the notifyId extra is absent or non-numeric (s.d(-2, str)). */
    const val DEFAULT_NOTIFY_ID = -2

    /** Stock m0 reads the message id from this extra key; PushConstants has no pinned entry. */
    const val EXTRA_MSG_ID = "msg_id"

    /**
     * Mirrors the stock wc.a.b() cancelType codes: wc.c=1, wc.b=2, wc.e=3, wc.d=4.
     */
    enum class MatcherKind(val cancelType: Int) {
        /** Stock wc.c(notifyId, pkg). */
        NOTIFY_ID(1),

        /** Stock wc.b(msgId). */
        MSG_ID(2),

        /** Stock wc.e(description, title). */
        TITLE_DESCRIPTION(3),

        /** Stock wc.d(notifyId, msgId, pkg). */
        NOTIFY_ID_AND_MSG_ID(4),
    }

    data class ClearMatcher(
        val kind: MatcherKind,
        val notifyId: Int,
        val msgId: String?,
        val title: String?,
        val description: String?,
    )

    @JvmStatic
    fun parseNotifyId(value: String?): Int = value?.toIntOrNull() ?: DEFAULT_NOTIFY_ID

    /**
     * Stock m0 selector order: (notifyId >= 0 && msg_id) -> wc.d; msg_id -> wc.b;
     * notifyId >= 0 -> wc.c; else title+description -> wc.e; otherwise no matcher
     * is built and the dispatcher acks with errorCode=-1.
     */
    @JvmStatic
    fun resolveMatcher(extra: Map<String, String>?): ClearMatcher? {
        extra ?: return null
        val notifyId = parseNotifyId(extra[PushConstants.PUSH_NOTIFY_ID])
        val msgId = extra[EXTRA_MSG_ID]?.takeIf { it.isNotEmpty() }
        val title = extra[PushConstants.PUSH_TITLE]?.takeIf { it.isNotEmpty() }
        val description = extra[PushConstants.PUSH_DESCRIPTION]?.takeIf { it.isNotEmpty() }
        return when {
            notifyId >= 0 && msgId != null ->
                ClearMatcher(MatcherKind.NOTIFY_ID_AND_MSG_ID, notifyId, msgId, title, description)
            msgId != null ->
                ClearMatcher(MatcherKind.MSG_ID, notifyId, msgId, title, description)
            notifyId >= 0 ->
                ClearMatcher(MatcherKind.NOTIFY_ID, notifyId, msgId, title, description)
            title != null && description != null ->
                ClearMatcher(MatcherKind.TITLE_DESCRIPTION, notifyId, msgId, title, description)
            else -> null
        }
    }
}
