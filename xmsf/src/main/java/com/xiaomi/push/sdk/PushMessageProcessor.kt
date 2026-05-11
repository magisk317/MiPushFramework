package com.xiaomi.push.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.platform.activity.AccessMode
import io.github.magisk317.mipush.platform.activity.ITopActivity
import io.github.magisk317.mipush.platform.activity.TopActivityFactory
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
        val targetPackage = container.packageName
        if (targetPackage.isBlank()) {
            logger.w("skip launch app because target package is blank")
            return
        }
        activeApp(targetPackage)
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
        val targetPackage = container.packageName

        if (notifyRuntime) {
            MiPushRuntimeBridge.onTransferToApplication(container)
        }

        // Use unified dispatch logic from XMPushUtils
        val dispatched = XMPushUtils.dispatchToApplication(context, targetPackage, payload, fromNotification = true)
        return if (dispatched) {
            // We don't get the ComponentName back in this simplified call, but for the sake of current logic:
            ApplicationDeliveryResult(serviceComponent = ComponentName(targetPackage, Constants.PUSH_MESSAGE_HANDLER_CLASS))
        } else {
            ApplicationDeliveryResult()
        }
    }

    private fun activeApp(targetPackage: String) {
        runCatching {
            AppRootAccessFacade.runRootCommand("pm enable $targetPackage")
        }.onFailure {
            logger.w(packageInfo(targetPackage, "pm enable failed: ${it.localizedMessage}"))
        }
    }

    private fun getJumpIntent(context: Context, container: XmPushActionContainer): Intent? {
        return MyMIPushNotificationHelper.getSdkIntent(context, container)
            ?: getJumpIntentFromPkg(context, container.packageName)
    }

    private fun getJumpIntentFromPkg(context: Context, targetPackage: String): Intent? {
        return runCatching {
            context.packageManager.getLaunchIntentForPackage(targetPackage)
        }.onFailure {
            logger.e(packageInfo(targetPackage, "get launch intent failed"), it)
        }.getOrNull()?.also {
            logger.d(packageInfo(targetPackage, "resolved launch intent=$it"))
        }
    }

    private fun pullUpApp(context: Context, targetPackage: String, container: XmPushActionContainer): Long {
        val start = System.currentTimeMillis()
        try {
            val topActivity = resolveTopActivity(context)
            if (!topActivity.isEnabled(context)) {
                logger.w(packageInfo(targetPackage, "top activity detector disabled, launch without foreground verification"))
                startJumpIntent(context, targetPackage, getJumpIntent(context, container))
                return System.currentTimeMillis() - start
            }

            if (!topActivity.isAppForeground(context, targetPackage)) {
                logger.d(packageInfo(targetPackage, "app is not at front, pull up"))
                startJumpIntent(context, targetPackage, getJumpIntent(context, container))
                for (i in 0 until APP_CHECK_FRONT_MAX_RETRY) {
                    if (topActivity.isAppForeground(context, targetPackage)) {
                        break
                    }
                    Thread.sleep(APP_CHECK_SLEEP_DURATION_MS)
                    if (i == (APP_CHECK_FRONT_MAX_RETRY / 2)) {
                        startJumpIntent(context, targetPackage, getJumpIntentFromPkg(context, targetPackage))
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
        return System.currentTimeMillis() - start
    }

    private fun startJumpIntent(context: Context, targetPackage: String, intent: Intent?) {
        if (intent == null) {
            logger.w(packageInfo(targetPackage, "can not resolve launch intent"))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching {
            context.startActivity(intent)
            logger.d(packageInfo(targetPackage, "start activity intent=$intent"))
        }.onFailure {
            logger.e(packageInfo(targetPackage, "start activity failed"), it)
        }
    }

    private fun packageInfo(packageName: String, message: String): String = "[$packageName] $message"
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PushMessageProcessorEntryPoint {
    fun pushMessageProcessor(): PushMessageProcessor
}
