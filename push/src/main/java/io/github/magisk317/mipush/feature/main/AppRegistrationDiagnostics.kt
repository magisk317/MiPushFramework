package io.github.magisk317.mipush.feature.main

import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import com.magisk317.compat.RegistrationStateCompat
import com.magisk317.utils.RegistrationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication

data class AppRegistrationDiagnostics(
    val packageName: String,
    val displayTypeReason: String,
    val forceTypeReason: String,
    val hasRuntimeService: Boolean,
    val hasHandlerService: Boolean,
    val hasOfficialReceiver: Boolean,
    val hasBridgeComponent: Boolean,
    val hasLauncherEntry: Boolean,
    val hasLocalRegistration: Boolean,
    val regSecCount: Int,
    val lastReceiveTime: Long?,
    val latestRegistrationEventType: Int?,
    val latestRegistrationEventResult: Int?,
    val latestRegistrationEventDate: Long?,
    val registeredType: Int,
    val inferenceReason: String
) {
    val hasRegSec: Boolean
        get() = regSecCount > 0
}

object AppRegistrationDiagnosticsHelper {
    private val runtimeServiceCandidates = setOf(
        "com.xiaomi.push.service.XMPushService",
        "com.xiaomi.push.service.XMJobService"
    )
    private val handlerCandidates = setOf(
        "com.xiaomi.mipush.sdk.PushMessageHandler",
        "com.xiaomi.mipush.sdk.MessageHandleService"
    )
    private val officialReceiverCandidates = setOf(
        "com.xiaomi.mipush.sdk.PushServiceReceiver",
        "com.xiaomi.push.service.receivers.PingReceiver",
        "com.xiaomi.mipush.sdk.PushMessageReceiver"
    )
    private val bridgeHints = listOf(
        "MiuiPushReceiver",
        "XiaoMiPushReceiver",
        "XiaomiPushReceiver",
        "com.igexin",
        "umeng",
        "HeytapPush",
        "HmsMessageService",
        "MzPush",
        "XGPush"
    )
    private val registrationEventTypes = setOf(
        Event.Type.Registration,
        Event.Type.RegistrationResult,
        Event.Type.UnRegistration
    )

    fun load(
        context: Context,
        packageName: String,
        registeredType: Int
    ): AppRegistrationDiagnostics {
        val packageInfo = runCatching {
            PackageManagerCompatBridge.getPackageInfo(
                context.packageManager,
                packageName,
                PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
            )
        }.getOrNull()
        val serviceNames = packageInfo?.services?.mapNotNull { it.name }?.toSet() ?: emptySet()
        val receiverNames = packageInfo?.receivers?.mapNotNull { it.name }?.toSet() ?: emptySet()
        val latestRegistrationEvent = runBlocking {
            EventDb.queryAsync(
                skip = 0,
                limit = 1,
                types = registrationEventTypes,
                pkg = packageName,
                text = null
            ).firstOrNull()
        }
        val hasRuntimeService = runtimeServiceCandidates.any { it in serviceNames }
        val hasHandlerService = handlerCandidates.any { it in serviceNames }
        val hasOfficialReceiver = officialReceiverCandidates.any { it in receiverNames }
        val hasBridgeComponent = bridgeHints.any { hint ->
            serviceNames.any { it.contains(hint, ignoreCase = true) } ||
                receiverNames.any { it.contains(hint, ignoreCase = true) }
        }
        val hasLocalRegistration = RegistrationStateCompat.hasValidLocalRegistration(packageName)
        val regSecCount = Utils.getRegSecs(packageName).size
        val displayTypeReason = RegistrationHelper.classifyDisplayTypeReason(serviceNames, receiverNames)
        val forceTypeReason = RegistrationHelper.classifyForceRegisterPlan(
            packageName = packageName,
            serviceNames = serviceNames,
            receiverNames = receiverNames
        ).reason
        return AppRegistrationDiagnostics(
            packageName = packageName,
            displayTypeReason = displayTypeReason,
            forceTypeReason = forceTypeReason,
            hasRuntimeService = hasRuntimeService,
            hasHandlerService = hasHandlerService,
            hasOfficialReceiver = hasOfficialReceiver,
            hasBridgeComponent = hasBridgeComponent,
            hasLauncherEntry = context.packageManager.getLaunchIntentForPackage(packageName) != null,
            hasLocalRegistration = hasLocalRegistration,
            regSecCount = regSecCount,
            lastReceiveTime = Utils.getLastReceiveTime(packageName),
            latestRegistrationEventType = latestRegistrationEvent?.type,
            latestRegistrationEventResult = latestRegistrationEvent?.result,
            latestRegistrationEventDate = latestRegistrationEvent?.date,
            registeredType = registeredType,
            inferenceReason = inferReason(
                registeredType = registeredType,
                latestEvent = latestRegistrationEvent,
                hasLocalRegistration = hasLocalRegistration,
                hasRegSec = regSecCount > 0
            )
        )
    }

    suspend fun launchAndObserve(
        context: Context,
        packageName: String,
        registeredTypeProvider: () -> Int,
        timeoutMs: Long = 15_000L,
        pollMs: Long = 1_500L
    ): AppRegistrationDiagnostics {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return load(context, packageName, registeredTypeProvider())
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(launchIntent) }
        val start = System.currentTimeMillis()
        var latest = load(context, packageName, registeredTypeProvider())
        while (System.currentTimeMillis() - start < timeoutMs) {
            delay(pollMs)
            latest = load(context, packageName, registeredTypeProvider())
            val recentEventDate = latest.latestRegistrationEventDate ?: 0L
            if (latest.hasLocalRegistration || latest.registeredType == RegisteredApplication.RegisteredType.Registered || recentEventDate >= start) {
                return latest
            }
        }
        return latest
    }

    private fun inferReason(
        registeredType: Int,
        latestEvent: Event?,
        hasLocalRegistration: Boolean,
        hasRegSec: Boolean
    ): String {
        if (registeredType == RegisteredApplication.RegisteredType.Registered) {
            return "registered"
        }
        if (latestEvent == null && !hasLocalRegistration && !hasRegSec) {
            return "never_attempted"
        }
        if (latestEvent?.type == Event.Type.UnRegistration) {
            return "unregistered_after_attempt"
        }
        if (latestEvent?.type == Event.Type.RegistrationResult && latestEvent.result != Event.ResultType.OK) {
            return "registration_result_failed"
        }
        if (latestEvent?.type == Event.Type.Registration) {
            return "registering_or_waiting_result"
        }
        if (hasLocalRegistration && registeredType != RegisteredApplication.RegisteredType.Registered) {
            return "local_state_stale"
        }
        if (hasRegSec && !hasLocalRegistration) {
            return "has_secret_but_no_local_reg"
        }
        return "unknown"
    }
}
