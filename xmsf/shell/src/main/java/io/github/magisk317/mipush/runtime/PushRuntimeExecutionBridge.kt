package io.github.magisk317.mipush.runtime

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.os.Bundle
import io.github.magisk317.mipush.diagnostics.PushHealthSnapshotLogger
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import io.github.magisk317.mipush.utils.RegistrationHelper
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.mipush.sdk.AppInfoHolder
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.mipush.sdk.PushServiceClient
import com.xiaomi.push.sdk.PushMessageProcessor
import com.xiaomi.push.service.ResetConnectJob
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.xposed.logging.MagiskOtel

object PushRuntimeExecutionBridge : PushRuntimeExecutionHost {

    @Volatile
    private var appContext: Context? = null

    @JvmStatic
    fun attach(context: Context) {
        appContext = context.applicationContext ?: context
        PushRuntime.attachExecutionHost(this)
    }

    @JvmStatic
    fun detach() {
        PushRuntime.detachExecutionHost(this)
        appContext = null
    }

    override fun requestFrameworkRegistration(reason: String): Boolean {
        val context = appContext ?: return false
        val startedAt = System.nanoTime()
        return runCatching {
            val appInfoHolder = AppInfoHolder.getInstance(context)
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.frameworkRegister",
                "reason=$reason before=${appInfoHolder.registrationStateSummary(Constants.APP_ID, Constants.APP_KEY)}"
            )
            MiPushClient.registerPush(context, Constants.APP_ID, Constants.APP_KEY)
            val regIdPresent = MiPushClient.getRegId(context).isNotBlank()
            if (regIdPresent) {
                PushRuntime.observeRegistrationResult(
                    packageName = PushRuntimeComponents.SERVICE_PACKAGE,
                    success = true,
                    source = "PushRuntimeExecutionBridge.requestFrameworkRegistration",
                    reason = "reg_id_present"
                )
            }
            logD(
                "requestFrameworkRegistration reason=$reason regIdPresent=$regIdPresent " +
                    appInfoHolder.registrationStateSummary(Constants.APP_ID, Constants.APP_KEY)
            )
            emitBridge(
                name = "push.register",
                result = "ok",
                stage = "runtime_framework_register",
                reason = if (regIdPresent) "reg_id_present" else reason.ifBlank { "requested" },
                durationMs = elapsedMs(startedAt),
                extra = mapOf("target_package" to PushRuntimeComponents.SERVICE_PACKAGE),
            )
            true
        }.getOrElse {
            logE("requestFrameworkRegistration failed reason=$reason", it)
            emitBridge(
                name = "push.register",
                result = "error",
                stage = "runtime_framework_register",
                reason = "exception",
                durationMs = elapsedMs(startedAt),
                statusOk = false,
                extra = mapOf("target_package" to PushRuntimeComponents.SERVICE_PACKAGE),
            )
            false
        }
    }

    override fun requestApplicationRegistration(packageName: String, reason: String): Boolean {
        val context = appContext ?: return false
        val startedAt = System.nanoTime()
        return runCatching {
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.appRegister",
                "pkg=$packageName reason=$reason"
            )
            val dispatched = RegistrationHelper.tryForceRegister(packageName)
            logD("requestApplicationRegistration pkg=$packageName reason=$reason dispatched=$dispatched")
            emitBridge(
                name = "push.register",
                result = if (dispatched) "ok" else "skip",
                stage = "runtime_app_register",
                reason = if (dispatched) reason.ifBlank { "dispatched" } else "not_dispatched",
                durationMs = elapsedMs(startedAt),
                statusOk = dispatched,
                extra = mapOf("target_package" to packageName),
            )
            dispatched
        }.getOrElse {
            logE("requestApplicationRegistration failed pkg=$packageName reason=$reason", it)
            emitBridge(
                name = "push.register",
                result = "error",
                stage = "runtime_app_register",
                reason = "exception",
                durationMs = elapsedMs(startedAt),
                statusOk = false,
                extra = mapOf("target_package" to packageName),
            )
            false
        }
    }

    override fun processPendingRegisterTasks(reason: String): Boolean {
        val context = appContext ?: return false
        val startedAt = System.nanoTime()
        return runCatching {
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.processRegisterTask",
                "reason=$reason"
            )
            PushServiceClient.getInstance(context).processRegisterTask()
            logD("processPendingRegisterTasks reason=$reason")
            emitBridge(
                name = "push.register",
                result = "ok",
                stage = "runtime_pending_register",
                reason = reason.ifBlank { "processed" },
                durationMs = elapsedMs(startedAt),
            )
            true
        }.getOrElse {
            logE("processPendingRegisterTasks failed reason=$reason", it)
            emitBridge(
                name = "push.register",
                result = "error",
                stage = "runtime_pending_register",
                reason = "exception",
                durationMs = elapsedMs(startedAt),
                statusOk = false,
            )
            false
        }
    }

    override fun dispatchDownstreamPayload(
        payload: ByteArray,
        source: String,
        launchApp: Boolean
    ): PushRuntimeApplicationDispatchResult {
        val context = appContext ?: return PushRuntimeApplicationDispatchResult()
        val startedAt = System.nanoTime()
        return runCatching {
            val container = XMPushUtils.packToContainer(payload)
            if (container == null) {
                emitBridge(
                    name = "push.dispatch",
                    result = "error",
                    stage = "runtime_downstream",
                    reason = "invalid_payload",
                    durationMs = elapsedMs(startedAt),
                    statusOk = false,
                    extra = mapOf(
                        "source" to source.ifBlank { "unknown" },
                        "payload_size" to payload.size.toString(),
                    ),
                )
                return@runCatching PushRuntimeApplicationDispatchResult()
            }
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.dispatchDownstream",
                "pkg=${container.packageName} source=$source launch=$launchApp"
            )
            val result = getProcessor(context).deliverToApplication(
                context = context,
                container = container,
                payload = payload,
                launchApp = launchApp,
                notifyRuntime = false
            )
            emitBridge(
                name = "push.dispatch",
                result = if (result.dispatched) "ok" else "error",
                stage = "runtime_downstream",
                reason = when {
                    result.deliveredToService -> "service"
                    result.deliveredByBroadcastFallback -> "broadcast"
                    else -> "failed"
                },
                durationMs = elapsedMs(startedAt),
                statusOk = result.dispatched,
                extra = mapOf(
                    "source" to source.ifBlank { "unknown" },
                    "target_package" to container.packageName.orEmpty(),
                    "payload_size" to payload.size.toString(),
                ),
            )
            PushRuntimeApplicationDispatchResult(
                dispatched = result.dispatched,
                deliveredToService = result.deliveredToService,
                deliveredByBroadcastFallback = result.deliveredByBroadcastFallback
            )
        }.getOrElse {
            logE("dispatchDownstreamPayload failed source=$source", it)
            emitBridge(
                name = "push.dispatch",
                result = "error",
                stage = "runtime_downstream",
                reason = "exception",
                durationMs = elapsedMs(startedAt),
                statusOk = false,
                extra = mapOf(
                    "source" to source.ifBlank { "unknown" },
                    "payload_size" to payload.size.toString(),
                ),
            )
            PushRuntimeApplicationDispatchResult()
        }
    }

    override fun cancelNotificationForPayload(
        payload: ByteArray,
        notificationId: Int,
        notificationGroup: String?,
        source: String
    ): Boolean {
        val context = appContext ?: return false
        val startedAt = System.nanoTime()
        return runCatching {
            val container = XMPushUtils.packToContainer(payload) ?: return@runCatching false
            val bundle = Bundle().apply {
                putByteArray(com.xiaomi.push.service.PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
                putInt(Constants.INTENT_NOTIFICATION_ID, notificationId)
                putString(Constants.INTENT_NOTIFICATION_GROUP, notificationGroup)
            }
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.cancelNotification",
                "pkg=${container.packageName} id=$notificationId source=$source"
            )
            getProcessor(context).cancelNotification(context, bundle, container)
            emitBridge(
                name = "notify.cancel",
                result = "ok",
                stage = "runtime_cancel",
                reason = source.ifBlank { "cancel" },
                durationMs = elapsedMs(startedAt),
                extra = mapOf(
                    "target_package" to container.packageName.orEmpty(),
                    "payload_size" to payload.size.toString(),
                ),
            )
            true
        }.getOrElse {
            logE("cancelNotificationForPayload failed source=$source", it)
            emitBridge(
                name = "notify.cancel",
                result = "error",
                stage = "runtime_cancel",
                reason = "exception",
                durationMs = elapsedMs(startedAt),
                statusOk = false,
                extra = mapOf(
                    "source" to source.ifBlank { "unknown" },
                    "payload_size" to payload.size.toString(),
                ),
            )
            false
        }
    }

    override fun ensureConnection(reason: String): Boolean {
        val startedAt = System.nanoTime()
        return runCatching {
            val service = XMPushServiceLifecycleBridge.withService { it } ?: return@runCatching false
            PushHealthSnapshotLogger.log(
                service,
                "PushRuntimeExecutionBridge.ensureConnection",
                "reason=$reason connected=${service.isConnected} connecting=${service.isConnecting}"
            )
            val alreadyActive = service.isConnected || service.isConnecting
            if (alreadyActive) {
                PushRuntimeChannelTracker.syncNow("PushRuntimeExecutionBridge.ensureConnection:already_active")
            } else {
                service.scheduleConnect(true)
                PushRuntimeChannelTracker.syncNow("PushRuntimeExecutionBridge.ensureConnection:scheduled")
            }
            emitBridge(
                name = "push.lifecycle",
                result = "ok",
                stage = "runtime_ensure_connection",
                reason = if (alreadyActive) "already_active" else "scheduled",
                durationMs = elapsedMs(startedAt),
                extra = mapOf("source" to reason.ifBlank { "ensure" }),
            )
            true
        }.getOrElse {
            logE("ensureConnection failed reason=$reason", it)
            emitBridge(
                name = "push.lifecycle",
                result = "error",
                stage = "runtime_ensure_connection",
                reason = "exception",
                durationMs = elapsedMs(startedAt),
                statusOk = false,
                extra = mapOf("source" to reason.ifBlank { "ensure" }),
            )
            false
        }
    }

    override fun resetConnection(reason: String): Boolean {
        val startedAt = System.nanoTime()
        return runCatching {
            val service = XMPushServiceLifecycleBridge.withService { it } ?: return@runCatching false
            PushHealthSnapshotLogger.log(
                service,
                "PushRuntimeExecutionBridge.resetConnection",
                "reason=$reason"
            )
            service.executeJob(ResetConnectJob(service))
            PushRuntime.observeConnectionState(
                state = PushConnectionState.Connecting,
                source = "PushRuntimeExecutionBridge.resetConnection",
                host = service.currentConnection?.host,
                reason = reason
            )
            emitBridge(
                name = "push.lifecycle",
                result = "ok",
                stage = "runtime_reset_connection",
                reason = reason.ifBlank { "reset" },
                durationMs = elapsedMs(startedAt),
            )
            true
        }.getOrElse {
            logE("resetConnection failed reason=$reason", it)
            emitBridge(
                name = "push.lifecycle",
                result = "error",
                stage = "runtime_reset_connection",
                reason = "exception",
                durationMs = elapsedMs(startedAt),
                statusOk = false,
                extra = mapOf("source" to reason.ifBlank { "reset" }),
            )
            false
        }
    }

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)

    private fun emitBridge(
        name: String,
        result: String,
        stage: String,
        reason: String,
        durationMs: Long,
        statusOk: Boolean = true,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = linkedMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "app",
            "stage" to stage,
            "reason" to reason,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = name, attributes = attrs, statusOk = statusOk)
    }

    private fun getProcessor(context: Context): PushMessageProcessor {
        return AppDependencies.get<PushMessageProcessor>(context.applicationContext)
    }
}
