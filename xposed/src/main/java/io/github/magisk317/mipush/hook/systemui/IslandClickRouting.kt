package io.github.magisk317.mipush.hook.systemui

import android.content.Context
import android.os.Process
import android.os.UserHandle
import io.github.magisk317.mipush.common.utils.Utils

/** User-aware helpers for the launcher fallback used by known broken notification click routes. */
internal object IslandClickRouting {
    private const val PER_USER_RANGE = 100_000
    private const val REQUEST_HASH_MULTIPLIER = 31

    fun requestCode(packageName: String, notificationId: Int, userId: Int): Int {
        var result = packageName.hashCode()
        result = REQUEST_HASH_MULTIPLIER * result + Utils.requireValidUserId(userId)
        return REQUEST_HASH_MULTIPLIER * result + notificationId
    }

    fun contextForUser(context: Context, userId: Int): Context? {
        val normalizedUserId = userId.takeIf { it >= 0 } ?: return null
        if (normalizedUserId == 0) return context
        return runCatching {
            val userHandle = UserHandle.getUserHandleForUid(
                normalizedUserId * PER_USER_RANGE + Process.FIRST_APPLICATION_UID,
            )
            val method = context.javaClass.getMethod(
                "createContextAsUser",
                UserHandle::class.java,
                Int::class.javaPrimitiveType,
            )
            method.invoke(context, userHandle, 0) as? Context
        }.getOrNull()
    }
}
