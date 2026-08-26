package io.github.magisk317.mipush.notification

import android.content.Context
import com.xiaomi.xmpush.thrift.PushMetaInfo

/** Shell-owned channel operations needed by the notification availability policy. */
object NotificationAvailabilityShellBridge {
    @Volatile
    private var findChannel: ((Context, PushMetaInfo, String) -> String?)? = null

    @Volatile
    private var isChannelEnabled: ((String, String) -> Boolean)? = null

    @JvmStatic
    fun install(
        findExistingChannelId: (Context, PushMetaInfo, String) -> String?,
        notificationChannelEnabled: (String, String) -> Boolean,
    ) {
        findChannel = findExistingChannelId
        isChannelEnabled = notificationChannelEnabled
    }

    fun findExistingChannelId(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
    ): String? = findChannel?.invoke(context, metaInfo, packageName)

    fun isNotificationChannelEnabled(packageName: String, channelId: String): Boolean =
        isChannelEnabled?.invoke(packageName, channelId) ?: false
}
