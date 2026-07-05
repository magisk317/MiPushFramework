package io.github.magisk317.mipush.hook.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import io.github.magisk317.mipush.common.NotificationClassifier
import io.github.magisk317.mipush.hook.XLog
import java.util.concurrent.TimeUnit

internal object IslandDispatcherNotifier {
    private const val TAG = "IslandDispatcherNotifier"
    private const val GROUP_KEY_PREFIX = "mipush_island"
    private const val DEFAULT_AUTO_CANCEL_SECS = 5
    private const val AUTO_CANCEL_GRACE_MS = 1_000L

    fun post(context: Context, request: IslandRequest) {
        runCatching {
            ensureChannel(context)
            val groupKey = request.sourcePackage?.takeIf { it.isNotBlank() }
                ?.let { "$GROUP_KEY_PREFIX:$it" }
            val notification = Notification.Builder(context, IslandDispatchContract.CHANNEL_ID)
                .setSmallIcon(request.icon ?: fallbackSmallIcon())
                .setContentTitle(request.title)
                .setContentText(request.content)
                .setAutoCancel(!request.isOngoing)
                .setOngoing(request.isOngoing)
                .setGroup(groupKey)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setTimeoutAfter(autoCancelAfterMillis(request.timeoutSecs))
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

    /**
     * Fallback status-bar small icon when the dispatch request carries no icon.
     *
     * The framework drawable id must be created with an explicit `"android"` resPackage. Using
     * `Icon.createWithResource(context, ...)` here would bind the framework id (0x0108____) to the
     * posting package (com.android.systemui), producing a malformed id/package pair that SystemUI
     * cannot resolve — the same defect that rendered the exclamation-mark / Android-robot glyphs.
     */
    private fun fallbackSmallIcon(): Icon =
        Icon.createWithResource("android", android.R.drawable.sym_def_app_icon)

    internal fun autoCancelAfterMillis(timeoutSecs: Int): Long {
        val displaySecs = timeoutSecs.takeIf { it > 0 } ?: DEFAULT_AUTO_CANCEL_SECS
        return TimeUnit.SECONDS.toMillis(displaySecs.toLong()) + AUTO_CANCEL_GRACE_MS
    }

    private fun IslandRequest.toIslandExtras(context: Context): android.os.Bundle {
        val resolvedStyle = style ?: NotificationClassifier.classify(title, content, sourcePackage)
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
            style = resolvedStyle,
            smallOnly = smallOnly,
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
