package com.xiaomi.push.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.Global
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.XMPushUtils
import com.topjohnwu.superuser.Shell
import com.xiaomi.push.service.MIPushNotificationHelper
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.push.notification.NotificationController
import com.xiaomi.xmsf.push.utils.Configurations
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.platform.activity.AccessMode
import io.github.magisk317.mipush.platform.activity.ITopActivity
import io.github.magisk317.mipush.platform.activity.TopActivityFactory
import io.github.magisk317.mipush.common.utils.Utils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushMessageProcessor @Inject constructor(
    private val configurations: Configurations
) {
    // No-arg fallback for legacy Singleton access.
    constructor() : this(
        Configurations.getInstance()
    )

    init {
        try {
            io.github.magisk317.mipush.common.utils.Singleton.reset(this)
        } catch (t: Throwable) {
            io.github.aakira.napier.Napier.w("Singleton.reset failed for PushMessageProcessor", t, tag = "PushMessageProcessor")
        }
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

    data class ApplicationDeliveryResult(
        val serviceComponent: ComponentName? = null,
        val deliveredByBroadcastFallback: Boolean = false
    ) {
        val deliveredToService: Boolean
            get() = serviceComponent != null

        val dispatched: Boolean
            get() = deliveredToService || deliveredByBroadcastFallback
    }

    fun resetTopActivityCache() {
        iTopActivity = null
        topActivityMode = null
    }

    private fun resolveTopActivity(context: Context): ITopActivity {
        val usageStats = TopActivityFactory.newInstance(AccessMode.USAGE_STATS)
        if (usageStats.isEnabled(context)) {
            iTopActivity = usageStats
            topActivityMode = AccessMode.USAGE_STATS
            return usageStats
        }

        val accessibility = TopActivityFactory.newInstance(AccessMode.ACCESSIBILITY)
        if (accessibility.isEnabled(context)) {
            if (topActivityMode != AccessMode.ACCESSIBILITY) {
                logger.w("UsageStats unavailable, fallback to Accessibility mode.")
            }
            iTopActivity = accessibility
            topActivityMode = AccessMode.ACCESSIBILITY
            return accessibility
        }

        iTopActivity = usageStats
        topActivityMode = AccessMode.USAGE_STATS
        return usageStats
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
        return deliverToApplication(
            context = context,
            container = container,
            payload = payload,
            launchApp = true,
            notifyRuntime = true
        ).serviceComponent
    }

    fun forwardToTargetApplication(context: Context, payload: ByteArray): ComponentName? {
        return forwardToTargetApplicationResult(
            context = context,
            payload = payload,
            notifyRuntime = true
        ).serviceComponent
    }

    fun deliverToApplication(
        context: Context,
        container: XmPushActionContainer,
        payload: ByteArray,
        launchApp: Boolean,
        notifyRuntime: Boolean
    ): ApplicationDeliveryResult {
        if (launchApp) {
            launchApp(context, container)
        }
        return forwardToTargetApplicationResult(
            context = context,
            payload = payload,
            notifyRuntime = notifyRuntime
        )
    }

    fun forwardToTargetApplicationResult(
        context: Context,
        payload: ByteArray,
        notifyRuntime: Boolean
    ): ApplicationDeliveryResult {
        val container = XMPushUtils.packToContainer(payload) ?: return ApplicationDeliveryResult()
        val metaInfo = container.metaInfo ?: return ApplicationDeliveryResult()
        val targetPackage = container.packageName

        if (notifyRuntime) {
            MiPushRuntimeBridge.onTransferToApplication(container)
        }
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
            return ApplicationDeliveryResult(serviceComponent = started)
        }

        val fallbackSent = sendBroadcastFallback(context, container, payload)
        if (fallbackSent) {
            logger.w(packageInfo(targetPackage, "service unavailable, fallback to broadcast"))
        }
        return ApplicationDeliveryResult(deliveredByBroadcastFallback = fallbackSent)
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
            Thread.currentThread().interrupt()
            logger.e(packageInfo(targetPackage, "pullUpApp interrupted"), e)
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
        val explicitReceivers = runCatching {
            context.packageManager.queryBroadcastReceivers(
                fallbackIntent,
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
        }.getOrDefault(emptyList())
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val canDispatch = activityInfo.enabled &&
                    (activityInfo.exported || activityInfo.packageName == context.packageName)
                if (!canDispatch || activityInfo.packageName != targetPackage) {
                    return@mapNotNull null
                }
                ComponentName(activityInfo.packageName, activityInfo.name)
            }
            .distinct()

        if (explicitReceivers.isNotEmpty()) {
            logger.d(
                packageInfo(
                    targetPackage,
                    "fallback to explicit receivers ${explicitReceivers.joinToString(",") { it.className }}"
                )
            )
            var dispatched = false
            explicitReceivers.forEach { component ->
                val explicitIntent = Intent(fallbackIntent).apply {
                    this.component = component
                    `package` = null
                }
                val delivered = runCatching {
                    context.sendBroadcast(explicitIntent)
                    true
                }.onFailure {
                    logger.e(packageInfo(targetPackage, "explicit receiver fallback failed ${component.className}"), it)
                }.getOrDefault(false)
                dispatched = dispatched || delivered
            }
            return dispatched
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
