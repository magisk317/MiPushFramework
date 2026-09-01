package io.github.magisk317.mipush.service.runtime

import android.app.Notification
import android.app.Notification.GROUP_ALERT_SUMMARY
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import co.touchlab.kermit.Logger
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.service.ForegroundHelper.Companion.CHANNEL_STATUS
import io.github.magisk317.xposed.logging.MagiskOtel

@RequiresApi(29)
object BackgroundActivityStartEnabler {

    private val notificationLock = Any()
    private var whitelistedNotification: Notification? = null
    private const val TAG = "MPF.BAFE"
    private const val CAPTURE_DELAY_MS = 500L
    private const val INITIAL_CAPTURE_RETRIES = 5


    internal data class NotificationCaptureCandidate(
        val id: Int,
        val tag: String?,
        val hasPendingIntent: Boolean,
    )

    private enum class CaptureSource {
        None,
        InitializingNotification,
        ExistingNotification,
    }
    @JvmStatic
    fun clonePendingIntentForBackgroundActivityStart(pi: PendingIntent): PendingIntent? {
        synchronized(notificationLock) {
            val source = whitelistedNotification ?: return null
            source.contentIntent = pi
            val parcel = Parcel.obtain()
            try {
                source.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                val copied = Notification.CREATOR.createFromParcel(parcel)
                val whitelisted = copied.contentIntent
                copied.contentIntent = null
                return whitelisted
            } finally {
                parcel.recycle()
                source.contentIntent = null
            }
        }
    }

    @JvmStatic
    fun initialize(context: Context) {
        synchronized(notificationLock) {
            // A service restart must not reuse a Notification object captured by an older
            // initialization cycle.
            whitelistedNotification = null
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm == null) {
            emitBg(result = "error", reason = "no_notification_manager", statusOk = false)
            return
        }
        val canPostInitializingNotification = nm.areNotificationsEnabled()
        val postedChannelId = if (canPostInitializingNotification) {
            val channelId = tryGetValidPushStatusChannelId(context, nm)
            if (channelId == null) {
                emitBg(result = "error", reason = "no_channel", statusOk = false)
                return
            }
            if (notifyPushStatusInitializing(context, channelId, nm)) {
                channelId
            } else {
                Logger.withTag(TAG).w {
                    "Initializing notification unavailable; trying existing active notification donor"
                }
                null
            }
        } else {
            Logger.withTag(TAG).i {
                "Notifications disabled for XMSF; using existing active notification donor"
            }
            null
        }
        scheduleCapture(nm, postedChannelId, INITIAL_CAPTURE_RETRIES)
        emitBg(
            result = "ok",
            reason = if (postedChannelId == null) "initialized_existing_notification_fallback" else "initialized",
        )
    }

    private fun emitBg(
        result: String,
        reason: String,
        statusOk: Boolean = true,
    ) {
        MagiskOtel.event(
            name = "push.lifecycle",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "bg_activity_start_enabler",
                "reason" to reason,
            ),
            statusOk = statusOk,
        )
    }

    private fun notifyPushStatusInitializing(
        context: Context,
        channelId: String,
        nm: NotificationManager
    ): Boolean {
        val n = Notification.Builder(context, channelId)
            .setTimeoutAfter(5_000)
            .setContentTitle("Initializing...")
            .setOngoing(true)
            .setGroup(TAG)
            .setGroupAlertBehavior(GROUP_ALERT_SUMMARY)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .build()
        return runCatching {
            nm.notify(TAG, 0, n)
            true
        }.onFailure {
            Logger.withTag(TAG).e(it) { "Failed to post initializing notification: ${it.message}" }
        }.getOrDefault(false)
    }

    private fun tryGetValidPushStatusChannelId(
        context: Context,
        nm: NotificationManager
    ): String? {
        var channelId = CHANNEL_STATUS
        var channelPostfix = 0
        while (true) {
            var channel = nm.getNotificationChannel(channelId)
            if (channel == null) {
                if (CHANNEL_STATUS == channelId) {
                    channelId = CHANNEL_STATUS + (++channelPostfix)
                    continue
                }
                channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.notification_category_alive),
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(channel)
                break
            } else {
                if (channel.importance > NotificationManager.IMPORTANCE_NONE) break
                if (channelPostfix == 16) {
                    Logger.withTag(TAG).e { "Failed to obtain available notification channel." }
                    return null
                }
                channelId = CHANNEL_STATUS + (++channelPostfix)
            }
        }
        return channelId
    }

    private fun scheduleCapture(
        nm: NotificationManager,
        postedChannelId: String?,
        retries: Int,
    ) {
        val attempt = INITIAL_CAPTURE_RETRIES - retries + 1
        Handler(Looper.getMainLooper()).postDelayed({
            val notifications = runCatching { nm.activeNotifications }
                .onFailure {
                    Logger.withTag(TAG).e(it) {
                        "Failed to read active notifications attempt=$attempt: ${it.message}"
                    }
                }
                .getOrDefault(emptyArray())
            val captureSource = findCaptureNotification(notifications)
            val captured = captureSource != CaptureSource.None
            Logger.withTag(TAG).d {
                "Capture attempt=$attempt activeCount=${notifications.size} captured=$captured " +
                    "source=${captureSource.name} remainingRetries=$retries"
            }
            if (captured || pushStatusInitializingNotificationExists()) {
                cancelInitializingNotificationSafely(nm)
                deleteTemporaryChannel(nm, postedChannelId)
                emitBg(
                    result = "ok",
                    reason = if (captureSource == CaptureSource.ExistingNotification) {
                        "existing_active_notification_captured"
                    } else {
                        "active_notification_captured"
                    },
                )
            } else if (retries == 0) {
                Logger.withTag(TAG).e {
                    "Failed to capture active notification after attempts=$attempt"
                }
                emitBg(result = "error", reason = "active_notification_capture_failed", statusOk = false)
                cancelInitializingNotificationSafely(nm)
                deleteTemporaryChannel(nm, postedChannelId)
            } else {
                Logger.withTag(TAG).i {
                    "Wait to capture active notification attempt=$attempt nextAttempt=${attempt + 1}"
                }
                scheduleCapture(nm, postedChannelId, retries - 1)
            }
        }, CAPTURE_DELAY_MS)
    }

    private fun cancelInitializingNotificationSafely(nm: NotificationManager) {
        runCatching { nm.cancel(TAG, 0) }
            .onFailure {
                Logger.withTag(TAG).w(it) { "skip initializing notification cleanup: ${it.message}" }
            }
    }

    private fun deleteTemporaryChannel(nm: NotificationManager, postedChannelId: String?) {
        if (postedChannelId != null && CHANNEL_STATUS != postedChannelId) {
            nm.deleteNotificationChannel(postedChannelId)
        }
    }

    private fun pushStatusInitializingNotificationExists(): Boolean = synchronized(notificationLock) {
        whitelistedNotification != null
    }

    private fun findCaptureNotification(
        notifications: Array<StatusBarNotification>,
    ): CaptureSource {
        val candidates = notifications.map { statusBarNotification ->
            val notification = statusBarNotification.notification
            NotificationCaptureCandidate(
                id = statusBarNotification.id,
                tag = statusBarNotification.tag,
                hasPendingIntent = notification.contentIntent != null ||
                    notification.deleteIntent != null ||
                    notification.fullScreenIntent != null ||
                    notification.actions?.any { action -> action.actionIntent != null } == true,
            )
        }
        val candidateIndex = selectCaptureCandidate(candidates)
        if (candidateIndex < 0) {
            return CaptureSource.None
        }
        val statusBarNotification = notifications[candidateIndex]
        synchronized(notificationLock) {
            whitelistedNotification = statusBarNotification.notification
        }
        return if (isInitializingNotification(statusBarNotification.id, statusBarNotification.tag)) {
            CaptureSource.InitializingNotification
        } else {
            Logger.withTag(TAG).i {
                "Captured existing active notification donor " +
                    "pkg=${statusBarNotification.packageName} id=${statusBarNotification.id} " +
                    "tag=${statusBarNotification.tag} channel=${statusBarNotification.notification.channelId}"
            }
            CaptureSource.ExistingNotification
        }
    }

    internal fun selectCaptureCandidate(candidates: List<NotificationCaptureCandidate>): Int {
        val initializingIndex = candidates.indexOfFirst { candidate ->
            isInitializingNotification(candidate.id, candidate.tag)
        }
        return if (initializingIndex >= 0) {
            initializingIndex
        } else {
            candidates.indexOfFirst(NotificationCaptureCandidate::hasPendingIntent)
        }
    }

    internal fun isInitializingNotification(id: Int, tag: String?): Boolean =
        id == 0 && TAG == tag
}
