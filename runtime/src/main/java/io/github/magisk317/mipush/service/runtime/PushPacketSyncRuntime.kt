package io.github.magisk317.mipush.service.runtime
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.push.service.*

object PushPacketSyncRuntime {
    private const val WAIT_TYPE = "wait"
    private const val AUTH_TYPE = "auth"
    private const val CANCEL_TYPE = "cancel"
    private const val INVALID_SIG_REASON = "invalid-sig"

    @JvmStatic
    fun resolveKick(
        kickType: String?,
        kickReason: String?
    ): PushKickPlan {
        val shouldWait = WAIT_TYPE == kickType
        return PushKickPlan(
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
    }

    @JvmStatic
    fun resolveBindResult(
        success: Boolean,
        errorType: String?,
        errorReason: String?
    ): PushBindResultPlan {
        if (success) {
            return PushBindResultPlan(
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
        }
        return when (errorType) {
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

    @JvmStatic
    fun resolveRedirect(hostsText: String?): PushRedirectPlan {
        val preferredHosts = hostsText
            ?.split(com.xiaomi.push.mpcd.Constants.ITEM_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return PushRedirectPlan(
            preferredHosts = preferredHosts,
            shouldReconnect = preferredHosts.isNotEmpty()
        )
    }
}
