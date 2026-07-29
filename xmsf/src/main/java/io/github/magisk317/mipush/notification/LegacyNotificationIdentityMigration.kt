package io.github.magisk317.mipush.notification

import android.content.Context
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb

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
        var removed = 0
        var allPackagesInspected = true
        for (packageName in packages) {
            val active = NotificationManagerEx.getActiveNotifications(packageName)
            if (active == null) {
                allPackagesInspected = false
                continue
            }
            for (sbn in active) {
                val notification = sbn ?: continue
                if (!isLegacyIdentity(notification.tag)) continue
                NotificationManagerEx.cancel(packageName, notification.tag, notification.id)
                removed++
            }
        }
        if (!allPackagesInspected) {
            Napier.w("legacy notification identity migration deferred after an active-notification query failed", tag = TAG)
            return
        }
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
        Napier.i("legacy notification identity migration completed packages=${packages.size} removed=$removed", tag = TAG)
    }

    internal fun isLegacyIdentity(tag: String?): Boolean = tag?.startsWith(LEGACY_TAG_PREFIX) == true
}
