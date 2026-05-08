package io.github.magisk317.mipush.service.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.core.app.NotificationCompat
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.push.hook.ExplicitHookBridge
import io.github.magisk317.mipush.push.hook.HookTraceCompat
import io.github.magisk317.mipush.push.pipeline.MessageIdentity
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.mipush.sdk.PushMessageProcessor
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.R
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.IconConfigurations
import io.github.magisk317.mipush.utils.PackageConfig
import io.github.magisk317.mipush.app.ConfigCenter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb

class MyMIPushNotificationHelper {
    private class NotificationInfo(
        val notificationId: Int,
        val notificationBuilder: NotificationCompat.Builder
    )

    companion object {
        private const val TAG = "MyNotificationHelper"
        private val logger = object {
            fun i(msg: String) = Napier.i(msg, tag = TAG)
            fun w(msg: String) = Napier.w(msg, tag = TAG)
            fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG)
        }

        const val CLASS_NAME_PUSH_MESSAGE_HANDLER = Constants.PUSH_MESSAGE_HANDLER_CLASS
        private const val GROUP_TYPE_MIPUSH_GROUP = "#group#"
        private const val GROUP_TYPE_PASS_THROUGH = "#pass_through#"

        @Volatile
        private var notificationSessionStartedAtMs: Long = System.currentTimeMillis()
        private var tryLoadConfigurations = false
        private val executorService: ExecutorService = Executors.newFixedThreadPool(3)

        @JvmStatic
        fun markNotificationSessionStarted(source: String, nowMs: Long = System.currentTimeMillis()) {
            notificationSessionStartedAtMs = nowMs
            logger.i("notification session started at=$nowMs source=$source")
        }

        @JvmStatic
        fun notifyPushMessage(context: Context, decryptedContent: ByteArray) {
            val container = XMPushUtils.packToContainer(decryptedContent) ?: return
            val messageId = MessageIdentity.fromContainer(container)
            val isMockReplay = MockMessageRegistry.isMarked(container)
            logger.i(
                "notifyPushMessage start pkg=${container.packageName} action=${container.action} " +
                    "messageId=$messageId payloadSize=${decryptedContent.size} mockReplay=$isMockReplay " +
                    "moduleEnhanced=${io.github.magisk317.mipush.notification.NotificationManagerEx.isHooked}"
            )
            if (!shouldPublishNotification(container)) {
                logger.i("skip non-display notification action=${container.action} pkg=${container.packageName}")
                return
            }
            if (RegisteredApplicationDb.isBlocked(container.packageName)) {
                logger.i("skip blocked application pkg=${container.packageName} action=${container.action}")
                return
            }
            if (!isMockReplay && shouldDropReplayNotification(container)) {
                val messageTs = container.metaInfo?.messageTs ?: 0L
                val sessionStartedAtMs = notificationSessionStartedAtMs
                logger.i(
                    "skip replay notification publish pkg=${container.packageName} action=${container.action} " +
                        "messageId=$messageId messageTs=$messageTs " +
                        "sessionStartedAtMs=$sessionStartedAtMs"
                )
                PushRuntime.observeNotificationEvent(
                    packageName = container.packageName,
                    action = "replay_notification_drop",
                    source = "MyMIPushNotificationHelper.notifyPushMessage"
                )
                return
            }
            HookTraceCompat.notifyPushMessage(container, decryptedContent)
            if (!MiPushRuntimeBridge.onNotificationDispatch(context, container, decryptedContent)) {
                logger.i(
                    "skip duplicate notification publish action=${container.action} pkg=${container.packageName} " +
                        "messageId=$messageId mockReplay=$isMockReplay"
                )
                return
            }
            val notificationOp = AppInfoUtils.getAppNotificationOp(
                context,
                MIPushNotificationHelper.getTargetPackage(container),
                true
            )
            if (notificationOp == AppInfoUtils.AppNotificationOp.NOT_ALLOWED) {
                logger.w(
                    "Do not notify because user block " +
                        MIPushNotificationHelper.getTargetPackage(container) +
                        "'s notification messageId=$messageId mockReplay=$isMockReplay"
                )
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
                val messageId = MessageIdentity.fromContainer(container)
                val operations = Configurations.getInstance().handle(packageName, container)
                logger.i(
                    "handleNotificationByConfigurations pkg=$packageName action=${container.action} " +
                        "messageId=$messageId operations=$operations"
                )
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
                            logger.i(
                                "policy_notify dispatch start pkg=$packageName action=${container.action} " +
                                    "messageId=$messageId"
                            )
                            doNotifyPushMessage(context, container, decryptedContent)
                        } catch (e: Exception) {
                            logger.e(
                                "policy_notify dispatch failed pkg=$packageName action=${container.action} " +
                                    "messageId=$messageId",
                                e
                            )
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
                            logger.e("Failed to dispatch downstream payload", e)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("handleNotificationByConfigurations encountered error", e)
            }
        }

        private fun loadConfigurationsOnce(context: Context) {
            if (!tryLoadConfigurations) {
                tryLoadConfigurations = true
                try {
                    val configCenter: ConfigCenter = Global.configCenter()
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

        internal fun shouldDropReplayNotification(
            container: XmPushActionContainer,
            sessionStartedAtMs: Long = notificationSessionStartedAtMs
        ): Boolean {
            if (container.action != ActionType.SendMessage) {
                return false
            }
            val metaInfo = container.metaInfo ?: return false
            if (!metaInfo.isSetMessageTs()) {
                return false
            }
            val messageTs = metaInfo.messageTs
            if (messageTs <= 0L || sessionStartedAtMs <= 0L) {
                return false
            }
            return messageTs < sessionStartedAtMs
        }

        private fun loadConfigurations(context: Context, configurationDirectory: Uri?) {
            val configurations = Configurations.getInstance()
            if (configurations.init(context, configurationDirectory)) {
                val iconConfigurations: IconConfigurations = Global.iconConfigurations()
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
            val messageId = MessageIdentity.fromContainer(container)
            if (metaInfo == null) {
                logger.w("doNotifyPushMessage: metaInfo is null, skip notification pkg=${container.packageName} messageId=$messageId")
                return
            }
            logger.i("title:${metaInfo.title}  description:${metaInfo.description}")
            val result = getNotificationFor(context, container, decryptedContent)
            logger.i(
                "doNotifyPushMessage publish start pkg=${container.packageName} action=${container.action} " +
                    "messageId=$messageId notificationId=${result.notificationId}"
            )
            NotificationController.publish(
                context,
                metaInfo,
                result.notificationId,
                container.packageName,
                result.notificationBuilder
            )
        }

        @NonNull
        private fun getNotificationFor(
            context: Context,
            container: XmPushActionContainer,
            decryptedContent: ByteArray
        ): NotificationInfo {
            val metaInfo = container.metaInfo
            val packageName = container.packageName

            val pkgCtx = XMPushUtils.getPackageContext(context, packageName)
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
                MyMIPushNotificationStyleSupport.normalStyleNotificationBuilder(pkgCtx, container.metaInfo, packageName)
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
            intentExtra.putExtra(Constants.INTENT_NOTIFICATION_GROUP, group)

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
            if (runBlocking { Global.configCenter().isDebugModeAsync() }) {
                val icon = R.drawable.ic_notifications_black_24dp
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

        @JvmStatic
        fun getSdkIntent(context: Context, container: XmPushActionContainer): Intent? {
            return MyMIPushNotificationIntentSupport.getSdkIntent(context, container)
        }
    }
}
