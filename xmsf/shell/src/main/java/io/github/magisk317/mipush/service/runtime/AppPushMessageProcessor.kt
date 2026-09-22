package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.freeze.FrozenAppCoordinator
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.service.runtime.MIPushNotificationPublishHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.platform.activity.AccessMode
import io.github.magisk317.mipush.platform.activity.ITopActivity
import io.github.magisk317.mipush.platform.activity.TopActivityFactory
import io.github.magisk317.xposed.logging.MagiskOtel

class AppPushMessageProcessor constructor(
    private val configurations: Configurations,
    private val frozenAppCoordinator: FrozenAppCoordinator? = null,
) {
    private val tag = "PushMessageProcessor"


    private val appCheckFrontMaxRetry = 8
    private val appCheckSleepDurationMs = 500L
    private val appCheckSleepMaxTimeoutMs =
        appCheckFrontMaxRetry * appCheckSleepDurationMs

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
                logW("UsageStats unavailable, fallback to Accessibility mode.")
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
            logE("mipush_payload is null")
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
            logE("cancelNotification", e)
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
            logW("skip launch app because target package is blank")
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
        notifyRuntime: Boolean,
        notified: Boolean = false
    ): ApplicationDeliveryResult {
        if (launchApp) {
            launchApp(context, container)
        }
        return forwardToTargetApplicationResult(
            context = context,
            payload = payload,
            notifyRuntime = notifyRuntime,
            notified = notified
        )
    }

    fun forwardToTargetApplicationResult(
        context: Context,
        payload: ByteArray,
        notifyRuntime: Boolean,
        notified: Boolean = false
    ): ApplicationDeliveryResult {
        val startedAt = System.nanoTime()
        val container = XMPushUtils.packToContainer(payload)
        if (container == null) {
            MagiskOtel.event(
                name = "push.dispatch",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "app_delivery",
                    "reason" to "invalid_payload",
                    "payload_size" to payload.size.toString(),
                ),
                statusOk = false,
            )
            return ApplicationDeliveryResult()
        }
        val targetPackage = container.packageName

        if (notifyRuntime) {
            MiPushRuntimeBridge.onTransferToApplication(container)
        }

        // Use unified dispatch logic from XMPushUtils
        val result = XMPushUtils.dispatchToApplicationResult(
            context,
            targetPackage,
            payload,
            fromNotification = true,
            notified = notified,
        )
        val resultTag = dispatchResultTag(result)
        logD("$tag forwardToTargetApplication pkg=$targetPackage dispatch result=$resultTag")
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val delivered = result is XMPushUtils.DispatchResult.ServiceStarted ||
            result is XMPushUtils.DispatchResult.BroadcastSent
        MagiskOtel.event(
            name = "push.dispatch",
            attributes = mapOf(
                "result" to if (delivered) "ok" else "error",
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "app_delivery",
                "reason" to resultTag,
                "target_package" to targetPackage.orEmpty(),
                "payload_size" to payload.size.toString(),
                "notified" to notified.toString(),
            ),
            statusOk = delivered,
        )
        return when (result) {
            is XMPushUtils.DispatchResult.ServiceStarted ->
                ApplicationDeliveryResult(serviceComponent = ComponentName(targetPackage, Constants.PUSH_MESSAGE_HANDLER_CLASS))
            is XMPushUtils.DispatchResult.BroadcastSent ->
                ApplicationDeliveryResult(deliveredByBroadcastFallback = true)
            is XMPushUtils.DispatchResult.ServiceBlocked,
            XMPushUtils.DispatchResult.Failed ->
                ApplicationDeliveryResult()
        }
    }

    private fun dispatchResultTag(result: XMPushUtils.DispatchResult): String = when (result) {
        XMPushUtils.DispatchResult.ServiceStarted -> "service_started"
        is XMPushUtils.DispatchResult.ServiceBlocked -> "service_blocked"
        is XMPushUtils.DispatchResult.BroadcastSent ->
            if (result.explicit) "broadcast_sent_explicit" else "broadcast_sent_generic"
        XMPushUtils.DispatchResult.Failed -> "failed"
    }

    private fun activeApp(targetPackage: String) {
        val coordinator = frozenAppCoordinator
        if (coordinator != null) {
            // Block the click route until the package is enabled and ready so the
            // subsequent pull-up resolves a launch intent (upstream behavior).
            runBlocking { coordinator.handleLaunchActivation(targetPackage) }
            return
        }
        runCatching {
            AppRootAccessFacade.runRootCommand("pm enable $targetPackage")
        }.onFailure {
            logW { packageInfo(targetPackage, "pm enable failed: ${it.localizedMessage}") }
        }
    }

    private fun getJumpIntent(context: Context, container: XmPushActionContainer): Intent? {
        return MIPushNotificationPublishHelper.getSdkIntent(context, container)
            ?: getJumpIntentFromPkg(context, container.packageName)
    }

    private fun getJumpIntentFromPkg(context: Context, targetPackage: String): Intent? {
        return runCatching {
            context.packageManager.getLaunchIntentForPackage(targetPackage)
        }.onFailure {
            logE(it) { packageInfo(targetPackage, "get launch intent failed") }
        }.getOrNull()?.also {
            logD { packageInfo(targetPackage, "resolved launch intent=$it") }
        }
    }

    private fun pullUpApp(context: Context, targetPackage: String, container: XmPushActionContainer): Long {
        val start = System.currentTimeMillis()
        try {
            val topActivity = resolveTopActivity(context)
            if (!topActivity.isEnabled(context)) {
                logW { packageInfo(targetPackage, "top activity detector disabled, launch without foreground verification") }
                startJumpIntent(context, targetPackage, getJumpIntent(context, container))
                // Without a foreground detector we cannot verify the pull-up, but the caller is
                // about to dispatch a payload that needs a live app process. Give a just-launched
                // app a bounded settle window instead of returning instantly.
                Thread.sleep(appCheckSleepDurationMs * 2)
                return System.currentTimeMillis() - start
            }

            if (!topActivity.isAppForeground(context, targetPackage)) {
                logD { packageInfo(targetPackage, "app is not at front, pull up") }
                startJumpIntent(context, targetPackage, getJumpIntentFromPkg(context, targetPackage))
                // Wait until the app actually reaches the foreground before returning. A blind
                // fixed delay lets the downstream dispatch race the app start: a stopped app
                // whose process never came up has no runtime receiver, so every dispatch
                // channel fails (the "已拒绝"/DENY_DISABLED force-register case).
                // Mirrors upstream pullUpApp: poll, re-launch once at half the budget.
                for (i in 0 until appCheckFrontMaxRetry) {
                    if (topActivity.isAppForeground(context, targetPackage)) {
                        break
                    }
                    Thread.sleep(appCheckSleepDurationMs)
                    if (i == appCheckFrontMaxRetry / 2) {
                        logD { packageInfo(targetPackage, "still not at front, relaunch") }
                        startJumpIntent(context, targetPackage, getJumpIntentFromPkg(context, targetPackage))
                    }
                }
                if (System.currentTimeMillis() - start >= appCheckSleepMaxTimeoutMs) {
                    logW { packageInfo(targetPackage, "pull up app timeout") }
                }
            } else {
                logD { packageInfo(targetPackage, "app is at foreground") }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logE(e) { packageInfo(targetPackage, "pullUpApp interrupted") }
        } catch (e: RuntimeException) {
            logE(e) { packageInfo(targetPackage, "pullUpApp failed ${e.localizedMessage}") }
        }
        return System.currentTimeMillis() - start
    }

    private fun startJumpIntent(context: Context, targetPackage: String, intent: Intent?) {
        if (intent == null) {
            logW { packageInfo(targetPackage, "can not resolve launch intent") }
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching {
            context.startActivity(intent)
            logD { packageInfo(targetPackage, "start activity intent=$intent") }
        }.onFailure {
            logE(it) { packageInfo(targetPackage, "start activity failed") }
        }
    }

    private fun packageInfo(packageName: String, message: String): String = "[$packageName] $message"
}
