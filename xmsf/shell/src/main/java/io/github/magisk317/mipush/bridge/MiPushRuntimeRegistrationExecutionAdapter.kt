package io.github.magisk317.mipush.bridge

import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.IPendingPacketErrorNotifier
import com.xiaomi.push.service.IPendingPacketSender
import com.xiaomi.push.service.MIPushAppAbsentManager
import com.xiaomi.push.service.MIPushClientManager
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushRegistrationPayloadRepairResult
import com.xiaomi.push.service.PushRegistrationState
import com.xiaomi.push.service.PushServiceRegisterAppAction
import com.xiaomi.push.service.PushServiceRegisterAppPlan
import com.xiaomi.push.service.XMPushServiceProxy
import io.github.magisk317.mipush.push.pipeline.PackageDataClearedCoordinator
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.runtime.core.PushRuntimeRegistrationChannelObservationSink
import io.github.magisk317.mipush.runtime.core.RegistrationThrottle
import io.github.magisk317.mipush.service.runtime.PushServiceIntentRuntime
import io.github.magisk317.mipush.service.runtime.RegistrationPayloadRepair

internal class MiPushRuntimeRegistrationExecutionAdapter(
    private val appContext: Context,
    private val observationSink: PushRuntimeRegistrationChannelObservationSink,
) {
    // The registration/channel observers below only want to track packages the framework serves,
    // which is why they filter out system packages. But xmsf itself is an updated system app
    // (flags carry SYSTEM | UPDATED_SYSTEM_APP), so a plain isUserApplication check also rejected
    // the push host's own registration. That silently dropped xmsf's cached registration payload,
    // leaving the request stranded with no way to reach the server after chid 5 bound.
    private fun isTrackedPackage(packageName: String): Boolean {
        return packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME ||
            packageName == appContext.packageName ||
            Utils.isUserApplication(appContext, packageName)
    }
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
            androidUserId = currentUserId(),
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
            androidUserId = currentUserId(),
        )
    }

    fun observeUnregistration(packageName: String, state: PushRegistrationState) {
        if (!isTrackedPackage(packageName)) return
        observationSink.observeUnregistration(
            packageName = packageName,
            source = "MiPushRuntimeObserverBridge.observeUnregistration",
            reason = state.name,
            nowMs = System.currentTimeMillis(),
            androidUserId = currentUserId(),
        )
    }

    private fun currentUserId(): Int = Utils.requireValidUserId(Utils.myUserId())

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
        PushRuntimeRegistrationTaskStore.dispatchAll(source, dispatcher = { _, intent ->
            runCatching {
                appContext.startService(Intent(intent))
                true
            }.getOrDefault(false)
        })
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
            androidUserId = io.github.magisk317.mipush.common.utils.Utils.requireValidUserId(
                io.github.magisk317.mipush.common.utils.Utils.myUserId(),
            ),
        )
    }

    fun processPendingRegistrationRequests(source: String, sender: IPendingPacketSender) {
        val pushAction = XMPushServiceProxy.get() ?: return
        PushRuntimePendingPacketStore.processPendingRegistrationRequests(source, sender = { packageName, payload ->
            com.xiaomi.push.service.MIPushHelper.sendPacket(pushAction, appContext, packageName, payload)
        })
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
