@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.push.service

import android.annotation.TargetApi
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.magisk317.push.hook.ExplicitHookBridge
import com.magisk317.Global
import com.magisk317.XMPushUtils
import com.magisk317.notification.NotificationManagerEx
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.mipush.sdk.PushMessageProcessor
import com.xiaomi.push.sdk.MyPushMessageHandler
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.push.notification.NotificationController
import com.xiaomi.xmsf.push.notification.NotificationController.getBitmapFromUri
import com.xiaomi.xmsf.push.notification.NotificationController.getLargeIcon
import com.xiaomi.xmsf.push.notification.NotificationController.getNotificationManagerEx
import com.xiaomi.xmsf.push.notification.NotificationController.roundLargeIconIfConfigured
import com.xiaomi.xmsf.push.utils.Configurations
import com.xiaomi.xmsf.push.utils.IconConfigurations
import com.xiaomi.xmsf.push.utils.PackageConfig
import com.xiaomi.xmsf.utils.ConfigCenter
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.db.RegisteredApplicationDb

class MyMIPushNotificationHelper {
    private class NotificationInfo(
        val notificationId: Int,
        val notificationBuilder: NotificationCompat.Builder
    )

    companion object {
        const val CLASS_NAME_PUSH_MESSAGE_HANDLER = "com.xiaomi.mipush.sdk.PushMessageHandler"

        private val logger: Logger = XLog.tag("MyNotificationHelper").build()
        private const val NOTIFICATION_BIG_STYLE_MIN_LEN = 25

        private const val GROUP_TYPE_MIPUSH_GROUP = "#group#"
        private const val GROUP_TYPE_PASS_THROUGH = "#pass_through#"

        private const val NOTIFICATION_ACTION_BUTTON_PLACE_MID = 2
        private const val NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT = 3
        private const val NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_CLASS = "notification_style_button_left_intent_class"
        private const val NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_URI = "notification_style_button_left_intent_uri"
        private const val NOTIFICATION_STYLE_BUTTON_LEFT_NAME = "notification_style_button_left_name"
        private const val NOTIFICATION_STYLE_BUTTON_LEFT_NOTIFY_EFFECT = "notification_style_button_left_notify_effect"
        private const val NOTIFICATION_STYLE_BUTTON_LEFT_WEB_URI = "notification_style_button_left_web_uri"
        private const val NOTIFICATION_STYLE_BUTTON_MID_INTENT_CLASS = "notification_style_button_mid_intent_class"
        private const val NOTIFICATION_STYLE_BUTTON_MID_INTENT_URI = "notification_style_button_mid_intent_uri"
        private const val NOTIFICATION_STYLE_BUTTON_MID_NAME = "notification_style_button_mid_name"
        private const val NOTIFICATION_STYLE_BUTTON_MID_NOTIFY_EFFECT = "notification_style_button_mid_notify_effect"
        private const val NOTIFICATION_STYLE_BUTTON_MID_WEB_URI = "notification_style_button_mid_web_uri"
        private const val NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_CLASS = "notification_style_button_right_intent_class"
        private const val NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_URI = "notification_style_button_right_intent_uri"
        private const val NOTIFICATION_STYLE_BUTTON_RIGHT_NAME = "notification_style_button_right_name"
        private const val NOTIFICATION_STYLE_BUTTON_RIGHT_NOTIFY_EFFECT = "notification_style_button_right_notify_effect"
        private const val NOTIFICATION_STYLE_BUTTON_RIGHT_WEB_URI = "notification_style_button_right_web_uri"

        private var tryLoadConfigurations = false
        private val executorService: ExecutorService = Executors.newFixedThreadPool(3)
        private const val FLAG_IMMUTABLE_UPDATE_CURRENT =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        @JvmStatic
        fun notifyPushMessage(context: Context, decryptedContent: ByteArray) {
            val container = XMPushUtils.packToContainer(decryptedContent) ?: return
            ExplicitHookBridge.notifyPushMessage(context, container, decryptedContent)
            val notificationOp = AppInfoUtils.getAppNotificationOp(
                context,
                MIPushNotificationHelper.getTargetPackage(container),
                true
            )
            if (notificationOp == AppInfoUtils.AppNotificationOp.NOT_ALLOWED) {
                logger.w("Do not notify because user block " + MIPushNotificationHelper.getTargetPackage(container) + "'s notification")
            } else {
                loadConfigurationsOnce(context)
                handleNotificationByConfigurations(context, decryptedContent, container.packageName, container)
            }
        }

        private fun handleNotificationByConfigurations(
            context: Context,
            decryptedContent: ByteArray,
            packageName: String,
            container: XmPushActionContainer
        ) {
            try {
                val operations = Configurations.getInstance().handle(packageName, container)
                if (operations.contains(PackageConfig.OPERATION_WAKE)) {
                    wakeScreen(context, packageName)
                }
                if (!operations.contains(PackageConfig.OPERATION_IGNORE)) {
                    executorService.execute {
                        try {
                            doNotifyPushMessage(context, container, decryptedContent)
                        } catch (e: Exception) {
                            logger.e(e.localizedMessage, e)
                        }
                    }
                }
                if (operations.contains(PackageConfig.OPERATION_OPEN)) {
                    MyPushMessageHandler.startService(context, container, decryptedContent)
                }
            } catch (e: Exception) {
                logger.e(e.localizedMessage, e)
            }
        }

        private fun loadConfigurationsOnce(context: Context) {
            if (!tryLoadConfigurations) {
                tryLoadConfigurations = true
                try {
                    val configCenter: ConfigCenter = Global.ConfigCenter()
                    val configurationDirectory = configCenter.getConfigurationDirectory(context)
                    loadConfigurations(context, configurationDirectory)
                } catch (e: Exception) {
                    Utils.makeText(context, e.toString(), Toast.LENGTH_LONG)
                }
            }
        }

        private fun loadConfigurations(context: Context, configurationDirectory: Uri?) {
            val configurations = Configurations.getInstance()
            if (configurations.init(context, configurationDirectory)) {
                val iconConfigurations: IconConfigurations = Global.IconConfigurations()
                iconConfigurations.init(context, configurationDirectory)
            }
        }

        private fun wakeScreen(context: Context, sourcePackage: String) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val fullWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "xmsf: configurations of $sourcePackage"
            )
            fullWakeLock.acquire(10000)
        }

        private fun findActiveNotification(packageName: String, notificationId: Int): Notification? {
            val notifications = getNotificationManagerEx().getActiveNotifications(packageName) ?: return null
            for (notification in notifications) {
                if (notification == null) continue
                if (notification.id == notificationId) {
                    return notification.notification
                }
            }
            return null
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
                        val builder = NotificationCompat.Builder(context, activeNotification)
                        activeStyle.addMessage(message)
                        builder.setStyle(activeStyle)
                        return builder
                    }
                }
                null
            } catch (e: Exception) {
                logger.e(e.localizedMessage, e)
                null
            }
        }

        private fun doNotifyPushMessage(context: Context, container: XmPushActionContainer, decryptedContent: ByteArray) {
            val metaInfo = container.metaInfo
            logPushMessage(metaInfo)
            val result = getNotificationFor(context, container, decryptedContent)
            NotificationController.publish(
                context,
                metaInfo,
                result.notificationId,
                container.packageName,
                result.notificationBuilder
            )
        }

        private fun logPushMessage(metaInfo: PushMetaInfo) {
            logger.i("title:${metaInfo.title}  description:${metaInfo.description}")
        }

        @NonNull
        private fun getNotificationFor(
            context: Context,
            container: XmPushActionContainer,
            decryptedContent: ByteArray
        ): NotificationInfo {
            val metaInfo = container.metaInfo
            val packageName = container.packageName

            val pkgCtx = getPackageContext(context, packageName)
            val message = createMessage(context, container, pkgCtx)
            val custom = XMPushUtils.getConfiguration(metaInfo)
            val useMessagingStyle = message != null && custom.useMessagingStyle(false)

            val notificationId = getNotificationId(container)
            val notificationBuilder = if (useMessagingStyle) {
                messagingStyleNotificationBuilder(context, container, notificationId, message, pkgCtx)
            } else {
                normalStyleNotificationBuilder(context, container.metaInfo)
            }

            if (metaInfo.extra != null) {
                setNotificationStyleAction(notificationBuilder, context, packageName, metaInfo.extra)
            }
            addDebugAction(context, container, decryptedContent, metaInfo, packageName, notificationBuilder)

            notificationBuilder.setWhen(metaInfo.messageTs)
            notificationBuilder.setShowWhen(true)
            val group = getGroupName(context, container)
            notificationBuilder.setGroup(group)

            val intentExtra = Intent()
            intentExtra.putExtra(Constants.INTENT_NOTIFICATION_ID, notificationId)
            intentExtra.putExtra(Constants.INTENT_NOTIFICATION_GROUP, notificationBuilder.build().group)

            val localPendingIntent = getClickedPendingIntent(
                context,
                container,
                decryptedContent,
                notificationId,
                intentExtra.extras
            )

            if (localPendingIntent != null) {
                notificationBuilder.setContentIntent(localPendingIntent)
                carryPendingIntentForTemporarilyWhitelisted(context, container, notificationBuilder)
            }
            return NotificationInfo(notificationId, notificationBuilder)
        }

        private fun getPackageContext(context: Context, packageName: String): Context {
            var pkgCtx = context
            if (NotificationManagerEx.isHooked) {
                try {
                    pkgCtx = context.createPackageContext(packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    logger.e(e.message, e)
                }
            }
            return pkgCtx
        }

        @NonNull
        private fun normalStyleNotificationBuilder(
            context: Context,
            metaInfo: PushMetaInfo
        ): NotificationCompat.Builder {
            val title = metaInfo.title
            val description = metaInfo.description
            val bigPic = getBigPic(context, metaInfo)

            val notificationBuilder = NotificationCompat.Builder(context)
            if (bigPic != null) {
                val style = NotificationCompat.BigPictureStyle()
                style.bigPicture(bigPic)
                style.setBigContentTitle(title)
                notificationBuilder.setStyle(style)
            } else if (description.length > NOTIFICATION_BIG_STYLE_MIN_LEN) {
                val style = NotificationCompat.BigTextStyle()
                style.bigText(description)
                style.setBigContentTitle(title)
                notificationBuilder.setStyle(style)
            }

            val titleAndDesp = determineTitleAndDespByDIP(context, metaInfo)
            notificationBuilder.setContentTitle(titleAndDesp[0])
            notificationBuilder.setContentText(titleAndDesp[1])
            return notificationBuilder
        }

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

        @NonNull
        private fun messagingStyleNotificationBuilder(
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

        @NonNull
        private fun createMessageStyleNotificationBuilder(
            context: Context,
            container: XmPushActionContainer,
            message: NotificationCompat.MessagingStyle.Message,
            pkgCtx: Context,
            packageName: String
        ): NotificationCompat.Builder {
            val metaInfo = container.metaInfo
            val group = getGroupFor(context, metaInfo).build()
            val notificationBuilder = NotificationCompat.Builder(context)
            attachMessagingStyle(message, group, metaInfo, notificationBuilder)
            addShortcutToEnableMessagingStyle(context, container, pkgCtx, packageName, group, notificationBuilder)
            return notificationBuilder
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
                val intent = getIntentForMessagingStyle(context, container, packageName)
                val shortcut = ShortcutInfoCompat.Builder(pkgCtx, key)
                    .setIntent(intent ?: return)
                    .setLongLived(true)
                    .setShortLabel(group.name ?: key)
                    .setIcon(group.icon)
                    .build()
                ShortcutManagerCompat.pushDynamicShortcut(pkgCtx, shortcut)
                notificationBuilder.setShortcutInfo(shortcut)
            } catch (_: Throwable) {
            }
        }

        @Nullable
        private fun getIntentForMessagingStyle(
            context: Context,
            container: XmPushActionContainer,
            packageName: String
        ): Intent? {
            var intent = getSdkIntent(context, container)
            if (intent == null) {
                intent = context.packageManager.getLaunchIntentForPackage(packageName)
            }
            return intent
        }

        @Nullable
        private fun createMessage(
            context: Context,
            container: XmPushActionContainer,
            pkgCtx: Context
        ): NotificationCompat.MessagingStyle.Message? {
            val metaInfo = container.metaInfo
            val custom = XMPushUtils.getConfiguration(metaInfo)
            val senderMessage = custom.conversationMessage(null)
            if (senderMessage == null) {
                return null
            }
            return createMessage(context, pkgCtx, metaInfo, senderMessage)
        }

        @NonNull
        private fun createMessage(
            context: Context,
            pkgCtx: Context,
            metaInfo: PushMetaInfo,
            senderMessage: String
        ): NotificationCompat.MessagingStyle.Message {
            val atLeastP = pkgCtx.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.P
            var person: Person? = null
            if (isGroupConversation(metaInfo) || atLeastP) {
                person = getPerson(context, metaInfo).build()
            }
            return NotificationCompat.MessagingStyle.Message(senderMessage, metaInfo.messageTs, person)
        }

        private fun isGroupConversation(metaInfo: PushMetaInfo): Boolean {
            val custom = XMPushUtils.getConfiguration(metaInfo)
            return custom.conversationTitle(null) != null
        }

        @NonNull
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

        @NonNull
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

        private fun carryPendingIntentForTemporarilyWhitelisted(
            xmPushService: Context,
            buildContainer: XmPushActionContainer,
            localBuilder: NotificationCompat.Builder
        ) {
            val metaInfo = buildContainer.metaInfo
            val targetIntent = buildTargetIntentWithoutExtras(buildContainer.packageName, metaInfo)
            val pi = PendingIntent.getService(
                xmPushService,
                0,
                targetIntent,
                FLAG_IMMUTABLE_UPDATE_CURRENT
            )
            localBuilder.extras.putParcelable("mipush.target", pi)
        }

        @JvmStatic
        fun getNotificationId(container: XmPushActionContainer): Int {
            val metaInfo = container.metaInfo
            val id = if (metaInfo.isSetNotifyId()) metaInfo.notifyId.toString() else metaInfo.id
            val idWithPackage = MIPushNotificationHelper.getTargetPackage(container) + "_" + id
            return idWithPackage.hashCode()
        }

        @JvmStatic
        fun getNotificationTag(packageName: String): String {
            return "mipush_$packageName"
        }

        @JvmStatic
        fun getNotificationTag(container: XmPushActionContainer): String {
            return getNotificationTag(container.packageName)
        }

        private fun getGroupName(xmPushService: Context, buildContainer: XmPushActionContainer): String {
            val metaInfo = buildContainer.metaInfo
            val packageName = buildContainer.packageName
            RegisteredApplicationDb.getRegisteredApplication(packageName)

            val configuration = XMPushUtils.getConfiguration(metaInfo)
            var group = configuration.notificationGroup(null)
            group = if (group != null) {
                "${packageName}_${GROUP_TYPE_MIPUSH_GROUP}_$group"
            } else if (metaInfo.passThrough == 1) {
                "${packageName}_${GROUP_TYPE_PASS_THROUGH}"
            } else {
                packageName
            }
            return group
        }

        private fun addDebugAction(
            xmPushService: Context,
            buildContainer: XmPushActionContainer,
            payload: ByteArray,
            metaInfo: PushMetaInfo,
            packageName: String,
            localBuilder: NotificationCompat.Builder
        ) {
            if (Global.ConfigCenter().isDebugMode) {
                val icon = R.drawable.ic_notifications_black_24dp
                val pendingIntentOpenActivity =
                    openActivityPendingIntent(xmPushService, buildContainer, metaInfo, payload)
                if (pendingIntentOpenActivity != null) {
                    localBuilder.addAction(NotificationCompat.Action(icon, "Open App", pendingIntentOpenActivity))
                }

                val pendingIntentJump =
                    startServicePendingIntent(xmPushService, buildContainer, metaInfo, payload)
                if (pendingIntentJump != null) {
                    localBuilder.addAction(NotificationCompat.Action(icon, "Jump", pendingIntentJump))
                }

                val sdkIntentJump = getSdkIntent(xmPushService, buildContainer)
                if (sdkIntentJump != null) {
                    val pendingIntent = PendingIntent.getActivity(
                        xmPushService,
                        0,
                        sdkIntentJump,
                        FLAG_IMMUTABLE_UPDATE_CURRENT
                    )
                    localBuilder.addAction(NotificationCompat.Action(icon, "SDK Intent", pendingIntent))
                }
            }
        }

        @JvmStatic
        fun buildTargetIntentWithoutExtras(pkg: String, metaInfo: PushMetaInfo): Intent {
            val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE)
                .addCategory(metaInfo.notifyId.toString())
                .setClassName(pkg, CLASS_NAME_PUSH_MESSAGE_HANDLER)
            ExplicitHookBridge.onBuildIntent(intent, "MyMIPushNotificationHelper.buildTargetIntentWithoutExtras")
            return intent
        }

        private fun openActivityPendingIntent(
            context: Context,
            container: XmPushActionContainer,
            metaInfo: PushMetaInfo,
            payload: ByteArray
        ): PendingIntent? {
            val localIntent = context.packageManager.getLaunchIntentForPackage(container.packageName)
            return if (localIntent != null) {
                localIntent.addCategory(metaInfo.notifyId.toString())
                PendingIntent.getActivity(context, 0, localIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
            } else {
                null
            }
        }

        private fun getClickedPendingIntent(
            context: Context,
            container: XmPushActionContainer,
            decryptedContent: ByteArray,
            notificationId: Int,
            extra: Bundle?
        ): PendingIntent? {
            val metaInfo = container.metaInfo ?: return null

            var urlJump: String? = null
            if (!TextUtils.isEmpty(metaInfo.url)) {
                urlJump = metaInfo.url
            } else if (metaInfo.extra != null) {
                urlJump = metaInfo.extra[PushConstants.EXTRA_PARAM_WEB_URI]
            }

            if (!TextUtils.isEmpty(urlJump)) {
                val intent = Intent("android.intent.action.VIEW")
                intent.data = Uri.parse(urlJump)
                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                return PendingIntent.getActivity(
                    context,
                    notificationId,
                    intent,
                    FLAG_IMMUTABLE_UPDATE_CURRENT
                )
            }

            val intent = Intent()
            intent.component = ComponentName(
                "com.xiaomi.xmsf",
                if (MIPushNotificationHelper.isBusinessMessage(container)) {
                    "com.xiaomi.mipush.sdk.PushMessageHandler"
                } else {
                    "com.xiaomi.push.sdk.MyPushMessageHandler"
                }
            )
            intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, decryptedContent)
            intent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
            if (extra != null) {
                intent.putExtras(extra)
            }
            intent.addCategory(metaInfo.notifyId.toString())

            val configuration = XMPushUtils.getConfiguration(metaInfo)
            val useActivity = configuration.useClickedActivity(false)
            val activityIntent = getSdkIntent(context, container)
            if (!useActivity || activityIntent == null) {
                return PendingIntent.getService(
                    context,
                    notificationId,
                    intent,
                    FLAG_IMMUTABLE_UPDATE_CURRENT
                )
            }
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activityIntent.putExtra("mipush_serviceIntent", intent)
            activityIntent.putExtras(intent)
            return PendingIntent.getActivity(
                context,
                notificationId,
                activityIntent,
                FLAG_IMMUTABLE_UPDATE_CURRENT
            )
        }

        /**
         * @see PushMessageProcessor#getNotificationMessageIntent
         */
        @JvmStatic
        fun getSdkIntent(context: Context, container: XmPushActionContainer): Intent? {
            val pkgName = container.packageName
            val paramPushMetaInfo = container.metaInfo
            val extra = paramPushMetaInfo.extra ?: return null
            if (!extra.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)) {
                return null
            }

            var intent: Intent? = null
            val typeId = extra[PushConstants.EXTRA_PARAM_NOTIFY_EFFECT]
            if (PushConstants.NOTIFICATION_CLICK_DEFAULT == typeId) {
                try {
                    intent = context.packageManager.getLaunchIntentForPackage(pkgName)
                } catch (e: Exception) {
                    logger.e("Cause: ${e.message}")
                }
            } else if (PushConstants.NOTIFICATION_CLICK_INTENT == typeId) {
                if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_URI)) {
                    val intentStr = extra[PushConstants.EXTRA_PARAM_INTENT_URI]
                    if (intentStr != null) {
                        try {
                            intent = Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME)
                            intent.setPackage(pkgName)
                        } catch (e: URISyntaxException) {
                            logger.e("Cause: ${e.message}")
                        }
                    }
                } else if (extra.containsKey(PushConstants.EXTRA_PARAM_CLASS_NAME)) {
                    val className = extra[PushConstants.EXTRA_PARAM_CLASS_NAME] ?: return null
                    intent = Intent()
                    intent.component = ComponentName(pkgName, className)
                    try {
                        if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_FLAG)) {
                            intent.flags = extra[PushConstants.EXTRA_PARAM_INTENT_FLAG]!!.toInt()
                        }
                    } catch (e: NumberFormatException) {
                        logger.e("Cause by intent_flag: ${e.message}")
                    }
                }
            } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE == typeId) {
                val uri = extra[PushConstants.EXTRA_PARAM_WEB_URI]
                if (uri != null) {
                    var tmp = uri.trim()
                    if (!(tmp.startsWith("http://") || tmp.startsWith("https://"))) {
                        tmp = "http://$tmp"
                    }
                    try {
                        val protocol = URL(tmp).protocol
                        if ("http" == protocol || "https" == protocol) {
                            val intent2 = Intent("android.intent.action.VIEW")
                            intent2.data = Uri.parse(tmp)
                            intent = intent2
                        }
                    } catch (e: MalformedURLException) {
                        logger.e("Cause: ${e.message}")
                        return null
                    }
                }
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val available = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
                ExplicitHookBridge.onIntentAvailabilityChecked(
                    intent,
                    available,
                    "MyMIPushNotificationHelper.getSdkIntent"
                )
                if (available) {
                    if (inFetchIntentBlackList(pkgName)) {
                        return null
                    }
                    return intent
                }
            }
            return null
        }

        private fun inFetchIntentBlackList(pkg: String): Boolean {
            return pkg.contains("youku")
        }

        private fun startServicePendingIntent(
            context: Context,
            container: XmPushActionContainer,
            pushMetaInfo: PushMetaInfo?,
            payload: ByteArray
        ): PendingIntent? {
            if (pushMetaInfo == null) {
                return null
            }
            val localIntent = if (MIPushNotificationHelper.isBusinessMessage(container)) {
                Intent().setComponent(
                    ComponentName("com.xiaomi.xmsf", "com.xiaomi.mipush.sdk.PushMessageHandler")
                )
            } else {
                Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).setComponent(
                    ComponentName(container.packageName, "com.xiaomi.mipush.sdk.PushMessageHandler")
                )
            }
            localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
            localIntent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
            localIntent.addCategory(pushMetaInfo.notifyId.toString())
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, 0, localIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
            } else {
                PendingIntent.getService(context, 0, localIntent, FLAG_IMMUTABLE_UPDATE_CURRENT)
            }
        }

        private fun determineTitleAndDespByDIP(
            context: Context,
            pushMetaInfo: PushMetaInfo
        ): Array<String> {
            return try {
                JavaCalls.callStaticMethodOrThrow(
                    MIPushNotificationHelper::class.java,
                    "determineTitleAndDespByDIP",
                    context,
                    pushMetaInfo
                )
            } catch (e: Exception) {
                logger.e(e.message, e)
                arrayOf(pushMetaInfo.title, pushMetaInfo.description)
            }
        }

        // from sdk 3.7.2
        @TargetApi(16)
        private fun setNotificationStyleAction(
            builder: NotificationCompat.Builder,
            context: Context,
            pkgName: String,
            metaExtra: Map<String, String>
        ): NotificationCompat.Builder {
            val left = getStylePendingIntent(context, pkgName, 1, metaExtra)
            if (left != null && !TextUtils.isEmpty(metaExtra[NOTIFICATION_STYLE_BUTTON_LEFT_NAME])) {
                builder.addAction(0, metaExtra[NOTIFICATION_STYLE_BUTTON_LEFT_NAME], left)
            }
            val mid = getStylePendingIntent(context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_MID, metaExtra)
            if (mid != null && !TextUtils.isEmpty(metaExtra[NOTIFICATION_STYLE_BUTTON_MID_NAME])) {
                builder.addAction(0, metaExtra[NOTIFICATION_STYLE_BUTTON_MID_NAME], mid)
            }
            val right = getStylePendingIntent(context, pkgName, NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT, metaExtra)
            if (right != null && !TextUtils.isEmpty(metaExtra[NOTIFICATION_STYLE_BUTTON_RIGHT_NAME])) {
                builder.addAction(0, metaExtra[NOTIFICATION_STYLE_BUTTON_RIGHT_NAME], right)
            }
            return builder
        }

        private fun getStylePendingIntent(
            context: Context,
            pkgName: String,
            place: Int,
            metaExtra: Map<String, String>?
        ): PendingIntent? {
            val intent = if (metaExtra == null) null else getPendingIntentFromExtra(context, pkgName, place, metaExtra)
            return if (intent == null) null else PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun getPendingIntentFromExtra(
            context: Context,
            pkgName: String,
            place: Int,
            extra: Map<String, String>
        ): Intent? {
            val str = if (place < NOTIFICATION_ACTION_BUTTON_PLACE_MID) {
                NOTIFICATION_STYLE_BUTTON_LEFT_NOTIFY_EFFECT
            } else if (place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT) {
                NOTIFICATION_STYLE_BUTTON_MID_NOTIFY_EFFECT
            } else {
                NOTIFICATION_STYLE_BUTTON_RIGHT_NOTIFY_EFFECT
            }
            val typeId = extra[str]
            if (TextUtils.isEmpty(typeId)) {
                return null
            }

            var intent: Intent? = null
            if (PushConstants.NOTIFICATION_CLICK_DEFAULT == typeId) {
                try {
                    intent = context.packageManager.getLaunchIntentForPackage(pkgName)
                } catch (e: Exception) {
                    logger.e("Cause: ${e.message}")
                }
            } else if (PushConstants.NOTIFICATION_CLICK_INTENT == typeId) {
                val intentUriKey = if (place < NOTIFICATION_ACTION_BUTTON_PLACE_MID) {
                    NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_URI
                } else if (place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT) {
                    NOTIFICATION_STYLE_BUTTON_MID_INTENT_URI
                } else {
                    NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_URI
                }
                val intentClassKey = if (place < NOTIFICATION_ACTION_BUTTON_PLACE_MID) {
                    NOTIFICATION_STYLE_BUTTON_LEFT_INTENT_CLASS
                } else if (place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT) {
                    NOTIFICATION_STYLE_BUTTON_MID_INTENT_CLASS
                } else {
                    NOTIFICATION_STYLE_BUTTON_RIGHT_INTENT_CLASS
                }
                if (extra.containsKey(intentUriKey)) {
                    val intentStr = extra[intentUriKey]
                    if (intentStr != null) {
                        try {
                            intent = Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME)
                            intent.setPackage(pkgName)
                        } catch (e: URISyntaxException) {
                            logger.e("Cause: ${e.message}")
                        }
                    }
                } else if (extra.containsKey(intentClassKey)) {
                    val className = extra[intentClassKey] ?: return null
                    intent = Intent()
                    intent.component = ComponentName(pkgName, className)
                }
            } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE == typeId) {
                val webUriKey = if (place < NOTIFICATION_ACTION_BUTTON_PLACE_MID) {
                    NOTIFICATION_STYLE_BUTTON_LEFT_WEB_URI
                } else if (place < NOTIFICATION_ACTION_BUTTON_PLACE_RIGHT) {
                    NOTIFICATION_STYLE_BUTTON_MID_WEB_URI
                } else {
                    NOTIFICATION_STYLE_BUTTON_RIGHT_WEB_URI
                }
                val uri = extra[webUriKey]
                if (!TextUtils.isEmpty(uri)) {
                    var tmp = uri!!.trim()
                    if (!tmp.startsWith("http://") && !tmp.startsWith("https://")) {
                        tmp = "http://$tmp"
                    }
                    try {
                        val protocol = URL(tmp).protocol
                        if ("http" == protocol || "https" == protocol) {
                            intent = Intent("android.intent.action.VIEW")
                            intent.data = Uri.parse(tmp)
                        }
                    } catch (e: MalformedURLException) {
                        logger.e("Cause: ${e.message}")
                    }
                }
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    val rinfo: ResolveInfo? =
                        context.packageManager.resolveActivity(intent, Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    ExplicitHookBridge.onIntentAvailabilityChecked(
                        intent,
                        rinfo != null,
                        "MyMIPushNotificationHelper.getPendingIntentFromExtra"
                    )
                    if (rinfo != null) {
                        return intent
                    }
                } catch (e: Exception) {
                    logger.e("Cause: ${e.message}")
                }
            }
            return null
        }
    }
}
