package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.common.R as CommonR

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.common.notification.NotificationContentSupport
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import io.github.magisk317.mipush.runtime.PushRuntime
import com.xiaomi.xmpush.thrift.PushMetaInfo

/** Shell-local mock replay visibility and receipt publishing, including Xiaomi hooked posting. */
internal object NotificationMockReplaySupport {
    private const val TAG = "NotificationController"
    private const val RECEIPT_CHANNEL_ID = "mipush_mock_replay_receipt"
    private const val RECEIPT_TAG_PREFIX = "mipush_mock_replay_receipt:"

    fun isMockReplay(metaInfo: PushMetaInfo): Boolean =
        metaInfo.extra?.get(MockMessageRegistry.EXTRA_MOCK_REPLAY)
            ?.equals("true", ignoreCase = true) == true

    fun applyVisibility(
        notificationBuilder: NotificationCompat.Builder,
        packageName: String,
        notificationId: Int,
    ) {
        val group = "$packageName#mipush_mock_replay#$notificationId"
        notificationBuilder.setCategory(Notification.CATEGORY_ALARM)
        notificationBuilder.priority = NotificationCompat.PRIORITY_MAX
        notificationBuilder.setGroup(null)
        notificationBuilder.setGroupSummary(false)
        notificationBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
        notificationBuilder.setDefaults(Notification.DEFAULT_ALL)
        notificationBuilder.setOnlyAlertOnce(false)
        notificationBuilder.setSilent(false)
        Logger.withTag(TAG).d {
            "apply mock replay visibility pkg=$packageName category=alarm priority=max group=$group summary=false"
        }
    }

    fun shouldPostVisibleReceipt(isMockReplay: Boolean, options: MiPushIslandOptions): Boolean =
        isMockReplay && options.showOriginalNotification

    fun shouldAttachPayloadLargeIcon(isMockReplay: Boolean, colorStatusBarIcon: Boolean): Boolean =
        !isMockReplay || colorStatusBarIcon

    fun receiptNotificationId(packageName: String): Int = 0x4d520000 xor packageName.hashCode()

    fun postVisibleReceipt(
        context: Context,
        packageName: String,
        notificationId: Int,
        originalTag: String?,
        source: Notification,
        replaceOriginal: Boolean,
        colorStatusBarIcon: Boolean?,
        resolveUserId: (Context, String) -> Int,
        applyStatusBarIcon: (Context, String, NotificationCompat.Builder, Boolean) -> Int,
    ): Boolean {
        val userId = resolveUserId(context, packageName)
        val effectiveColorStatusBarIcon = colorStatusBarIcon
            ?: MiPushIslandPreferences.read(context, packageName, userId).colorStatusBarIcon
        if (!NotificationManagerEx.canNotifyForUser(
                userId,
                io.github.magisk317.mipush.common.utils.Utils.requireValidUserId(
                    io.github.magisk317.mipush.common.utils.Utils.myUserId(),
                ),
            )) {
            Logger.withTag(TAG).w { "skip mock replay receipt for foreign user=$userId pkg=$packageName" }
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        ensureReceiptChannel(manager, packageName)
        val appName = Global.applicationNameCache().getAppName(context, packageName)
        val title = NotificationContentSupport.firstText(
            source.extras,
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
        ) ?: source.tickerText?.toString() ?: appName
        val content = NotificationContentSupport.firstText(
            source.extras,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
        ) ?: appName
        val sourceSmallIcon = runCatching {
            val field = Notification::class.java.getDeclaredField("mSmallIcon")
            field.isAccessible = true
            field.get(source) as? android.graphics.drawable.Icon
        }.getOrNull()
        val builder = NotificationCompat.Builder(context, RECEIPT_CHANNEL_ID)
            .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSubText(appName)
            .setWhen(source.`when`.takeIf { it > 0L } ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .addExtras(
                Bundle().apply {
                    putString("target_package", packageName)
                    putString("miui.targetPkg", packageName)
                    putBoolean("mipush_mock_replay_receipt", true)
                    putString("mipush_mock_replay_source_package", packageName)
                },
            )
        if (source.contentIntent != null) builder.setContentIntent(source.contentIntent)
        if (sourceSmallIcon != null) {
            @SuppressLint("RestrictedApi")
            runCatching { builder.setSmallIcon(IconCompat.createFromIcon(sourceSmallIcon)) }
        }
        applyStatusBarIcon(context, packageName, builder, effectiveColorStatusBarIcon)
        val receipt = ProgressStyleBuilder.buildNotification(context, builder)
        val receiptTag = if (replaceOriginal) originalTag else "$RECEIPT_TAG_PREFIX$packageName"
        val receiptId = if (replaceOriginal) notificationId else receiptNotificationId(packageName)
        return runCatching {
            val postedAsTarget = NotificationManagerEx.isHooked &&
                NotificationController.getNotificationManagerEx().notify(packageName, receiptTag, receiptId, receipt, userId)
            if (!postedAsTarget) manager.notify(receiptTag, receiptId, receipt)
            PushRuntime.observeNotificationEvent(packageName, "mock_replay_visible_receipt_posted", "NotificationController.publish")
            Logger.withTag(TAG).d {
                "posted mock replay visible receipt pkg=$packageName sourceId=$notificationId tag=$receiptTag targetIdentity=$postedAsTarget"
            }
            true
        }.onFailure {
            Logger.withTag(TAG).w(it) { "mock replay visible receipt failed pkg=$packageName id=$notificationId: ${it.message}" }
        }.getOrDefault(false)
    }

    private fun ensureReceiptChannel(manager: NotificationManager, packageName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            RECEIPT_CHANNEL_ID,
            "MiPush replay",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        if (NotificationManagerEx.isHooked) {
            NotificationController.getNotificationManagerEx().createNotificationChannels(packageName, listOf(channel))
        }
        if (manager.getNotificationChannel(RECEIPT_CHANNEL_ID) == null) manager.createNotificationChannel(channel)
    }
}
