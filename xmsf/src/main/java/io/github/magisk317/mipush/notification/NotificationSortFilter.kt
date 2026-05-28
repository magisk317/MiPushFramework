package io.github.magisk317.mipush.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo

/**
 * Filters notifications based on MIUI focus parameters.
 *
 * Based on stock xmsf 7.4.67 `com.xiaomi.push.sort` package.
 * Handles `miui.focus.param` JSON in notification extras:
 * - legacy root `updatable` / `reopen`
 * - HyperIsland ToolKit `param_v2.updatable` / `param_v2.reopen`
 * - Deleted focus notifications are cached and filtered for 24h.
 *
 * Delegates pure cache logic to [FocusNotificationCache] in `:core`.
 * Keeps Android-specific code (SharedPreferences persistence, PendingIntent wiring,
 * BroadcastReceiver) in this module.
 */
object NotificationSortFilter {

    private const val TAG = "NotificationSortFilter"
    private const val PREFS_NAME = "mipush_focus_deleted_notifications"
    private const val EXTRA_PACKAGE_NAME = "package_name"
    private const val EXTRA_NOTIFICATION_ID = "notification_id"
    private const val ACTION_FOCUS_DELETED = "io.github.magisk317.mipush.notification.FOCUS_DELETED"

    private var storeInstalled = false

    /**
     * Typealias bridging the core [FocusNotificationCache.DeletedFocusStore] interface
     * so that existing test code referencing `NotificationSortFilter.DeletedFocusStore` still compiles.
     */
    interface DeletedFocusStore : FocusNotificationCache.DeletedFocusStore

    /**
     * Check if this notification should be filtered out based on focus parameters.
     * Returns true if the notification should be dropped.
     */
    @JvmStatic
    fun shouldFilter(
        metaInfo: PushMetaInfo?,
        packageName: String,
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = shouldFilter(null, metaInfo, packageName, notificationId, nowMs)

    @JvmStatic
    fun shouldFilter(
        context: Context?,
        metaInfo: PushMetaInfo?,
        packageName: String,
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val focusParam = metaInfo?.extra?.get("miui.focus.param")
        return shouldFilter(context, focusParam, packageName, notificationId, nowMs)
    }

    @JvmStatic
    fun shouldFilter(
        context: Context?,
        focusParam: String?,
        packageName: String,
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        ensureStoreInstalled(context)
        return FocusNotificationCache.shouldFilter(focusParam, packageName, notificationId, nowMs)
    }

    /**
     * Record that a focus notification has been deleted/removed.
     * Should be called when a notification with focus parameters is cancelled.
     */
    @JvmStatic
    fun onFocusDeleted(packageName: String, notificationId: Int, nowMs: Long = System.currentTimeMillis()) {
        onFocusDeleted(null, packageName, notificationId, nowMs)
    }

    @JvmStatic
    fun onFocusDeleted(
        context: Context?,
        packageName: String,
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis()
    ) {
        ensureStoreInstalled(context)
        FocusNotificationCache.onFocusDeleted(packageName, notificationId, nowMs)
    }

    @JvmStatic
    fun attachDeleteIntentIfNeeded(
        context: Context,
        builder: NotificationCompat.Builder,
        packageName: String,
        focusParam: String?,
        notificationId: Int
    ) {
        val focus = FocusNotificationCache.parseFocusParam(focusParam) ?: return
        if (!focus.updatable) return

        val key = FocusNotificationCache.cacheKey(packageName, notificationId)
        val intent = Intent(context, NotificationFocusDeleteReceiver::class.java).apply {
            action = ACTION_FOCUS_DELETED
            data = Uri.parse("mipush-focus-delete://$key")
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        builder.setDeleteIntent(
            PendingIntent.getBroadcast(
                context,
                key.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    internal fun handleDeleteIntent(context: Context?, intent: Intent?, nowMs: Long = System.currentTimeMillis()) {
        if (intent?.action != ACTION_FOCUS_DELETED) return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId == Int.MIN_VALUE) return
        onFocusDeleted(context, packageName, notificationId, nowMs)
    }

    internal fun resetForTest() {
        FocusNotificationCache.resetForTest()
        storeInstalled = false
    }

    internal fun clearMemoryCacheForTest() {
        FocusNotificationCache.clearMemoryCacheForTest()
    }

    internal fun installPersistentStoreForTest(store: FocusNotificationCache.DeletedFocusStore) {
        FocusNotificationCache.installPersistentStoreForTest(store)
        storeInstalled = true
    }

    internal fun parseFocusParamForTest(focusParam: String?): Pair<Boolean, String>? {
        return FocusNotificationCache.parseFocusParam(focusParam)?.let { it.updatable to it.reopen }
    }

    private fun ensureStoreInstalled(context: Context?) {
        if (storeInstalled) return
        if (context != null) {
            FocusNotificationCache.installPersistentStore(
                SharedPreferencesDeletedFocusStore(context.applicationContext ?: context)
            )
            storeInstalled = true
        }
    }

    /**
     * Android SharedPreferences-backed implementation of [FocusNotificationCache.DeletedFocusStore].
     */
    private class SharedPreferencesDeletedFocusStore(context: Context) : FocusNotificationCache.DeletedFocusStore {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun readAll(): Map<String, Long> {
            return prefs.all.mapNotNull { (key, value) ->
                val expiresAtMs = when (value) {
                    is Long -> value
                    is Number -> value.toLong()
                    is String -> value.toLongOrNull()
                    else -> null
                } ?: return@mapNotNull null
                key to expiresAtMs
            }.toMap()
        }

        override fun put(key: String, expiresAtMs: Long) {
            prefs.edit().putLong(key, expiresAtMs).commit()
        }

        override fun remove(key: String) {
            prefs.edit().remove(key).commit()
        }

        override fun clear() {
            prefs.edit().clear().commit()
        }
    }
}

class NotificationFocusDeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationSortFilter.handleDeleteIntent(context, intent)
    }
}
