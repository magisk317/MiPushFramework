package io.github.magisk317.mipush.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb

data class NotificationPostResult(
    val posted: Boolean,
    val owner: NotificationPostOwner,
    val reason: String,
)

enum class NotificationPostOwner {
    NONE,
    TARGET,
    LOCAL_XMSF,
}

/**
 * Narrow callbacks owned by the installable XMSF shell.
 *
 * Notification policy and preference readers remain independently compilable; the shell only
 * supplies operations that require its Android/stock notification surface.
 */
object NotificationShellBridge {
    @Volatile
    private var statusBarRefresh: (() -> Unit)? = null

    @JvmStatic
    fun installStatusBarRefresh(callback: () -> Unit) {
        statusBarRefresh = callback
    }

    @Volatile
    private var notificationTag: ((String) -> String?)? = null

    @Volatile
    private var activeNotifications: ((String) -> Array<StatusBarNotification?>?)? = null

    @Volatile
    private var cancelNotification: ((String, String?, Int, Int) -> Unit)? = null

    @Volatile
    private var postDetailed: ((String, String?, Int, Notification, Int) -> NotificationPostResult)? = null

    @Volatile
    private var notify: ((String, String?, Int, Notification, Int) -> Boolean)? = null

    @JvmStatic
    fun installPublishOperations(
        postDetailed: (String, String?, Int, Notification, Int) -> NotificationPostResult,
        notify: (String, String?, Int, Notification, Int) -> Boolean,
    ) {
        this.postDetailed = postDetailed
        this.notify = notify
    }

    fun postDetailed(
        packageName: String,
        tag: String?,
        id: Int,
        notification: Notification,
        userId: Int,
    ): NotificationPostResult {
        if (shouldSuppressForInactiveTarget(packageName)) {
            return NotificationPostResult(false, NotificationPostOwner.NONE, "target_app_inactive")
        }
        return postDetailed?.invoke(packageName, tag, id, notification, userId)
            ?: NotificationPostResult(false, NotificationPostOwner.NONE, "bridge_uninstalled")
    }

    fun notify(
        packageName: String,
        tag: String?,
        id: Int,
        notification: Notification,
        userId: Int,
    ): Boolean {
        if (shouldSuppressForInactiveTarget(packageName)) {
            return false
        }
        return notify?.invoke(packageName, tag, id, notification, userId) ?: false
    }

    /**
     * Per-app opt-in gate (app detail page): while the target package is disabled or suspended,
     * no module-initiated post for it goes through — this covers the main publish funnel plus the
     * coordinator-driven reposts (top-notification jobs, sweet restore) that bypass dispatch
     * gating. Fails open so a store/PM problem never blocks an allowed notification.
     */
    private fun shouldSuppressForInactiveTarget(packageName: String): Boolean =
        runCatching { RegisteredApplicationDb.shouldSuppressForInactiveApp(packageName) }
            .getOrDefault(false)


    @JvmStatic
    fun installNotificationOperations(
        getNotificationTag: (String) -> String?,
        getActiveNotifications: (String) -> Array<StatusBarNotification?>?,
        cancel: (String, String?, Int, Int) -> Unit,
    ) {
        notificationTag = getNotificationTag
        activeNotifications = getActiveNotifications
        cancelNotification = cancel
    }

    fun getNotificationTag(packageName: String): String? = notificationTag?.invoke(packageName)

    fun getActiveNotifications(packageName: String): Array<StatusBarNotification?>? =
        activeNotifications?.invoke(packageName)

    fun cancelNotification(packageName: String, tag: String?, id: Int, userId: Int) {
        cancelNotification?.invoke(packageName, tag, id, userId)
    }


    @JvmStatic
    fun triggerStatusBarRefresh() {
        statusBarRefresh?.invoke()
    }
}
