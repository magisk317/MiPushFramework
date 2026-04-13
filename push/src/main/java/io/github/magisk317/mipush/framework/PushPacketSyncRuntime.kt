package io.github.magisk317.mipush.framework

import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.network.HostManager
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushKickPlan
import com.xiaomi.push.service.PushBindResultPlan
import com.xiaomi.push.service.PushRedirectPlan
import com.xiaomi.push.service.PushKickAction
import com.xiaomi.push.service.PushBindAction
import com.xiaomi.push.service.PushChannelState

@Suppress("NAME_SHADOWING")
object PushPacketSyncRuntime {
    fun resolveKick(kickType: String?, kickReason: String?): PushKickPlan {
        val shouldWait = "wait".equals(kickType, ignoreCase = true)
        return PushKickPlan(
            action = if (shouldWait) PushKickAction.Rebind else PushKickAction.Close,
            eventAction = if (shouldWait) "wait_rebind" else "kicked",
            runtimeState = if (shouldWait) PushChannelState.Unbound else PushChannelState.Kicked,
            shouldCloseChannel = !shouldWait,
            shouldScheduleRebind = shouldWait,
            shouldDeactivateClient = !"kick".equals(kickType, ignoreCase = true),
            statusReasonCode = 3,
            statusReasonMessage = kickReason,
            statusErrorType = kickType
        )
    }

    fun resolveBindResult(success: Boolean, errorType: String?, errorReason: String?): PushBindResultPlan {
        if (!success) {
            val isAuth = "auth".equals(errorType, ignoreCase = true)
            val isWait = "wait".equals(errorType, ignoreCase = true)
            
            return PushBindResultPlan(
                action = if (isWait) PushBindAction.Rebind else PushBindAction.Deactivate,
                eventAction = "com.xiaomi.push.channel_open_failed",
                runtimeState = PushChannelState.Unbound,
                clientStatus = PushClientsManager.ClientStatus.unbind,
                notifyType = 1,
                statusReasonCode = 1,
                statusReasonMessage = errorReason,
                statusErrorType = errorType,
                shouldScheduleRebind = isWait,
                shouldDeactivateClient = !isWait,
                shouldReportInvalidSig = isAuth && "invalid-sig".equals(errorReason, ignoreCase = true)
            )
        } else {
            return PushBindResultPlan(
                action = PushBindAction.Bound,
                eventAction = "com.xiaomi.push.channel_opened",
                runtimeState = PushChannelState.Bound,
                clientStatus = PushClientsManager.ClientStatus.binded,
                notifyType = 1,
                statusReasonCode = 0,
                statusReasonMessage = null,
                statusErrorType = null,
                shouldScheduleRebind = false,
                shouldDeactivateClient = false,
                shouldReportInvalidSig = false
            )
        }
    }

    fun resolveRedirect(hostsText: String?): PushRedirectPlan {
        if (TextUtils.isEmpty(hostsText)) {
            return PushRedirectPlan(emptyList(), false)
        }
        val hostsArr = hostsText!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (hostsArr.isEmpty()) {
            return PushRedirectPlan(emptyList(), false)
        }
        val fallbacks = HostManager.getInstance().getFallbacksByHost(ConnectionConfiguration.getXmppServerHost(), false)
        if (fallbacks == null) {
            return PushRedirectPlan(hostsArr.toList(), true)
        }
        return PushRedirectPlan(hostsArr.toList(), true)
    }
}
