package io.github.magisk317.mipush.hook.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import io.github.magisk317.mipush.common.NotificationClassifier
import io.github.magisk317.mipush.hook.XLog

internal object IslandDispatcherNotifier {
    private const val TAG = "IslandDispatcherNotifier"

    fun post(context: Context, request: IslandRequest) {
        runCatching {
            ensureChannel(context)
            val notification = Notification.Builder(context, IslandDispatchContract.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(request.title)
                .setContentText(request.content)
                .setAutoCancel(!request.isOngoing)
                .setOngoing(request.isOngoing)
                .setVisibility(
                    if (request.showNotification) {
                        Notification.VISIBILITY_PRIVATE
                    } else {
                        Notification.VISIBILITY_SECRET
                    },
                )
                .apply {
                    request.contentIntent?.let { setContentIntent(it) }
                }
                .build()

            notification.extras.putAll(request.toIslandExtras(context))

            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (request.clearBeforePost) {
                manager.cancel(request.notificationId)
            }
            manager.notify(request.notificationId, notification)
        }.onFailure {
            XLog.e(TAG, "post failed: ${it.message}", it)
        }
    }

    private fun IslandRequest.toIslandExtras(context: Context): android.os.Bundle {
        val style = NotificationClassifier.classify(title, content, sourcePackage)
        return IslandPayloadBuilder.buildExtras(
            context = context,
            title = title,
            content = content,
            icon = icon,
            timeoutSecs = timeoutSecs,
            firstFloat = firstFloat,
            enableFloat = enableFloat,
            showNotification = showNotification,
            sourcePackage = sourcePackage,
            sourceChannelId = sourceChannelId,
            actions = actions,
            showIslandIcon = showIslandIcon,
            highlightColor = highlightColor,
            islandOuterGlow = islandOuterGlow,
            style = style,
        )
    }

    fun cancel(context: Context, notificationId: Int) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        }.onFailure {
            XLog.e(TAG, "cancel failed: ${it.message}", it)
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(IslandDispatchContract.CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                IslandDispatchContract.CHANNEL_ID,
                IslandDispatchContract.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }
}
