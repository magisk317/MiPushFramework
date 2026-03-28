package com.xiaomi.push.service

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.magisk317.Global
import com.magisk317.XMPushUtils
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.push.notification.NotificationController.getBitmapFromUri
import com.xiaomi.xmsf.push.notification.NotificationController.getLargeIcon
import com.xiaomi.xmsf.push.notification.NotificationController.roundLargeIconIfConfigured

internal object MyMIPushNotificationStyleSupport {
    private const val NOTIFICATION_BIG_STYLE_MIN_LEN = 25

    fun getPackageContext(context: Context, packageName: String): Context {
        if (!com.magisk317.notification.NotificationManagerEx.isHooked) {
            return context
        }
        return try {
            context.createPackageContext(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            MyMIPushNotificationLogs.logger.e(e.message ?: "Unknown package manager error", e)
            context
        }
    }

    fun normalStyleNotificationBuilder(
        context: Context,
        metaInfo: PushMetaInfo
    ): NotificationCompat.Builder {
        val title = metaInfo.title.orEmpty()
        val description = metaInfo.description.orEmpty()
        val bigPic = getBigPic(context, metaInfo)

        return NotificationCompat.Builder(context, "xmsf.default").apply {
            if (bigPic != null) {
                val style = NotificationCompat.BigPictureStyle()
                style.bigPicture(bigPic)
                style.setBigContentTitle(title)
                setStyle(style)
            } else if (description.length > NOTIFICATION_BIG_STYLE_MIN_LEN) {
                val style = NotificationCompat.BigTextStyle()
                style.bigText(description)
                style.setBigContentTitle(title)
                setStyle(style)
            }

            val titleAndDesp = determineTitleAndDespByDIP(context, metaInfo)
            setContentTitle(titleAndDesp[0])
            setContentText(titleAndDesp[1])
        }
    }

    fun messagingStyleNotificationBuilder(
        context: Context,
        container: XmPushActionContainer,
        notificationId: Int,
        message: NotificationCompat.MessagingStyle.Message,
        pkgCtx: Context
    ): NotificationCompat.Builder {
        val packageName = container.packageName
        val messagingBuilder = addToExistingMessageNotification(context, packageName, notificationId, message)
        return messagingBuilder ?: createMessageStyleNotificationBuilder(
            context,
            container,
            message,
            pkgCtx,
            packageName
        )
    }

    fun createMessage(
        context: Context,
        container: XmPushActionContainer,
        pkgCtx: Context
    ): NotificationCompat.MessagingStyle.Message? {
        val metaInfo = container.metaInfo
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val senderMessage = custom.conversationMessage(null) ?: return null
        return createMessage(context, pkgCtx, metaInfo, senderMessage)
    }

    fun getSdkIntentForMessagingStyle(
        context: Context,
        container: XmPushActionContainer,
        packageName: String
    ) = MyMIPushNotificationIntentSupport.getSdkIntent(context, container)
        ?: context.packageManager.getLaunchIntentForPackage(packageName)

    private fun getBigPic(context: Context, metaInfo: PushMetaInfo): Bitmap? {
        val configuration = XMPushUtils.getConfiguration(metaInfo)
        val bigPicUri = configuration.notificationBigPicUri(null)
        return Global.IconCache().getBitmap(
            context,
            bigPicUri,
            object : top.trumeet.common.cache.IconCache.Converter<String, Bitmap> {
                override fun convert(ctx: Context, b: String): Bitmap {
                    return getBitmapFromUri(ctx, b, 1 * MyNotificationIconHelper.MiB)!!
                }
            }
        )
    }

    private fun addToExistingMessageNotification(
        context: Context,
        packageName: String,
        notificationId: Int,
        message: NotificationCompat.MessagingStyle.Message
    ): NotificationCompat.Builder? {
        return try {
            val activeNotification = findActiveNotification(packageName, notificationId)
            if (activeNotification != null) {
                val activeStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(activeNotification)
                if (activeStyle != null) {
                    return NotificationCompat.Builder(context, activeNotification).apply {
                        activeStyle.addMessage(message)
                        setStyle(activeStyle)
                    }
                }
            }
            null
        } catch (e: Exception) {
            MyMIPushNotificationLogs.logger.e(e.localizedMessage, e)
            null
        }
    }

    private fun findActiveNotification(packageName: String, notificationId: Int) =
        com.xiaomi.xmsf.push.notification.NotificationController.getNotificationManagerEx()
            .getActiveNotifications(packageName)
            ?.firstOrNull { it != null && it.id == notificationId }
            ?.notification

    private fun createMessageStyleNotificationBuilder(
        context: Context,
        container: XmPushActionContainer,
        message: NotificationCompat.MessagingStyle.Message,
        pkgCtx: Context,
        packageName: String
    ): NotificationCompat.Builder {
        val metaInfo = container.metaInfo
        val group = getGroupFor(context, metaInfo).build()
        return NotificationCompat.Builder(context, "xmsf.default").apply {
            attachMessagingStyle(message, group, metaInfo, this)
            addShortcutToEnableMessagingStyle(context, container, pkgCtx, packageName, group, this)
        }
    }

    private fun attachMessagingStyle(
        message: NotificationCompat.MessagingStyle.Message,
        group: Person,
        metaInfo: PushMetaInfo,
        notificationBuilder: NotificationCompat.Builder
    ) {
        val style = NotificationCompat.MessagingStyle(group)
        style.setConversationTitle(group.name)
        style.setGroupConversation(isGroupConversation(metaInfo))
        style.addMessage(message)
        notificationBuilder.setStyle(style)
    }

    private fun addShortcutToEnableMessagingStyle(
        context: Context,
        container: XmPushActionContainer,
        pkgCtx: Context,
        packageName: String,
        group: Person,
        notificationBuilder: NotificationCompat.Builder
    ) {
        try {
            val key = group.key ?: group.name.toString()
            val intent = getSdkIntentForMessagingStyle(context, container, packageName) ?: return
            val shortcut = ShortcutInfoCompat.Builder(pkgCtx, key)
                .setIntent(intent)
                .setLongLived(true)
                .setShortLabel(group.name ?: key)
                .setIcon(group.icon)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(pkgCtx, shortcut)
            notificationBuilder.setShortcutInfo(shortcut)
        } catch (_: Throwable) {
        }
    }

    private fun createMessage(
        context: Context,
        pkgCtx: Context,
        metaInfo: PushMetaInfo,
        senderMessage: String
    ): NotificationCompat.MessagingStyle.Message {
        val atLeastP = pkgCtx.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.P
        val person = if (isGroupConversation(metaInfo) || atLeastP) {
            getPerson(context, metaInfo).build()
        } else {
            null
        }
        return NotificationCompat.MessagingStyle.Message(senderMessage, metaInfo.messageTs, person)
    }

    private fun isGroupConversation(metaInfo: PushMetaInfo): Boolean {
        return XMPushUtils.getConfiguration(metaInfo).conversationTitle(null) != null
    }

    private fun getGroupFor(context: Context, metaInfo: PushMetaInfo): Person.Builder {
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val conversation = custom.conversationTitle(null)
        val conversationId = custom.conversationId(null)
        val conversationIcon = custom.conversationIcon(null)

        val personBuilder = if (isGroupConversation(metaInfo)) Person.Builder() else getPerson(context, metaInfo)
        if (conversation != null) {
            personBuilder.setName(conversation)
        } else if (personBuilder.build().name == null) {
            personBuilder.setName(metaInfo.title)
        }
        if (conversationId != null) {
            personBuilder.setKey(conversationId)
        }
        val largeIcon = getLargeIcon(context, metaInfo, conversationIcon)
        if (largeIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(largeIcon))
        }
        return personBuilder
    }

    private fun getPerson(context: Context, metaInfo: PushMetaInfo): Person.Builder {
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val sender = custom.conversationSender(null)
        val senderId = custom.conversationSenderId(null)
        val senderIcon = custom.conversationSenderIcon(null)
        val textIcon = custom.textIcon(null)

        val personBuilder = Person.Builder().setName(sender)
        personBuilder.setImportant(custom.conversationImportant(false))
        if (senderId != null) {
            personBuilder.setKey(senderId)
        }
        val largeIcon = getLargeIcon(context, metaInfo, senderIcon)
        if (largeIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(largeIcon))
        } else if (textIcon != null) {
            personBuilder.setIcon(
                IconCompat.createWithBitmap(
                    roundLargeIconIfConfigured(
                        metaInfo,
                        ImageUtils.textToBitmap(textIcon, 72f, 0xFF003E6F.toInt(), Color.WHITE)
                    )
                )
            )
        }
        return personBuilder
    }

    private fun determineTitleAndDespByDIP(
        context: Context,
        pushMetaInfo: PushMetaInfo
    ): Array<String> {
        return try {
            MIPushNotificationViewSupport.determineTitleAndDespByDIP(context, pushMetaInfo).let { values ->
                arrayOf(values.getOrNull(0).orEmpty(), values.getOrNull(1).orEmpty())
            }
        } catch (e: Exception) {
            MyMIPushNotificationLogs.logger.e(e.message ?: "Error in determineTitleAndDespByDIP", e)
            arrayOf(pushMetaInfo.title.orEmpty(), pushMetaInfo.description.orEmpty())
        }
    }
}
