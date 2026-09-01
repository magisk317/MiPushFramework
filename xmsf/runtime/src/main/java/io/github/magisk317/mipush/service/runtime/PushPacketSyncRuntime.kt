package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.*
import io.github.magisk317.xposed.logging.MagiskOtel

object PushPacketSyncRuntime {
    private const val WAIT_TYPE = "wait"
    private const val AUTH_TYPE = "auth"
    private const val CANCEL_TYPE = "cancel"
    private const val INVALID_SIG_REASON = "invalid-sig"

    private fun emitPacketSync(
        result: String,
        reason: String,
        statusOk: Boolean = true,
        count: Int? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "xmsf",
            "stage" to "packet_sync",
            "reason" to reason,
        )
        if (count != null) {
            attrs["count"] = count.toString()
        }
        MagiskOtel.event(
            name = "push.register",
            attributes = attrs,
            statusOk = statusOk,
        )
    }

    @JvmStatic
    fun resolveKick(
        kickType: String?,
        kickReason: String?
    ): PushKickPlan {
        val shouldWait = WAIT_TYPE == kickType
        val plan = PushKickPlan(
            action = if (shouldWait) PushKickAction.Rebind else PushKickAction.Close,
            eventAction = if (shouldWait) "server_kick_wait" else "server_kick_close",
            runtimeState = if (shouldWait) PushChannelState.Unbound else PushChannelState.Kicked,
            shouldCloseChannel = !shouldWait,
            shouldScheduleRebind = shouldWait,
            shouldDeactivateClient = !shouldWait,
            statusReasonCode = 0,
            statusReasonMessage = kickReason,
            statusErrorType = kickType
        )
        emitPacketSync(
            result = "ok",
            reason = plan.eventAction,
        )
        return plan
    }

    @JvmStatic
    fun resolveBindResult(
        success: Boolean,
        errorType: String?,
        errorReason: String?
    ): PushBindResultPlan {
        val plan = if (success) {
            PushBindResultPlan(
                action = PushBindAction.Bound,
                eventAction = "server_bind_success",
                runtimeState = PushChannelState.Bound,
                clientStatus = PushClientsManager.ClientStatus.binded,
                notifyType = PushClientsManager.ClientLoginInfo.TYPE_CHANNEL_OPEN_RESULT,
                statusReasonCode = 0,
                statusReasonMessage = null,
                statusErrorType = null,
                shouldScheduleRebind = false,
                shouldDeactivateClient = false,
                shouldReportInvalidSig = false
            )
        } else {
            when (errorType) {
                AUTH_TYPE -> PushBindResultPlan(
                    action = PushBindAction.Deactivate,
                    eventAction = "server_bind_auth_failed",
                    runtimeState = PushChannelState.OpenFailed,
                    clientStatus = PushClientsManager.ClientStatus.unbind,
                    notifyType = PushClientsManager.ClientLoginInfo.TYPE_CHANNEL_OPEN_RESULT,
                    statusReasonCode = 5,
                    statusReasonMessage = errorReason,
                    statusErrorType = errorType,
                    shouldScheduleRebind = false,
                    shouldDeactivateClient = true,
                    shouldReportInvalidSig = INVALID_SIG_REASON == errorReason
                )
                CANCEL_TYPE -> PushBindResultPlan(
                    action = PushBindAction.Deactivate,
                    eventAction = "server_bind_cancelled",
                    runtimeState = PushChannelState.OpenFailed,
                    clientStatus = PushClientsManager.ClientStatus.unbind,
                    notifyType = PushClientsManager.ClientLoginInfo.TYPE_CHANNEL_OPEN_RESULT,
                    statusReasonCode = 7,
                    statusReasonMessage = errorReason,
                    statusErrorType = errorType,
                    shouldScheduleRebind = false,
                    shouldDeactivateClient = true,
                    shouldReportInvalidSig = false
                )
                WAIT_TYPE -> PushBindResultPlan(
                    action = PushBindAction.Rebind,
                    eventAction = "server_bind_wait",
                    runtimeState = PushChannelState.Unbound,
                    clientStatus = PushClientsManager.ClientStatus.unbind,
                    notifyType = PushClientsManager.ClientLoginInfo.TYPE_CHANNEL_OPEN_RESULT,
                    statusReasonCode = 7,
                    statusReasonMessage = errorReason,
                    statusErrorType = errorType,
                    shouldScheduleRebind = true,
                    shouldDeactivateClient = false,
                    shouldReportInvalidSig = false
                )
                else -> PushBindResultPlan(
                    action = PushBindAction.Ignore,
                    eventAction = "server_bind_failed",
                    runtimeState = null,
                    clientStatus = null,
                    notifyType = null,
                    statusReasonCode = 0,
                    statusReasonMessage = errorReason,
                    statusErrorType = errorType,
                    shouldScheduleRebind = false,
                    shouldDeactivateClient = false,
                    shouldReportInvalidSig = false
                )
            }
        }
        emitPacketSync(
            result = if (success) "ok" else "error",
            reason = plan.eventAction,
            statusOk = success,
        )
        return plan
    }

    @JvmStatic
    fun resolveRedirect(hostsText: String?): PushRedirectPlan {
        val corePlan = io.github.magisk317.mipush.runtime.core.PushRedirectPlanFactory.resolve(
            hostsText = hostsText,
            itemSeparator = com.xiaomi.push.mpcd.Constants.ITEM_SEPARATOR,
        )
        val plan = PushRedirectPlan(
            preferredHosts = corePlan.preferredHosts,
            shouldReconnect = corePlan.shouldReconnect,
        )
        emitPacketSync(
            result = if (plan.shouldReconnect) "ok" else "skip",
            reason = if (plan.shouldReconnect) "redirect" else "no_hosts",
            count = plan.preferredHosts.size,
        )
        return plan
    }
}
