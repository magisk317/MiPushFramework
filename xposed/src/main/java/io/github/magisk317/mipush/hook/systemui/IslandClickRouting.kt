package io.github.magisk317.mipush.hook.systemui

import android.content.Context
import android.os.Process
import android.os.UserHandle

/** User-aware helpers for the launcher fallback used by known broken notification click routes. */
internal object IslandClickRouting {
    private const val PER_USER_RANGE = 100_000

    fun requestCode(packageName: String, notificationId: Int, userId: Int): Int {
        var result = packageName.hashCode()
        result = 31 * result + userId.coerceAtLeast(0)
        return 31 * result + notificationId
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
