package io.github.magisk317.mipush.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.xiaomi.push.service.NotificationManagerHelper
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Replacing this package cancels every PendingIntent the previous incarnation created, but the
 * notifications it posted stay in the shade. Their contentIntent can only fail with
 * `sendAndReturnResult -96`, so cancel the stale records instead of leaving dead clicks behind.
 *
 * The stock revival only republishes XMSF-owned notifications; target-attributed posts made
 * through notifyAsPackage are invisible to it and are the ones users actually click.
 */
import io.github.magisk317.mipush.common.logging.StableLogTag

@StableLogTag
object StaleTargetNotificationCleanup {
    private const val PREFS = "mipush_self_update"
    private const val KEY_LAST_INSTALL_UPDATE_TIME = "last_install_update_time"

    // Posting requires a received message, so the receive-time store is the widest durable proof
    // that XMSF handled a package; the registration stores backfill entries that predate it.
    private val PACKAGE_SOURCES = listOf(
        "last_receive_time",
        "pref_registered_pkg_names",
        "pref_registered_pkg_names_sec",
    )

    fun runIfSelfUpdated(context: Context) {
        val app = context.applicationContext
        val installUpdateTime = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        if (installUpdateTime <= 0L) {
            logW("self-update cleanup skipped: package update time unavailable")
            return
        }
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_LAST_INSTALL_UPDATE_TIME, 0L) == installUpdateTime) return
        val currentUserId = Utils.myUserId()
        if (currentUserId < 0) {
            logW("self-update cleanup skipped: current user unresolved")
            return
        }
        runCatching {
            val targets = cancelStaleTargetNotifications(app, installUpdateTime, currentUserId)
            val local = cancelStaleLocalNotifications(app, installUpdateTime, currentUserId)
            Triple(targets.first, targets.second, local)
        }.onSuccess { (cancelledTargets, scanned, cancelledLocal) ->
            prefs.edit().putLong(KEY_LAST_INSTALL_UPDATE_TIME, installUpdateTime).apply()
            logI(
                "self-update cleanup cancelled targets=$cancelledTargets scanned=$scanned " +
                    "local=$cancelledLocal updateTime=$installUpdateTime",
            )
            MagiskOtel.event(
                name = "notification.self_update_cleanup",
                attributes = mapOf(
                    "result" to "ok",
                    "targets_cancelled" to cancelledTargets.toString(),
                    "scanned_packages" to scanned.toString(),
                    "local_cancelled" to cancelledLocal.toString(),
                ),
            )
        }.onFailure { error ->
            logW("self-update cleanup failed: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    @Suppress("DEPRECATION") // StatusBarNotification.userId is the only user accessor the compile SDK exposes.
    private fun cancelStaleTargetNotifications(
        app: Context,
        installUpdateTime: Long,
        currentUserId: Int,
    ): Pair<Int, Int> {
        val packages = knownTargetPackages(app)
        var cancelled = 0
        for (packageName in packages) {
            val helper = NotificationManagerHelper.from(app, packageName)
            for (sbn in helper.getManagedActiveNotifications()) {
                val notification = sbn.notification ?: continue
                if (!StaleTargetNotificationPolicy.shouldCancel(
                        postTime = sbn.postTime,
                        ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                        userId = sbn.userId,
                        currentUserId = currentUserId,
                        installUpdateTime = installUpdateTime,
                    )
                ) {
                    continue
                }
                NotificationManagerEx.cancel(packageName, sbn.tag, sbn.id, sbn.userId)
                cancelled += 1
            }
        }
        return cancelled to packages.size
    }

    private fun knownTargetPackages(app: Context): Set<String> {
        val packages = LinkedHashSet<String>()
        for (source in PACKAGE_SOURCES) {
            packages += app.getSharedPreferences(source, Context.MODE_PRIVATE).all.keys
        }
        packages.remove(app.packageName)
        return packages
    }

    @Suppress("DEPRECATION") // StatusBarNotification.userId is the only user accessor the compile SDK exposes.
    private fun cancelStaleLocalNotifications(
        app: Context,
        installUpdateTime: Long,
        currentUserId: Int,
    ): Int {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return 0
        var cancelled = 0
        for (sbn in manager.activeNotifications) {
            if (sbn.packageName != app.packageName) continue
            val notification = sbn.notification ?: continue
            if (!StaleTargetNotificationPolicy.shouldCancel(
                    postTime = sbn.postTime,
                    ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                    userId = sbn.userId,
                    currentUserId = currentUserId,
                    installUpdateTime = installUpdateTime,
                )
            ) {
                continue
            }
            NotificationManagerEx.cancel(app.packageName, sbn.tag, sbn.id, sbn.userId)
            cancelled += 1
        }
        return cancelled
    }
}

internal object StaleTargetNotificationPolicy {
    fun shouldCancel(
        postTime: Long,
        ongoing: Boolean,
        userId: Int,
        currentUserId: Int,
        installUpdateTime: Long,
    ): Boolean {
        if (installUpdateTime <= 0L || ongoing) return false
        if (userId != currentUserId) return false
        return postTime in 1 until installUpdateTime
    }
}
