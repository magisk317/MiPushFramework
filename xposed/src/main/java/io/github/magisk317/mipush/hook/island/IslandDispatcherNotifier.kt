package io.github.magisk317.mipush.hook.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Process
import android.os.UserHandle
import io.github.magisk317.mipush.common.NotificationClassifier
import io.github.magisk317.mipush.hook.XLog
import java.util.concurrent.TimeUnit
import io.github.magisk317.xposed.logging.MagiskOtel

internal object IslandDispatcherNotifier {
    private const val TAG = "IslandDispatcherNotifier"
    private const val GROUP_KEY_PREFIX = "mipush_island"
    private const val DEFAULT_AUTO_CANCEL_SECS = 5
    private const val AUTO_CANCEL_GRACE_MS = 1_000L
    private const val PER_USER_RANGE = 100_000L

    fun post(context: Context, request: IslandRequest): Boolean {
        return runCatching {
            val notificationContext = contextForUser(context, request.userId)
                ?: error("notification user context unavailable user=${request.userId}")
            ensureChannel(notificationContext)
            val groupKey = request.sourcePackage?.takeIf { it.isNotBlank() }
                ?.let { "$GROUP_KEY_PREFIX:$it" }
            val notification = Notification.Builder(notificationContext, IslandDispatchContract.CHANNEL_ID)
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

            notification.extras.putAll(request.toIslandExtras(notificationContext))

            val manager = notificationContext.getSystemService(NotificationManager::class.java)
                ?: error("notification manager unavailable user=${request.userId}")
            if (request.clearBeforePost) {
                manager.cancel(request.notificationId)
            }
            manager.notify(request.notificationId, notification)
            MagiskOtel.event(
                name = "push.island",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "post",
                    "target_package" to (request.sourcePackage.orEmpty()),
                ),
                statusOk = true,
            )
            true
        }.onFailure {
            XLog.e(TAG, "post failed: ${it.message}", it)
            MagiskOtel.event(
                name = "push.island",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "post",
                    "reason" to it.javaClass.simpleName,
                    "target_package" to (request.sourcePackage.orEmpty()),
                ),
                statusOk = false,
            )
        }.getOrDefault(false)
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

    private fun contextForUser(context: Context, userId: Int): Context? {
        if (userId < 0) return null
        val normalizedUserId = userId
        val currentUserId = Process.myUid().toLong().div(PER_USER_RANGE).toInt().coerceAtLeast(0)
        if (normalizedUserId == currentUserId) return context
        return runCatching {
            val uid = normalizedUserId.toLong() * PER_USER_RANGE + Process.FIRST_APPLICATION_UID
            val userHandle = UserHandle.getUserHandleForUid(uid.toInt())
            val method = context.javaClass.getMethod(
                "createContextAsUser",
                UserHandle::class.java,
                Int::class.javaPrimitiveType,
            )
            method.invoke(context, userHandle, 0) as? Context
        }.getOrNull().also { resolved ->
            if (resolved == null) {
                XLog.w(TAG, "user context unavailable; refusing notification user=$normalizedUserId")
            }
        }
    }

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
            userId = userId,
            sourceChannelId = sourceChannelId,
            actions = actions,
            showIslandIcon = showIslandIcon,
            highlightColor = highlightColor,
            islandOuterGlow = islandOuterGlow,
            style = resolvedStyle,
            smallOnly = smallOnly,
        )
    }

    fun cancel(context: Context, notificationId: Int, userId: Int): Boolean {
        return runCatching {
            val notificationContext = contextForUser(context, userId)
                ?: error("notification user context unavailable user=$userId")
            val manager = notificationContext.getSystemService(NotificationManager::class.java)
                ?: error("notification manager unavailable user=$userId")
            manager.cancel(notificationId)
            MagiskOtel.event(
                name = "push.island",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "cancel",
                ),
                statusOk = true,
            )
            true
        }.onFailure {
            XLog.e(TAG, "cancel failed: ${it.message}", it)
            MagiskOtel.event(
                name = "push.island",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "cancel",
                    "reason" to it.javaClass.simpleName,
                ),
                statusOk = false,
            )
        }.getOrDefault(false)
    }

    fun ensureChannel(context: Context) {
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
