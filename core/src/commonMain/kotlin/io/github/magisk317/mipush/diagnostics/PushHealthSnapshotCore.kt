package io.github.magisk317.mipush.diagnostics

/** Platform-neutral health snapshot values collected by the XMSF diagnostics adapter. */
data class PushHealthSnapshot(
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
    val extra: String?,
)

/** Deterministic health snapshot formatting; snapshot collection and logging remain adapters. */
object PushHealthSnapshotFormatter {
    fun format(snapshot: PushHealthSnapshot): String {
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
