package io.github.magisk317.mipush.diagnostics

import android.app.Application
import android.content.Context
import io.github.magisk317.mipush.Global
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import com.xiaomi.mipush.sdk.MiPushClient
import io.github.magisk317.mipush.runtime.PushRuntime
import com.xiaomi.xmsf.push.control.PushControllerUtils
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking

object PushHealthSnapshotLogger {
    private const val TAG = "PushHealthSnapshot"

    data class Snapshot(
        val stage: String,
        val processName: String,
        val pushEnabled: Boolean,
        val regIdPresent: Boolean,
        val debugMode: Boolean,
        val lifecycleReady: Boolean,
        val lifecyclePendingCount: Int,
        val runtimeExecutionReady: Boolean,
        val runtimeConnectionState: String,
        val runtimeTrackedChannels: Int,
        val runtimeBoundChannels: Int,
        val runtimeTrackedRegistrations: Int,
        val runtimeRegisteredPackages: Int,
        val runtimeDownstreamCount: Long,
        val runtimeDeliveredCount: Long,
        val runtimeDuplicateCount: Long,
        val runtimeAckCount: Long,
        val runtimeBroadcastFallbackCount: Long,
        val runtimeNotificationCancelCount: Long,
        val runtimeNotificationCount: Long,
        val runtimeChannelCount: Long,
        val runtimeAccountCount: Long,
        val extra: String?
    )

    fun log(context: Context, stage: String, extra: String? = null) {
        runCatching {
            val snapshot = capture(context, stage, extra)
            Napier.i(format(snapshot), tag = TAG)
        }.onFailure {
            RateLimitedWarnLogger.warn(
                logTag = TAG,
                key = "capture:$stage",
                message = "failed to capture health snapshot",
                throwable = it
            )
        }
    }

    internal fun capture(context: Context, stage: String, extra: String? = null): Snapshot {
        val processName = runCatching { Application.getProcessName() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: context.applicationInfo?.processName
            ?: context.packageName
        val pushEnabled = runCatching { PushControllerUtils.isPrefsEnable(context) }.getOrDefault(false)
        val regIdPresent = runCatching { MiPushClient.getRegId(context).isNotBlank() }.getOrDefault(false)
        val debugMode = runCatching { runBlocking { Global.ConfigCenter().isDebugModeAsync() } }.getOrDefault(false)
        val lifecycle = XMPushServiceLifecycleBridge.snapshot()
        val runtime = PushRuntime.snapshot()
        return Snapshot(
            stage = stage,
            processName = processName,
            pushEnabled = pushEnabled,
            regIdPresent = regIdPresent,
            debugMode = debugMode,
            lifecycleReady = lifecycle.serviceReady,
            lifecyclePendingCount = lifecycle.pendingStartCount,
            runtimeExecutionReady = runtime.executionReady,
            runtimeConnectionState = runtime.connectionState.name,
            runtimeTrackedChannels = runtime.trackedChannelCount,
            runtimeBoundChannels = runtime.boundChannelCount,
            runtimeTrackedRegistrations = runtime.trackedRegistrationCount,
            runtimeRegisteredPackages = runtime.registeredPackageCount,
            runtimeDownstreamCount = runtime.downstreamMessageCount,
            runtimeDeliveredCount = runtime.deliveredToAppCount,
            runtimeDuplicateCount = runtime.duplicateMessageCount,
            runtimeAckCount = runtime.ackMessageCount,
            runtimeBroadcastFallbackCount = runtime.broadcastFallbackDeliveryCount,
            runtimeNotificationCancelCount = runtime.notificationCancelCount,
            runtimeNotificationCount = runtime.notificationEventCount,
            runtimeChannelCount = runtime.channelEventCount,
            runtimeAccountCount = runtime.accountEventCount,
            extra = extra
        )
    }

    internal fun format(snapshot: Snapshot): String {
        val extraInfo = snapshot.extra?.takeIf { it.isNotBlank() }?.let { " extra=$it" }.orEmpty()
        return "stage=${snapshot.stage}" +
            " process=${snapshot.processName}" +
            " pushEnabled=${snapshot.pushEnabled}" +
            " regIdPresent=${snapshot.regIdPresent}" +
            " debugMode=${snapshot.debugMode}" +
            " lifecycleReady=${snapshot.lifecycleReady}" +
            " lifecyclePending=${snapshot.lifecyclePendingCount}" +
            " runtimeExecutionReady=${snapshot.runtimeExecutionReady}" +
            " runtimeConnection=${snapshot.runtimeConnectionState}" +
            " runtimeTrackedChannels=${snapshot.runtimeTrackedChannels}" +
            " runtimeBoundChannels=${snapshot.runtimeBoundChannels}" +
            " runtimeTrackedRegs=${snapshot.runtimeTrackedRegistrations}" +
            " runtimeRegistered=${snapshot.runtimeRegisteredPackages}" +
            " runtimeDownstream=${snapshot.runtimeDownstreamCount}" +
            " runtimeDelivered=${snapshot.runtimeDeliveredCount}" +
            " runtimeDuplicate=${snapshot.runtimeDuplicateCount}" +
            " runtimeAck=${snapshot.runtimeAckCount}" +
            " runtimeFallback=${snapshot.runtimeBroadcastFallbackCount}" +
            " runtimeCancel=${snapshot.runtimeNotificationCancelCount}" +
            " runtimeNotification=${snapshot.runtimeNotificationCount}" +
            " runtimeChannel=${snapshot.runtimeChannelCount}" +
            " runtimeAccount=${snapshot.runtimeAccountCount}" +
            extraInfo
    }
}
