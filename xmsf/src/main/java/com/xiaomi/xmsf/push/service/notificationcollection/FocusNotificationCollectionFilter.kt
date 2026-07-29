package com.xiaomi.xmsf.push.service.notificationcollection

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import com.xiaomi.channel.commonutils.android.SystemProperties
import io.github.aakira.napier.Napier
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Stock notification-collection filter for focus notifications.
 *
 * Stock 7.4.67-C keeps this behavior in `com.xiaomi.push.sort.b/c/d` and invokes it from
 * `notificationcollection.local.b`; it is not part of the `t0` notification publish chain. The
 * older MiPushFramework implementation ran before publishing, keyed entries as `package:id`, and
 * persisted one 24-hour policy for every device. That changed user-visible delivery, so this
 * replacement is intentionally collection-only, uses [StatusBarNotification.getKey], and keeps
 * process-local state exactly as the stock implementation does.
 */
internal class FocusNotificationCollectionFilter(
    private val policy: Policy,
    private val timeoutScheduler: TimeoutScheduler = TimeoutScheduler.NONE,
) {
    internal enum class Policy {
        DISABLED,
        HYPER_OS_1,
        HYPER_OS_2,
    }

    internal interface TimeoutScheduler {
        fun schedule(key: String)
        fun cancel(key: String)

        companion object {
            val NONE = object : TimeoutScheduler {
                override fun schedule(key: String) = Unit
                override fun cancel(key: String) = Unit
            }
        }
    }

    private val deletedKeys = object : LinkedHashMap<String, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean {
            val shouldRemove = size > MAX_CACHE_SIZE
            if (shouldRemove && policy == Policy.HYPER_OS_2 && eldest != null) {
                timeoutScheduler.cancel(eldest.key)
            }
            return shouldRemove
        }
    }

    /**
     * Updates deleted-focus state for a post, then returns whether local collection should skip it.
     */
    fun onNotificationPosted(sbn: StatusBarNotification): Boolean {
        if (policy == Policy.DISABLED) return false
        val key = sbn.key?.takeIf(String::isNotEmpty) ?: return false
        synchronized(deletedKeys) {
            if (deletedKeys.containsKey(key) && matchesFocusMode(sbn.notification, MODE_REOPEN)) {
                deletedKeys.remove(key)
                if (policy == Policy.HYPER_OS_2) {
                    timeoutScheduler.cancel(key)
                }
            }
            return matchesFocusMode(sbn.notification, MODE_CLOSE) && deletedKeys.containsKey(key)
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification, reason: Int) {
        if (policy == Policy.DISABLED) return
        val key = sbn.key?.takeIf(String::isNotEmpty) ?: return
        synchronized(deletedKeys) {
            when (policy) {
                Policy.DISABLED -> Unit
                Policy.HYPER_OS_1 -> handleHyperOs1Removal(sbn.packageName, key, reason)
                Policy.HYPER_OS_2 -> handleHyperOs2Removal(sbn.packageName, key, reason)
            }
        }
    }

    fun onTimeout(key: String) {
        if (policy != Policy.HYPER_OS_2 || key.isEmpty()) return
        synchronized(deletedKeys) {
            deletedKeys.remove(key)
            timeoutScheduler.cancel(key)
        }
    }

    internal fun containsKey(key: String): Boolean = synchronized(deletedKeys) {
        deletedKeys.containsKey(key)
    }

    internal fun size(): Int = synchronized(deletedKeys) { deletedKeys.size }

    private fun handleHyperOs1Removal(packageName: String, key: String, reason: Int) {
        // Stock 7.4.67-C sort.c records reason 2 only for this allowlist and has no TTL.
        // The old shared 24-hour policy expired HyperOS 1 state that stock keeps for the process.
        if (reason == REASON_USER_DISMISS) {
            if (packageName in HYPER_OS_1_ALLOWLIST) {
                deletedKeys[key] = Unit
            }
        } else {
            deletedKeys.remove(key)
        }
    }

    private fun handleHyperOs2Removal(packageName: String, key: String, reason: Int) {
        // DEX-verified stock 7.4.67-C sort.d.a: reason 12 leaves state/alarm untouched; reason 2
        // records all but six system packages; every other reason clears both. The previous
        // deleteIntent path could not observe these system removal reasons and therefore diverged.
        when {
            reason == REASON_GROUP_SUMMARY_CANCELED -> Unit
            reason == REASON_USER_DISMISS -> {
                if (packageName !in HYPER_OS_2_EXCLUSIONS && !deletedKeys.containsKey(key)) {
                    deletedKeys[key] = Unit
                    timeoutScheduler.schedule(key)
                }
            }
            else -> {
                deletedKeys.remove(key)
                timeoutScheduler.cancel(key)
            }
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 50
        private const val FOCUS_PARAM = "miui.focus.param"
        private const val MODE_CLOSE = "close"
        private const val MODE_REOPEN = "reopen"
        private const val REASON_USER_DISMISS = NotificationListenerService.REASON_CANCEL
        private const val REASON_GROUP_SUMMARY_CANCELED =
            NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED

        private val HYPER_OS_1_ALLOWLIST = setOf(
            "com.autonavi.minimap",
            "com.sankuai.meituan",
            "com.sankuai.meituan.takeoutnew",
            "me.ele",
            "com.android.keyguard",
            "com.eg.android.AlipayGphone",
        )

        private val HYPER_OS_2_EXCLUSIONS = setOf(
            "com.android.soundrecorder",
            "com.android.deskclock",
            "com.android.settings",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.miui.securitycenter",
        )

        /**
         * Stock 7.4.67-C `sort.b.d` parses only root `updatable` and `reopen` fields. The old parser
         * also accepted HyperIsland `param_v2` and boolean reopen values, which broadened filtering
         * beyond stock collection behavior.
         */
        internal fun matchesFocusMode(notification: Notification?, expectedMode: String): Boolean {
            val rawParam = notification?.extras?.getString(FOCUS_PARAM)?.takeIf(String::isNotEmpty)
                ?: return false
            return runCatching {
                val root = JSONObject(rawParam)
                root.optBoolean("updatable", false) &&
                    root.optString("reopen", MODE_CLOSE) == expectedMode
            }.getOrDefault(false)
        }

        /**
         * Stock 7.4.67-C `notificationcollection.local.b` selects sort.d for
         * `ro.mi.os.version.code >= 2`, sort.c for code 1, and no focus filter otherwise. The old
         * implementation enabled one policy on non-Xiaomi Android as well, so selection now follows
         * the same system-property boundary.
         */
        internal fun policyForOsVersionCode(rawCode: String?): Policy {
            val code = rawCode?.takeIf { value -> value.all(Char::isDigit) }?.toIntOrNull() ?: 0
            return when {
                code >= 2 -> Policy.HYPER_OS_2
                code >= 1 -> Policy.HYPER_OS_1
                else -> Policy.DISABLED
            }
        }
    }
}

internal object FocusNotificationCollection {
    private const val TAG = "FocusCollectionFilter"
    private const val MI_OS_VERSION_CODE = "ro.mi.os.version.code"
    private const val DELETE_TIMEOUT_MS = 86_400_000L
    private const val ACTION_DELETE_TIMEOUT =
        "com.xiaomi.xmsf.action.FOCUS_NOTIFICATION_DELETE_TIMEOUT"
    private const val EXTRA_KEY = "extra_key"

    private val lock = Any()

    @Volatile
    private var filter: FocusNotificationCollectionFilter? = null

    fun initialize(context: Context) {
        getOrCreateFilter(context)
    }

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification): Boolean {
        return getOrCreateFilter(context).onNotificationPosted(sbn)
    }

    fun onNotificationRemoved(context: Context, sbn: StatusBarNotification, reason: Int) {
        getOrCreateFilter(context).onNotificationRemoved(sbn, reason)
    }

    private fun onTimeout(key: String) {
        filter?.onTimeout(key)
    }

    private fun getOrCreateFilter(context: Context): FocusNotificationCollectionFilter {
        filter?.let { return it }
        return synchronized(lock) {
            filter ?: createFilter(context.applicationContext ?: context).also { filter = it }
        }
    }

    private fun createFilter(context: Context): FocusNotificationCollectionFilter {
        val policy = FocusNotificationCollectionFilter.policyForOsVersionCode(
            SystemProperties.get(MI_OS_VERSION_CODE, ""),
        )
        val scheduler = if (policy == FocusNotificationCollectionFilter.Policy.HYPER_OS_2) {
            AlarmTimeoutScheduler(context)
        } else {
            FocusNotificationCollectionFilter.TimeoutScheduler.NONE
        }
        Napier.d("initialized policy=$policy", tag = TAG)
        return FocusNotificationCollectionFilter(policy, scheduler)
    }

    private class AlarmTimeoutScheduler(context: Context) :
        FocusNotificationCollectionFilter.TimeoutScheduler {
        private val appContext = context.applicationContext ?: context
        private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        private val pendingIntents = ConcurrentHashMap<String, PendingIntent>()

        init {
            ContextCompat.registerReceiver(
                appContext,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action != ACTION_DELETE_TIMEOUT) return
                        intent.getStringExtra(EXTRA_KEY)?.takeIf(String::isNotEmpty)?.let(::onTimeout)
                    }
                },
                IntentFilter(ACTION_DELETE_TIMEOUT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        override fun schedule(key: String) {
            // Stock 7.4.67-C sort.d.i uses ELAPSED_REALTIME_WAKEUP plus an exact 24-hour alarm.
            // A wall-clock timestamp or persisted expiry would survive conditions stock forgets,
            // so this intentionally keeps only the process-local PendingIntent map.
            val intent = Intent(ACTION_DELETE_TIMEOUT).apply {
                setPackage(appContext.packageName)
                putExtra(EXTRA_KEY, key)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                key.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            pendingIntents[key] = pendingIntent
            runCatching {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + DELETE_TIMEOUT_MS,
                    pendingIntent,
                )
            }.onFailure {
                Napier.w("failed to schedule key=$key: ${it.message}", it, tag = TAG)
            }
        }

        override fun cancel(key: String) {
            val pendingIntent = pendingIntents.remove(key) ?: return
            runCatching { alarmManager?.cancel(pendingIntent) }
                .onFailure { Napier.w("failed to cancel key=$key: ${it.message}", it, tag = TAG) }
        }
    }
}
