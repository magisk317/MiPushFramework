package io.github.magisk317.mipush.notification

import android.content.Context
import androidx.core.content.edit
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb

@Suppress("DEPRECATION")
object LegacyNotificationIdentityMigration {
    private const val TAG = "LegacyNotificationIdentityMigration"
    private const val PREFS = "mipush_notification_identity_migration"
    private const val KEY_COMPLETED = "stock_identity_v1_completed"
    private const val LEGACY_TAG_PREFIX = "mipush_"

    fun runOnce(context: Context) {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_COMPLETED, false)) return

        val packages = RegisteredApplicationDb.getList(null)
            .map { it.packageName }
            .filter { it.isNotBlank() }
            .distinct()
        val currentUserId = Utils.requireValidUserId(Utils.myUserId())
        var removed = 0
        var allPackagesInspected = true
        for (packageName in packages) {
            val active = NotificationShellBridge.getActiveNotifications(packageName)
            if (active == null) {
                allPackagesInspected = false
                continue
            }
            for (sbn in active) {
                val notification = sbn ?: continue
                if (!belongsToUser(notification.userId, currentUserId)) continue
                if (!isLegacyIdentity(notification.tag)) continue
                NotificationShellBridge.cancelNotification(
                    packageName,
                    notification.tag,
                    notification.id,
                    notification.userId,
                )
                removed++
            }
        }
        if (!allPackagesInspected) {
            Logger.withTag(TAG).w { "legacy notification identity migration deferred after an active-notification query failed" }
            return
        }
        prefs.edit { putBoolean(KEY_COMPLETED, true) }
        Logger.withTag(TAG).i { "legacy notification identity migration completed packages=${packages.size} removed=$removed" }
    }

    internal fun isLegacyIdentity(tag: String?): Boolean = tag?.startsWith(LEGACY_TAG_PREFIX) == true

    internal fun belongsToUser(notificationUserId: Int, currentUserId: Int): Boolean =
        notificationUserId == currentUserId
}
