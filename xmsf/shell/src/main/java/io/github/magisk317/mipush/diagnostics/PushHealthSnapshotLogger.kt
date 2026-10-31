package io.github.magisk317.mipush.diagnostics

import android.app.Application
import android.content.Context
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import com.xiaomi.mipush.sdk.MiPushClient
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.control.PushControllerUtils
import android.os.SystemClock
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.logging.FixedWindowLogLimiter
import io.github.magisk317.xposed.logging.MagiskLogPolicy

object PushHealthSnapshotLogger {
    private const val TAG = "PushHealthSnapshot"

    private val warnLimiter = FixedWindowLogLimiter(maxEvents = 1, windowMs = 30_000L)

    /**
     * Rate-limited warning: one per key per 30s window, then suppressed until the
     * window rolls. Shares the kit's platform-neutral limiter; output stays on kermit.
     */
    private fun warnLimited(key: String, message: String, throwable: Throwable? = null) {
        if (!warnLimiter.tryAcquire(key, SystemClock.elapsedRealtime()).allowed) return
        val text = "[$key] $message"
        if (throwable == null) {
            XLog.tagged(TAG).w { text }
        } else {
            XLog.e(text, throwable)
        }
    }

    data class Snapshot(
        val stage: String,
        val processName: String,
        val pushEnabled: Boolean,
        val regIdPresent: Boolean,
        val verboseLogging: Boolean,
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
            XLog.tagged(TAG).i { format(snapshot) }
        }.onFailure {
            warnLimited(
                key = "capture:$stage",
                message = "failed to capture health snapshot",
                throwable = it,
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
        val verboseLogging = MagiskLogPolicy.current().verboseLogging
        val lifecycle = XMPushServiceLifecycleBridge.snapshot()
        val runtime = PushRuntime.snapshot()
        return Snapshot(
            stage = stage,
            processName = processName,
            pushEnabled = pushEnabled,
            regIdPresent = regIdPresent,
            verboseLogging = verboseLogging,
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
        return PushHealthSnapshotFormatter.format(
            PushHealthSnapshot(
                stage = snapshot.stage,
                processName = snapshot.processName,
                pushEnabled = snapshot.pushEnabled,
                regIdPresent = snapshot.regIdPresent,
                verboseLogging = snapshot.verboseLogging,
                lifecycleReady = snapshot.lifecycleReady,
                lifecyclePendingCount = snapshot.lifecyclePendingCount,
                runtimeExecutionReady = snapshot.runtimeExecutionReady,
                runtimeConnectionState = snapshot.runtimeConnectionState,
                runtimeTrackedChannels = snapshot.runtimeTrackedChannels,
                runtimeBoundChannels = snapshot.runtimeBoundChannels,
                runtimeTrackedRegistrations = snapshot.runtimeTrackedRegistrations,
                runtimeRegisteredPackages = snapshot.runtimeRegisteredPackages,
                runtimeDownstreamCount = snapshot.runtimeDownstreamCount,
                runtimeDeliveredCount = snapshot.runtimeDeliveredCount,
                runtimeDuplicateCount = snapshot.runtimeDuplicateCount,
                runtimeAckCount = snapshot.runtimeAckCount,
                runtimeBroadcastFallbackCount = snapshot.runtimeBroadcastFallbackCount,
                runtimeNotificationCancelCount = snapshot.runtimeNotificationCancelCount,
                runtimeNotificationCount = snapshot.runtimeNotificationCount,
                runtimeChannelCount = snapshot.runtimeChannelCount,
                runtimeAccountCount = snapshot.runtimeAccountCount,
                extra = snapshot.extra,
            ),
        )
    }
}
