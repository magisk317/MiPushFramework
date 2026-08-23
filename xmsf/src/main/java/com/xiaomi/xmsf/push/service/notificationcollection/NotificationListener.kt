package com.xiaomi.xmsf.push.service.notificationcollection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.xiaomi.push.service.NotificationUtils
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.notification.TopNotificationCoordinator
import io.github.magisk317.mipush.notification.SweetNotificationCoordinator

@Suppress("DEPRECATION")
class NotificationListener : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        FocusNotificationCollection.initialize(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        StockSurfaceSupport.recordNotificationEvent(this, "listener_connected", packageName)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val eventUserId = sbn.userId
        if (!acceptsUser(eventUserId)) {
            Logger.withTag(TAG).d { "skip notification from another user key=${sbn.key} user=$eventUserId" }
            return
        }
        // Stock 7.4.67-C schedules sweet reminder expiry from its posted callback. Keep this in
        // addition to the direct publish hook so externally reposted/top-updated records also refresh.
        SweetNotificationCoordinator.onNotificationPosted(this, sbn)
        if (FocusNotificationCollection.onNotificationPosted(this, sbn)) {
            Logger.withTag(TAG).d { "skip collected focus notification key=${sbn.key}" }
            return
        }
        if (NotificationUtils.isNotificationFromXmsf(this, sbn)) {
            StockSurfaceSupport.recordNotificationEvent(this, "posted", sbn.packageName)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (!acceptsUser(sbn.userId)) return
        // Pre-21 callbacks do not include a removal reason. Stock 7.4.67-C leaves focus-sort state
        // untouched on this overload, so preserve existing top-notification cleanup only.
        recordRemoval(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (!acceptsUser(sbn.userId)) return
        // NotificationListenerService's default three-argument method delegates to the one-argument
        // overload. Calling super here would run product cleanup twice, so handle the reason-aware
        // stock path once and stop at this boundary.
        FocusNotificationCollection.onNotificationRemoved(this, sbn, reason)
        SweetNotificationCoordinator.onNotificationRemoved(this, sbn, reason)
        recordRemoval(sbn)
    }

    private fun recordRemoval(sbn: StatusBarNotification) {
        // Stock 7.4.67-C m2.c cancels the marker-derived job here. The product coordinator also
        // cancels jobs created by the dormant vendor route; invoking both paths would let a stale
        // removal cancel the replacement generation after the coordinator deliberately rejected it.
        TopNotificationCoordinator.onNotificationRemoved(this, sbn)
        if (NotificationUtils.isNotificationFromXmsf(this, sbn)) {
            StockSurfaceSupport.recordNotificationEvent(this, "removed", sbn.packageName)
        }
    }

    companion object {
        private const val TAG = "NotificationListener"

        /**
         * Stock 7.4.67-C NotificationListener.a compares the SBN UserHandle with the process user
         * before dispatching collection callbacks. Negative IDs represent USER_ALL and remain
         * accepted; cross-user records must not mutate owner-space focus or grouping state.
         */
        internal fun acceptsUser(
            eventUserId: Int,
            currentUserId: Int = Utils.myUserId(),
        ): Boolean {
            return currentUserId < 0 || eventUserId < 0 || currentUserId == eventUserId
        }

        @JvmStatic
        fun ensureStarted(context: Context) {
            runCatching {
                // Stock 7.4.67-C first reflects registerAsSystemService on API 27+, but that call is
                // system/SystemUI-only and stock itself falls back when it is rejected. Use the
                // public rebind route so replacement and non-MIUI installs share the valid path.
                requestRebind(ComponentName(context, NotificationListener::class.java))
                context.startService(Intent(context, NotificationListener::class.java))
            }
        }
    }
}
