package com.xiaomi.push.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.topjohnwu.superuser.Shell
import com.xiaomi.push.service.MIPushNotificationHelper
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.utils.Configurations
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
        // activeApp(context, targetPackage)
        // pullUpApp(context, targetPackage, container)
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
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PushMessageProcessorEntryPoint {
    fun pushMessageProcessor(): PushMessageProcessor
}
