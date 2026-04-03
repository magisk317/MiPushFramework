package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.core.app.NotificationCompat
import com.magisk317.push.hook.ExplicitHookBridge
import com.magisk317.push.hook.HookTraceCompat
import com.magisk317.push.pipeline.MessageIdentity
import com.magisk317.push.pipeline.MiPushRuntimeBridge
import com.magisk317.Global
import com.magisk317.XMPushUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.mipush.sdk.PushMessageProcessor
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.runtime.PushRuntime
import com.xiaomi.xmsf.push.notification.NotificationController
import com.xiaomi.xmsf.push.utils.Configurations
import com.xiaomi.xmsf.push.utils.IconConfigurations
import com.xiaomi.xmsf.push.utils.PackageConfig
import com.xiaomi.xmsf.utils.ConfigCenter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
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

        private val logger = MyMIPushNotificationLogs.logger

        private const val GROUP_TYPE_MIPUSH_GROUP = "#group#"
        private const val GROUP_TYPE_PASS_THROUGH = "#pass_through#"

        private var tryLoadConfigurations = false
        private val executorService: ExecutorService = Executors.newFixedThreadPool(3)

        @JvmStatic
        fun notifyPushMessage(context: Context, decryptedContent: ByteArray) {
            val container = XMPushUtils.packToContainer(decryptedContent) ?: return
            if (!shouldPublishNotification(container)) {
                logger.i("skip non-display notification action=${container.action} pkg=${container.packageName}")
                return
            }
            HookTraceCompat.notifyPushMessage(container, decryptedContent)
            if (!MiPushRuntimeBridge.onNotificationDispatch(context, container, decryptedContent)) {
                logger.i("skip duplicate notification publish action=${container.action} pkg=${container.packageName}")
                return
            }
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
                    PushRuntime.observeNotificationEvent(
                        packageName = packageName,
                        action = "policy_wake",
                        source = "MyMIPushNotificationHelper.handleNotificationByConfigurations"
                    )
                    wakeScreen(context, packageName)
                }
                if (!operations.contains(PackageConfig.OPERATION_IGNORE)) {
                    PushRuntime.observeNotificationEvent(
                        packageName = packageName,
                        action = "policy_notify",
                        source = "MyMIPushNotificationHelper.handleNotificationByConfigurations"
                    )
                    executorService.execute {
                        try {
                            doNotifyPushMessage(context, container, decryptedContent)
                        } catch (e: Exception) {
                            logger.e(e.localizedMessage, e)
                        }
                    }
                } else {
                    PushRuntime.observeNotificationEvent(
                        packageName = packageName,
                        action = "policy_ignore",
                        source = "MyMIPushNotificationHelper.handleNotificationByConfigurations"
                    )
                }
                if (operations.contains(PackageConfig.OPERATION_OPEN)) {
                    PushRuntime.observeNotificationEvent(
                        packageName = packageName,
                        action = "policy_open",
                        source = "MyMIPushNotificationHelper.handleNotificationByConfigurations"
                    )
                    executorService.execute {
                        try {
                            PushRuntime.dispatchDownstreamPayload(
                                packageName = packageName,
                                action = container.action?.name ?: "Unknown",
                                messageId = MessageIdentity.fromContainer(container),
                                payload = decryptedContent,
                                source = "MyMIPushNotificationHelper.policy_open",
                                launchApp = true
                            )
                        } catch (e: Exception) {
                            logger.e(e.localizedMessage, e)
                        }
                    }
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
                    val configurationDirectory = runBlocking { configCenter.getConfigurationDirectoryAsync() }
                    loadConfigurations(context, configurationDirectory)
                } catch (e: Exception) {
                    Utils.makeText(context, e.toString(), Toast.LENGTH_LONG)
                }
            }
        }

        internal fun shouldPublishNotification(container: XmPushActionContainer): Boolean {
            return container.action == ActionType.SendMessage
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
                PowerManager.PARTIAL_WAKE_LOCK,
                "xmsf: configurations of $sourcePackage"
            )
            fullWakeLock.acquire(10000)
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

            val pkgCtx = MyMIPushNotificationStyleSupport.getPackageContext(context, packageName)
            val message = MyMIPushNotificationStyleSupport.createMessage(context, container, pkgCtx)
            val custom = XMPushUtils.getConfiguration(metaInfo)
            val useMessagingStyle = message != null && custom.useMessagingStyle(false)

            val notificationId = getNotificationId(container)
            val notificationBuilder = if (useMessagingStyle) {
                MyMIPushNotificationStyleSupport.messagingStyleNotificationBuilder(
                    context,
                    container,
                    notificationId,
                    message,
                    pkgCtx
                )
            } else {
                MyMIPushNotificationStyleSupport.normalStyleNotificationBuilder(context, container.metaInfo)
            }

            if (metaInfo.extra != null) {
                MyMIPushNotificationIntentSupport.addStyleActions(notificationBuilder, context, packageName, metaInfo.extra)
            }
            addDebugAction(context, container, decryptedContent, metaInfo, packageName, notificationBuilder)

            notificationBuilder.setWhen(metaInfo.messageTs)
            notificationBuilder.setShowWhen(true)
            val group = getGroupName(context, container)
            notificationBuilder.setGroup(group)

            val intentExtra = Intent()
            intentExtra.putExtra(Constants.INTENT_NOTIFICATION_ID, notificationId)
            intentExtra.putExtra(Constants.INTENT_NOTIFICATION_GROUP, notificationBuilder.build().group)

            val localPendingIntent = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
                context,
                container,
                decryptedContent,
                notificationId,
                intentExtra.extras
            )

            if (localPendingIntent != null) {
                notificationBuilder.setContentIntent(localPendingIntent)
                MyMIPushNotificationIntentSupport.carryPendingIntentForTemporarilyWhitelisted(
                    context,
                    container,
                    notificationBuilder
                )
            }
            return NotificationInfo(notificationId, notificationBuilder)
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
            if (runBlocking { Global.ConfigCenter().isDebugModeAsync() }) {
                val icon = R.drawable.ic_notifications_black_24dp
                val pendingIntentOpenActivity =
                    MyMIPushNotificationIntentSupport.openActivityPendingIntent(
                        xmPushService,
                        buildContainer,
                        metaInfo
                    )
                if (pendingIntentOpenActivity != null) {
                    localBuilder.addAction(NotificationCompat.Action(icon, "Open App", pendingIntentOpenActivity))
                }

                val pendingIntentJump =
                    MyMIPushNotificationIntentSupport.startServicePendingIntent(
                        xmPushService,
                        buildContainer,
                        metaInfo,
                        payload
                    )
                if (pendingIntentJump != null) {
                    localBuilder.addAction(NotificationCompat.Action(icon, "Jump", pendingIntentJump))
                }

                val sdkIntentJump = MyMIPushNotificationIntentSupport.getSdkIntent(xmPushService, buildContainer)
                if (sdkIntentJump != null) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        xmPushService,
                        0,
                        sdkIntentJump,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
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

        /**
         * @see PushMessageProcessor#getNotificationMessageIntent
         */
        @JvmStatic
        fun getSdkIntent(context: Context, container: XmPushActionContainer): Intent? {
            return MyMIPushNotificationIntentSupport.getSdkIntent(context, container)
        }
    }
}
