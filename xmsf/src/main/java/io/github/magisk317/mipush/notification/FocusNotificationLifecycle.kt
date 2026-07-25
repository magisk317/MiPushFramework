package io.github.magisk317.mipush.notification

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper

/**
 * Single exit path for MIUI focus / Live Update notifications.
 *
 * HyperOS AOD keeps rendering focus cards while [FocusNotificationRegistry] still lists the key in
 * Settings.Secure `updatable_focus_notifs`, even after the shade row and island chip are gone.
 * DeleteIntent previously only recorded [NotificationSortFilter.onFocusDeleted] (blocks reopen for
 * 24h) and never unregistered that secure list — matching "island/shade gone, AOD stuck for hours,
 * no re-post".
 */
object FocusNotificationLifecycle {
    private const val TAG = "FocusNotificationLifecycle"

    @JvmStatic
    fun focusKey(context: Context, packageName: String, notificationId: Int, tag: String?): String {
        val uid = resolveUid(context, packageName)
        return "0|$packageName|$notificationId|$tag|$uid"
    }

    @JvmStatic
    fun end(
        context: Context,
        packageName: String,
        notificationId: Int,
        tag: String? = MyMIPushNotificationHelper.getNotificationTag(packageName),
        cancelNotification: Boolean = true,
        recordDeleted: Boolean = true,
        unregisterFocus: Boolean = true,
    ) {
        val appContext = context.applicationContext ?: context
        val resolvedTag = tag ?: MyMIPushNotificationHelper.getNotificationTag(packageName)
        if (cancelNotification) {
            runCatching {
                NotificationManagerEx.cancel(packageName, resolvedTag, notificationId)
            }.onFailure {
                Napier.w("focus end target cancel failed pkg=$packageName id=$notificationId: ${it.message}", it, tag = TAG)
            }
            runCatching {
                appContext.getSystemService(android.app.NotificationManager::class.java)
                    ?.cancel(resolvedTag, notificationId)
            }.onFailure {
                Napier.w("focus end local cancel failed pkg=$packageName id=$notificationId: ${it.message}", it, tag = TAG)
            }
        }
        if (unregisterFocus) {
            val key = focusKey(appContext, packageName, notificationId, resolvedTag)
            val ok = FocusNotificationRegistry.unregisterAllUidVariants(appContext, key)
            Napier.d(
                "focus end unregister key=$key ok=$ok pkg=$packageName id=$notificationId tag=$resolvedTag",
                tag = TAG,
            )
        }
        if (recordDeleted) {
            NotificationSortFilter.onFocusDeleted(appContext, packageName, notificationId)
        }
    }

    private fun resolveUid(context: Context, packageName: String): Int {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                ).uid
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0).uid
            }
        }.getOrElse {
            if (packageName == context.packageName) android.os.Process.myUid() else 0
        }
    }
}
