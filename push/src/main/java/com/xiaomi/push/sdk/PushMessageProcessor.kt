package com.xiaomi.push.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.magisk317.Global
import com.magisk317.push.pipeline.MiPushRuntimeBridge
import com.magisk317.XMPushUtils
import com.topjohnwu.superuser.Shell
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.MyMIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.push.notification.NotificationController
import com.xiaomi.xmsf.push.utils.Configurations
import com.xiaomi.xmsf.utils.ConfigCenter
import top.trumeet.common.Constants
import top.trumeet.common.ita.AccessMode
import top.trumeet.common.ita.ITopActivity
import top.trumeet.common.ita.TopActivityFactory
import top.trumeet.common.utils.Utils
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class PushMessageProcessor @Inject constructor(
    private val configCenter: ConfigCenter,
    private val configurations: Configurations
) {
    // No-arg fallback for legacy Singleton access.
    constructor() : this(
        com.magisk317.utils.Singleton.instance<ConfigCenter>(),
        Configurations.getInstance()
    )

    init {
        try {
            com.magisk317.utils.Singleton.reset(this)
        } catch (_: Throwable) {}
    }

    private val TAG = "PushMessageProcessor"
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
        fun i(msg: String) = Napier.i(msg, tag = TAG)
        fun w(msg: String) = Napier.w(msg, tag = TAG)
        fun e(msg: String) = Napier.e(msg, tag = TAG)
        fun e(msg: String, t: Throwable) = Napier.e(msg, t, tag = TAG)
    }
    

    private val APP_CHECK_FRONT_MAX_RETRY = 8
    private val APP_CHECK_SLEEP_DURATION_MS = 500L
    private val APP_CHECK_SLEEP_MAX_TIMEOUT_MS =
        APP_CHECK_FRONT_MAX_RETRY * APP_CHECK_SLEEP_DURATION_MS

    var iTopActivity: ITopActivity? = null
    private var topActivityMode: Int? = null

    fun resetTopActivityCache() {
        iTopActivity = null
        topActivityMode = null
    }

    private fun resolveTopActivity(context: Context): ITopActivity {
        val configuredMode = runBlocking { configCenter.getAccessModeAsync() }
        if (iTopActivity == null || topActivityMode != configuredMode) {
            iTopActivity = TopActivityFactory.newInstance(configuredMode)
            topActivityMode = configuredMode
        }
        val selected = iTopActivity ?: TopActivityFactory.newInstance(configuredMode)
        if (configuredMode == AccessMode.USAGE_STATS && !selected.isEnabled(context)) {
            val accessibility = TopActivityFactory.newInstance(AccessMode.ACCESSIBILITY)
            if (accessibility.isEnabled(context)) {
                logger.w("UsageStats unavailable, fallback to Accessibility mode.")
                return accessibility
            }
        }
        return selected
    }

    fun cancelNotification(context: Context, bundle: Bundle) {
        val payload = bundle.getByteArray(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        if (payload == null) {
            logger.e("mipush_payload is null")
            return
        }
        val container = XMPushUtils.packToContainer(payload) ?: return
        cancelNotification(context, bundle, container)
    }

    fun cancelNotification(context: Context, bundle: Bundle, container: XmPushActionContainer) {
        val notificationId = bundle.getInt(Constants.INTENT_NOTIFICATION_ID, 0)
        val notificationGroup = bundle.getString(Constants.INTENT_NOTIFICATION_GROUP)
        try {
            configurations.handle(container.packageName, container)
        } catch (e: Exception) {
            logger.e("cancelNotification", e)
        }
        val custom = XMPushUtils.getConfiguration(container)
        NotificationController.cancel(
            context,
            container,
            notificationId,
            notificationGroup,
            custom.clearGroup(false)
        )
    }

    fun launchApp(context: Context, container: XmPushActionContainer) {
        val topActivity = resolveTopActivity(context)
        if (!topActivity.isEnabled(context)) {
            topActivity.guideToEnable(context)
            return
        }
        val targetPackage = container.packageName
        activeApp(context, targetPackage)
        pullUpApp(context, targetPackage, container)
    }

    fun startService(
        context: Context,
        container: XmPushActionContainer,
        payload: ByteArray
    ): ComponentName? {
        launchApp(context, container)
        return forwardToTargetApplication(context, payload)
    }

    fun forwardToTargetApplication(context: Context, payload: ByteArray): ComponentName? {
        val container = XMPushUtils.packToContainer(payload) ?: return null
        val metaInfo = container.metaInfo ?: return null
        val targetPackage = container.packageName

        MiPushRuntimeBridge.onTransferToApplication(container)
        val localIntent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE)
        localIntent.component = ComponentName(targetPackage, "com.xiaomi.mipush.sdk.PushMessageHandler")
        localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        localIntent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
        localIntent.addCategory(metaInfo.notifyId.toString())
        logger.d(packageInfo(targetPackage, "send to service"))
        val started = runCatching { context.startService(localIntent) }
            .onFailure { logger.e(packageInfo(targetPackage, "forward to service failed"), it) }
            .getOrNull()
        if (started != null) {
            return started
        }

        val fallbackSent = sendBroadcastFallback(context, container, payload)
        if (fallbackSent) {
            logger.w(packageInfo(targetPackage, "service unavailable, fallback to broadcast"))
        }
        return null
    }

    private fun activeApp(context: Context, targetPackage: String) {
        Shell.cmd("pm enable $targetPackage").exec()
    }

    private fun getJumpIntent(context: Context, container: XmPushActionContainer): Intent? {
        var intent = MyMIPushNotificationHelper.getSdkIntent(context, container)
        if (intent == null) {
            intent = getJumpIntentFromPkg(context, container.packageName)
        }
        return intent
    }

    private fun getJumpIntentFromPkg(context: Context, targetPackage: String): Intent? {
        var intent: Intent? = null
        try {
            intent = context.packageManager.getLaunchIntentForPackage(targetPackage)
            logger.d(packageInfo(targetPackage, intent.toString()))
        } catch (_: RuntimeException) {
        }
        return intent
    }

    private fun pullUpApp(context: Context, targetPackage: String, container: XmPushActionContainer): Long {
        val start = System.currentTimeMillis()
        try {
            val topActivity = resolveTopActivity(context)
            if (!topActivity.isAppForeground(context, targetPackage)) {
                logger.d(packageInfo(targetPackage, "app is not at front , let's pull up"))
                var intent = getJumpIntent(context, container)
                if (intent == null) {
                    throw RuntimeException("can not get default activity for $targetPackage")
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    context.startActivity(intent)
                    logger.d(packageInfo(targetPackage, "start activity"))
                }
                for (i in 0 until APP_CHECK_FRONT_MAX_RETRY) {
                    if (!topActivity.isAppForeground(context, targetPackage)) {
                        Thread.sleep(APP_CHECK_SLEEP_DURATION_MS)
                    } else {
                        break
                    }
                    if (i == (APP_CHECK_FRONT_MAX_RETRY / 2)) {
                        intent = getJumpIntentFromPkg(context, targetPackage)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        if (intent != null) {
                            context.startActivity(intent)
                        }
                    }
                }
                if ((System.currentTimeMillis() - start) >= APP_CHECK_SLEEP_MAX_TIMEOUT_MS) {
                    logger.w(packageInfo(targetPackage, "pull up app timeout"))
                }
            } else {
                logger.d(packageInfo(targetPackage, "app is at foreground"))
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: RuntimeException) {
            logger.e(packageInfo(targetPackage, "pullUpApp failed ${e.localizedMessage}"), e)
        }
        val end = System.currentTimeMillis()
        return end - start
    }

    private fun packageInfo(packageName: String, message: String): String = "[$packageName] $message"

    private fun sendBroadcastFallback(
        context: Context,
        container: XmPushActionContainer,
        payload: ByteArray
    ): Boolean {
        val targetPackage = container.packageName
        if (targetPackage.isBlank()) return false
        val fallbackIntent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
            `package` = targetPackage
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
            putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
            putExtra(PushConstants.MESSAGE_RECEIVE_TIME, System.currentTimeMillis())
            container.metaInfo?.notifyId?.let { addCategory(it.toString()) }
        }
        return runCatching {
            context.sendBroadcast(fallbackIntent)
            true
        }.onFailure {
            logger.e(packageInfo(targetPackage, "fallback broadcast failed"), it)
        }.getOrDefault(false)
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PushMessageProcessorEntryPoint {
    fun pushMessageProcessor(): PushMessageProcessor
}
