package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.core.PushChannelRecord

/** Pure runtime key/reason projections; it owns no mutable state and takes no lock. */
internal object RuntimeDeterministicCoordinator {
    fun packageScope(packageName: String): String = packageScope(packageName, currentUserId())

    fun packageScope(packageName: String, androidUserId: Int): String =
        "${androidUserId.coerceAtLeast(0)}:$packageName"

    fun messageScope(packageName: String?, messageId: String): String =
        "${currentUserId()}:${packageName.orEmpty()}:$messageId"

    fun actionScope(packageName: String, action: String): String =
        "${currentUserId()}:$packageName:$action"

    fun currentUserId(): Int = runCatching { Utils.myUserId() }
        .getOrDefault(0)
        .coerceAtLeast(0)

    fun buildReason(source: String, reason: String?): String =
        if (reason.isNullOrBlank()) source else "$source:$reason"

    fun channelIdentity(record: PushChannelRecord): String =
        channelIdentity(record, record.androidUserId)

    fun channelIdentity(record: PushChannelRecord, androidUserId: Int): String = buildString {
        append(androidUserId.coerceAtLeast(0))
        append(':')
        append(record.channelId)
        append(':')
        append(record.packageName ?: "")
        append(':')
        append(record.userId ?: "")
        append(':')
        append(record.session ?: "")
    }
}
