package io.github.magisk317.mipush.notification

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.common.notification.NotificationContentSupport
import io.github.magisk317.mipush.runtime.PushRuntime
import com.xiaomi.xmpush.thrift.PushMetaInfo

/** Shell-local SystemUI island proxy transport; it retains all Android and vendor integration. */
internal object NotificationIslandProxySupport {
    private const val TAG = "NotificationController"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val ACTION_SHOW_ISLAND = "io.github.magisk317.mipush.action.SHOW_ISLAND"
    private const val ACTION_CANCEL_ISLAND = "io.github.magisk317.mipush.action.CANCEL_ISLAND"
    private const val EXTRA_NOTIFICATION_ID = "notification_id"

    fun quietGeneratedStatusBarNotification(notificationBuilder: NotificationCompat.Builder) {
        notificationBuilder.setDefaults(0)
        notificationBuilder.setOnlyAlertOnce(true)
    }

    fun sendGeneratedProxy(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
        notificationId: Int,
        tag: String?,
        notification: Notification,
        options: MiPushIslandOptions,
        resolveUserId: (Context, String) -> Int,
    ): Boolean {
        if (!NotificationManagerEx.isHooked) return false
        val title = NotificationContentSupport.firstText(
            notification.extras,
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
        ) ?: notification.tickerText?.toString()
            ?: metaInfo.title?.takeIf { it.isNotBlank() }
            ?: metaInfo.description?.takeIf { it.isNotBlank() }
            ?: return false
        val content = NotificationContentSupport.firstText(
            notification.extras,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
        ) ?: metaInfo.description?.takeIf { it.isNotBlank() }
            ?: title
        val appContext = context.applicationContext ?: context
        val userId = resolveUserId(appContext, packageName)
        val proxyId = IslandProxyNotificationId.fromPackage(packageName, notificationId, tag, userId)
        return runCatching {
            appContext.sendBroadcast(
                Intent(ACTION_SHOW_ISLAND).apply {
                    setPackage(SYSTEM_UI_PACKAGE)
                    putExtra("title", title)
                    putExtra("content", content)
                    putExtra(
                        "icon",
                        MiPushIslandPayloadBuilder.resolveNotificationIcon(
                            context = appContext,
                            packageName = packageName,
                            notificationIcon = notification.getLargeIconCompat(),
                            largeIcon = null,
                        ),
                    )
                    putExtra("notificationId", proxyId)
                    putExtra("timeoutSecs", options.timeoutSecs)
                    putExtra("firstFloat", options.firstFloat)
                    putExtra("enableFloat", options.enableFloat)
                    putExtra("showNotification", options.showNotification)
                    putExtra("sourcePackage", packageName)
                    putExtra("userId", userId)
                    putExtra("sourceChannelId", notification.channelId)
                    putExtra("contentIntent", notification.contentIntent)
                    putExtra("isOngoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
                    putExtra("showIslandIcon", true)
                    putExtra("clearBeforePost", true)
                },
            )
        }.fold(
            onSuccess = {
                PushRuntime.observeNotificationEvent(packageName, "notification_island_proxy_posted", "NotificationController.publish")
                Logger.withTag(TAG).d { "posted island proxy pkg=$packageName id=$notificationId proxyId=$proxyId" }
                true
            },
            onFailure = {
                PushRuntime.observeNotificationEvent(packageName, "notification_island_proxy_failed", "NotificationController.publish")
                Logger.withTag(TAG).w(it) { "island proxy failed pkg=$packageName id=$notificationId: ${it.message}" }
                false
            },
        )
    }

    fun cancelGeneratedProxy(
        context: Context,
        packageName: String,
        notificationId: Int,
        tag: String?,
        resolveUserId: (Context, String) -> Int,
    ) {
        if (!NotificationManagerEx.isHooked) return
        val userId = resolveUserId(context, packageName)
        runCatching {
            (context.applicationContext ?: context).sendBroadcast(
                Intent(ACTION_CANCEL_ISLAND).apply {
                    setPackage(SYSTEM_UI_PACKAGE)
                    putExtra(
                        EXTRA_NOTIFICATION_ID,
                        IslandProxyNotificationId.fromPackage(packageName, notificationId, tag, userId),
                    )
                    putExtra("userId", userId)
                },
            )
        }.onFailure {
            Logger.withTag(TAG).w(it) { "cancel island proxy failed pkg=$packageName id=$notificationId: ${it.message}" }
        }
    }

    private fun Notification.getLargeIconCompat(): Icon? = runCatching { getLargeIcon() }.getOrNull()
}
