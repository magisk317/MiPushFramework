package io.github.magisk317.mipush.bridge

import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.IPendingPacketErrorNotifier
import com.xiaomi.push.service.IPendingPacketSender
import com.xiaomi.push.service.MIPushAppAbsentManager
import com.xiaomi.push.service.MIPushClientManager
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushRegistrationPayloadRepairResult
import com.xiaomi.push.service.PushRegistrationState
import com.xiaomi.push.service.PushServiceRegisterAppAction
import com.xiaomi.push.service.PushServiceRegisterAppPlan
import com.xiaomi.push.service.XMPushServiceProxy
import io.github.magisk317.mipush.push.pipeline.PackageDataClearedCoordinator
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.runtime.core.PushRuntimeRegistrationChannelObservationSink
import io.github.magisk317.mipush.runtime.core.RegistrationThrottle
import io.github.magisk317.mipush.service.runtime.PushServiceIntentRuntime
import io.github.magisk317.mipush.service.runtime.RegistrationPayloadRepair

internal class MiPushRuntimeRegistrationExecutionAdapter(
    private val appContext: Context,
    private val observationSink: PushRuntimeRegistrationChannelObservationSink,
    private val isTrackedPackage: (String) -> Boolean,
) {
    fun onRegistrationStateChanged(
        packageName: String,
        state: PushRegistrationState,
        reason: String,
        message: String,
    ) {
        if (!isTrackedPackage(packageName)) return
        observationSink.observeRegistrationState(
            packageName = packageName,
            state = state,
            source = reason,
            reason = message,
            nowMs = System.currentTimeMillis(),
        )
    }

    fun onPackageDataCleared(packageName: String) {
        PackageDataClearedCoordinator.handle(appContext, packageName)
    }

    fun onRegistrationResult(packageName: String, success: Boolean, source: String, reason: String) {
        if (!isTrackedPackage(packageName)) return
        observationSink.observeRegistrationResult(
            packageName = packageName,
            success = success,
            source = source,
            reason = reason,
            nowMs = System.currentTimeMillis(),
        )
    }

    fun observeUnregistration(packageName: String, state: PushRegistrationState) {
        if (!isTrackedPackage(packageName)) return
        observationSink.observeUnregistration(
            packageName = packageName,
            source = "MiPushRuntimeObserverBridge.observeUnregistration",
            reason = state.name,
            nowMs = System.currentTimeMillis(),
        )
    }

    fun repairRegistrationPayload(context: Context, packageName: String): PushRegistrationPayloadRepairResult? =
        RegistrationPayloadRepair.repair(context, packageName)

    fun rememberPendingRegistration(packageName: String, appId: String?) {
        if (!isTrackedPackage(packageName)) return
        MIPushAppAbsentManager.rememberPendingRegistration(appContext, packageName, appId)
    }

    fun cacheRegistrationRequest(packageName: String, payload: ByteArray) {
        if (!isTrackedPackage(packageName)) return
        PushRuntimePendingPacketStore.cacheRegistrationRequest(packageName, payload)
    }

    fun cacheRegistrationTask(
        packageName: String,
        intent: Intent,
        source: String,
        reason: String,
        timestampMs: Long,
    ) {
        if (!isTrackedPackage(packageName)) return
        PushRuntimeRegistrationTaskStore.cache(packageName, intent, source, reason, timestampMs)
    }

    fun dispatchRegistrationTasks(source: String, dispatcher: Any?) {
        PushRuntimeRegistrationTaskStore.dispatchAll(source) { _, intent ->
            runCatching {
                appContext.startService(Intent(intent))
                true
            }.getOrDefault(false)
        }
    }

    fun clearRegistrationTasks(packageName: String) {
        PushRuntimeRegistrationTaskStore.clear(packageName)
    }

    fun notifyRegisterError(
        errorCode: Int,
        errorMessage: String,
        notifier: IPendingPacketErrorNotifier,
    ) {
        PushRuntimePendingPacketStore.notifyRegisterError(
            errorCode = errorCode,
            errorMessage = errorMessage,
            notifier = { packageName, payload, code, message ->
                MIPushClientManager.notifyError(appContext, packageName, payload, code, message)
            },
        )
    }

    fun processPendingRegistrationRequests(source: String, sender: IPendingPacketSender) {
        val pushAction = XMPushServiceProxy.get() ?: return
        PushRuntimePendingPacketStore.processPendingRegistrationRequests(source) { packageName, payload ->
            com.xiaomi.push.service.MIPushHelper.sendPacket(pushAction, appContext, packageName, payload)
        }
    }

    fun resolveRegisterAppPlan(
        packageName: String?,
        payload: ByteArray?,
        envChanged: Boolean,
        envType: Int,
        servicePackageName: String,
    ): PushServiceRegisterAppPlan {
        if (packageName != null && io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb.isBlocked(packageName)) {
            return PushServiceRegisterAppPlan(action = PushServiceRegisterAppAction.Ignore)
        }
        if (packageName != null) {
            val channelBound = PushClientsManager.getInstance().getAllClients().any {
                it.status == PushClientsManager.ClientStatus.binded
            }
            if (RegistrationThrottle.shouldThrottle(packageName, channelBound)) {
                return PushServiceRegisterAppPlan(action = PushServiceRegisterAppAction.Ignore)
            }
        }
        return PushServiceIntentRuntime.resolveRegisterAppPlan(
            packageName,
            payload,
            envChanged,
            envType,
            servicePackageName,
        )
    }
}
