package io.github.magisk317.mipush.hook.systemui

import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.getHookObjectField
import io.github.magisk317.xposed.hookMethod
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog

/**
 * Per-app opt-out from HyperOS's CountLimitCoordinator notification trimming (the "same app can
 * show at most 10 notifications" cap). The coordinator dismisses the oldest entry once a
 * package's notification bucket grows past its limit; the manager's app detail page exposes a
 * per-package exemption that this hook enforces by suppressing coordinator-initiated dismissals.
 *
 * Hook point is `NotifCollection.dismissNotifications` (single-entry `dismissNotification`
 * delegates into it) guarded by a caller-stack check, so user swipes and every other dismissal
 * path stay on stock behavior. Everything fails open: an unmappable entry, a missing class on a
 * future SystemUI refactor, or any hook exception just lets the stock dismissal proceed.
 */
class HookNotificationCountLimit : BaseHook() {
    companion object {
        private const val TAG = "HookNotificationCountLimit"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val NOTIF_COLLECTION_CLASS =
            "com.android.systemui.statusbar.notification.collection.NotifCollection"
        // The eviction victim lookup only walks the direct dispatch path; deeper frames cannot
        // change the caller verdict and the scan must stay cheap on the notification bind path.
        private const val MAX_STACK_SCAN_FRAMES = 32
    }

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        val classLoader = param.classLoader
        val notifCollection = runCatching { classLoader.findClass(NOTIF_COLLECTION_CLASS) }
            .onFailure {
                XLog.d(TAG, "count-limit exemption hook skipped: NotifCollection missing: ${it.message}")
            }
            .getOrNull() ?: return

        notifCollection.hookMethod("dismissNotifications") {
            doBefore {
                val failure = runCatching {
                    val entries = args.firstOrNull() as? List<*> ?: return@doBefore
                    if (entries.isEmpty()) return@doBefore
                    val callerClassNames =
                        Thread.currentThread().stackTrace
                            .take(MAX_STACK_SCAN_FRAMES)
                            .map { it.className }
                    if (!NotificationCountLimitPolicy.isCoordinatorInitiated(callerClassNames)) {
                        return@doBefore
                    }
                    val targets = entries.map { if (it == null) null else resolveEntryTarget(it) }
                    if (targets.any { it == null }) {
                        XLog.d(TAG, "count-limit exemption skipped: unmappable entry in batch")
                        return@doBefore
                    }
                    val settled = targets.filterNotNull()
                    val exempt = NotificationCountLimitPolicy.isAllTargetsExempt(settled) { target ->
                        IslandPreferences.current(target.packageName, target.userId)
                            .notificationCountLimitExempt
                    }
                    if (!exempt) return@doBefore

                    val packages = settled.map { it.packageName }.distinct()
                    XLog.i(TAG, "suppressing count-limit dismissal entries=${settled.size} pkgs=$packages")
                    MagiskOtel.event(
                        name = "systemui.count_limit_bypass",
                        attributes = mapOf(
                            "result" to "ok",
                            "duration_ms" to "0",
                            "process" to "hook",
                            "stage" to "notification_count_limit",
                            "reason" to "exempt_by_app_setting",
                            "target_package" to packages.firstOrNull().orEmpty(),
                        ),
                        statusOk = true,
                    )
                    result = null
                }.exceptionOrNull()
                if (failure != null) {
                    XLog.d(TAG, "count-limit exemption hook failed, keeping stock: ${failure.message}")
                }
            }
        }
    }

    /** Null means the entry could not be resolved; the caller then fails open. */
    @Suppress("DEPRECATION") // StatusBarNotification.userId: the UserHandle identifier is hidden API.
    private fun resolveEntryTarget(entry: Any): CountLimitTarget? =
        runCatching {
            val sbn = getHookObjectField(entry, "mSbn") as? StatusBarNotification
                ?: return@runCatching null
            // USER_ALL (-1) notifications belong to the primary user for display purposes.
            CountLimitTarget(
                packageName = sbn.packageName,
                userId = sbn.userId.takeIf { it >= 0 } ?: 0,
            )
        }.getOrNull()
}
