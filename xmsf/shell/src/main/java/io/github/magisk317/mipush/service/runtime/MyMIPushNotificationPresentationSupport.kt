package io.github.magisk317.mipush.service.runtime

import android.content.Context
import android.os.Bundle
import androidx.annotation.NonNull
import androidx.core.app.NotificationCompat
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.NotificationGroupHelper
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.stock.StockNotificationPresentationBridge
import io.github.magisk317.mipush.notification.AndroidWGroupStrategy
import io.github.magisk317.mipush.notification.VoipNotificationHelper
import io.github.magisk317.mipush.notification.policy.NotificationClickFallbackContract
import io.github.magisk317.mipush.platform.support.XMPushUtils

internal object MyMIPushNotificationPresentationSupport {
    internal data class NotificationInfo(
        val notificationId: Int,
        val notificationBuilder: NotificationCompat.Builder,
    )

    @NonNull
    fun getNotificationFor(
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray,
        notificationId: Int,
    ): NotificationInfo {
        val metaInfo = container.metaInfo
        val packageName = MIPushNotificationHelper.getTargetPackage(container)

        val pkgCtx = XMPushUtils.getPackageContext(context, packageName)
        val message = MyMIPushNotificationStyleSupport.createMessage(context, container, pkgCtx)
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val useMessagingStyle = message != null && custom.useMessagingStyle(false)

        val sourceGroup = getSourceGroup(metaInfo)
        // Stock 7.4.67-C t0 keeps delegated notifications under the target package's
        // default group. Only a non-MIUI payload that explicitly disables that default
        // retains its source group, so use the same identity before click/group handling.
        val group = MyMIPushNotificationHelper.resolveStockGroup(
            targetPackage = packageName,
            sourceGroup = sourceGroup,
            disableDefault = metaInfo.extra
                ?.get("notification_group_disable_default")
                ?.toBoolean() == true,
            isMiui = MIUIUtils.isMIUI(),
        )
        val intentExtra = android.content.Intent()
        intentExtra.putExtra(io.github.magisk317.mipush.common.Constants.INTENT_NOTIFICATION_ID, notificationId)
        intentExtra.putExtra(io.github.magisk317.mipush.common.Constants.INTENT_NOTIFICATION_GROUP, group)

        val localPendingIntent = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context,
            container,
            decryptedContent,
            notificationId,
            intentExtra.extras,
        )

        val voipBuilder = if (VoipNotificationHelper.isVoipNotification(metaInfo)) {
            VoipNotificationHelper.buildVoipNotification(
                context, packageName, metaInfo, notificationId, localPendingIntent
            )
        } else null

        val notificationBuilder = voipBuilder ?: if (useMessagingStyle) {
            MyMIPushNotificationStyleSupport.messagingStyleNotificationBuilder(
                context,
                container,
                notificationId,
                message,
                pkgCtx,
            )
        } else {
            MyMIPushNotificationStyleSupport.normalStyleNotificationBuilder(pkgCtx, container.metaInfo, packageName)
        }

        if (MyMIPushNotificationIntentSupport.shouldUseLauncherFallback(packageName)) {
            notificationBuilder.extras.putBoolean(
                NotificationClickFallbackContract.USE_LAUNCHER_FALLBACK,
                true,
            )
        }

        if (metaInfo.extra != null) {
            MyMIPushNotificationIntentSupport.addStyleActions(
                notificationBuilder,
                context,
                packageName,
                notificationId,
                metaInfo.extra,
            )
        }

        StockNotificationPresentationBridge.apply(metaInfo, notificationBuilder)

        if (MIUIUtils.isMIUI()) {
            // Stock 7.4.67-C t0 writes these fields into each MIUI notification so
            // active-notification clear and reporting can identify delegated records.
            // This custom builder bypasses t0, so it must restore that metadata here.
            val stockExtras = Bundle().apply {
                MyMIPushNotificationHelper.buildStockMiuiIdentityExtras(container, packageName)
                    .forEach(::putString)
            }
            notificationBuilder.addExtras(stockExtras)
        }
        AndroidWGroupStrategy.applyIfEligible(context, sourceGroup, notificationBuilder.extras)
        val effectiveGroup = group?.let {
            NotificationGroupHelper.maskGroup(context, notificationBuilder.extras, it)
        }

        notificationBuilder.setGroup(effectiveGroup)
        notificationBuilder.setGroupSummary(
            metaInfo.extra?.get("notification_is_summary")?.toBoolean() == true
        )

        if (localPendingIntent != null) {
            notificationBuilder.setContentIntent(localPendingIntent)
            MyMIPushNotificationIntentSupport.carryPendingIntentForTemporarilyWhitelisted(
                context,
                container,
                notificationId,
                notificationBuilder,
            )
        }
        return NotificationInfo(notificationId, notificationBuilder)
    }

    private fun getSourceGroup(metaInfo: PushMetaInfo): String? {
        return XMPushUtils.getConfiguration(metaInfo)
            .notificationGroup(null)
            ?.takeIf { it.isNotBlank() }
    }
}
