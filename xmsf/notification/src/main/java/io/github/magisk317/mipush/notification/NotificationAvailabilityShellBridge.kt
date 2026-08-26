package io.github.magisk317.mipush.notification

import android.content.Context
import com.xiaomi.xmpush.thrift.PushMetaInfo

/** Shell-owned channel operations needed by the notification availability policy. */
object NotificationAvailabilityShellBridge {
    @Volatile
    private var findChannel: ((Context, PushMetaInfo, String) -> String?)? = null

    @Volatile
    private var isChannelEnabled: ((String, String) -> Boolean)? = null

    @Volatile
    private var resolveChannel: ((Context, PushMetaInfo, String) -> String)? = null

    @JvmStatic
    fun install(
        findExistingChannelId: (Context, PushMetaInfo, String) -> String?,
        notificationChannelEnabled: (String, String) -> Boolean,
        resolveChannelId: (Context, PushMetaInfo, String) -> String,
    ) {
        findChannel = findExistingChannelId
        isChannelEnabled = notificationChannelEnabled
        resolveChannel = resolveChannelId
    }

    fun findExistingChannelId(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
    ): String? = findChannel?.invoke(context, metaInfo, packageName)

    fun resolveChannelId(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
    ): String = resolveChannel?.invoke(context, metaInfo, packageName).orEmpty()

    fun isNotificationChannelEnabled(packageName: String, channelId: String): Boolean =
        isChannelEnabled?.invoke(packageName, channelId) ?: false
}
