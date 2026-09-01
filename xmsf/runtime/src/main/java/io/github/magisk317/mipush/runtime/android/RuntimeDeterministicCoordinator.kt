package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.core.PushChannelRecord

/** Pure runtime key/reason projections; it owns no mutable state and takes no lock. */
internal object RuntimeDeterministicCoordinator {
    fun packageScope(packageName: String): String = packageScope(packageName, currentUserId())

    fun packageScope(packageName: String, androidUserId: Int): String =
        "${requireValidUserId(androidUserId)}:$packageName"

    fun messageScope(packageName: String?, messageId: String, androidUserId: Int): String =
        "${requireValidUserId(androidUserId)}:${packageName.orEmpty()}:$messageId"

    fun messageScope(packageName: String?, messageId: String): String =
        messageScope(packageName, messageId, currentUserId())

    fun actionScope(packageName: String, action: String, androidUserId: Int): String =
        "${requireValidUserId(androidUserId)}:$packageName:$action"

    fun actionScope(packageName: String, action: String): String =
        actionScope(packageName, action, currentUserId())

    fun currentUserId(): Int = runCatching { Utils.myUserId() }
        .getOrNull()
        ?.takeIf { it >= 0 }
        ?: error("Unable to resolve current Android user id")

    fun buildReason(source: String, reason: String?): String =
        if (reason.isNullOrBlank()) source else "$source:$reason"

    fun channelIdentity(record: PushChannelRecord): String =
        channelIdentity(record, record.androidUserId)

    fun channelIdentity(record: PushChannelRecord, androidUserId: Int): String = buildString {
        append(requireValidUserId(androidUserId))
        append(':')
        append(record.channelId)
        append(':')
        append(record.packageName ?: "")
        append(':')
        append(record.userId ?: "")
        append(':')
        append(record.session ?: "")
    }

    private fun requireValidUserId(androidUserId: Int): Int {
        require(androidUserId >= 0) { "Invalid Android user id: $androidUserId" }
        return androidUserId
    }
}
