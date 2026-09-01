package io.github.magisk317.mipush.service.runtime
import com.xiaomi.push.service.*
import io.github.magisk317.xposed.logging.MagiskOtel
import com.xiaomi.smack.packet.*
import com.xiaomi.smack.*
import com.xiaomi.slim.*
import com.xiaomi.push.service.timers.*
import com.xiaomi.push.service.*

object PushServiceIntentRuntime {
    @JvmStatic
    fun resolveCloseChannelPlan(
        request: PushServiceCloseRequest,
        packageChannelIds: List<String>
    ): PushServiceClosePlan {
        return when {
            request.channelId.isNullOrBlank() -> {
                if (packageChannelIds.isEmpty()) {
                    PushServiceClosePlan(PushServiceCloseAction.Ignore)
                } else {
                    PushServiceClosePlan(
                        action = PushServiceCloseAction.ClosePackageChannels,
                        channelIds = packageChannelIds
                    )
                }
            }
            request.userId.isNullOrBlank() -> PushServiceClosePlan(
                action = PushServiceCloseAction.CloseChannel,
                channelIds = listOfNotNull(request.channelId)
            )
            else -> PushServiceClosePlan(
                action = PushServiceCloseAction.CloseSingleUserChannel,
                channelIds = listOfNotNull(request.channelId),
                userId = request.userId
            )
        }
    }

    @JvmStatic
    fun resolveRegisterAppPlan(
        packageName: String?,
        payload: ByteArray?,
        envChanged: Boolean,
        envType: Int,
        servicePackageName: String
    ): PushServiceRegisterAppPlan {
        return PushServiceRegisterAppPlan(
            packageName = packageName,
            payload = payload,
            shouldClearAccountCache = envChanged && servicePackageName != PushConstants.PUSH_SERVICE_PACKAGE_NAME,
            envType = envType
        )
    }

    @JvmStatic
    fun resolveMiPushAppPlan(
        action: String?,
        packageName: String?,
        payload: ByteArray?,
        cacheMessage: Boolean
    ): PushServiceMiPushAppPlan {
        val resolvedAction = when (action) {
            PushConstants.MIPUSH_ACTION_SEND_MESSAGE -> PushServiceMiPushAppAction.SendMessage
            PushConstants.MIPUSH_ACTION_UNREGISTER_APP -> PushServiceMiPushAppAction.Unregister
            else -> PushServiceMiPushAppAction.Unsupported
        }
        return PushServiceMiPushAppPlan(
            action = resolvedAction,
            packageName = packageName,
            payload = payload,
            cacheMessage = cacheMessage
        )
    }

    @JvmStatic
    fun decideMiPushPayloadDispatch(
        hasActiveChannel: Boolean,
        clientStatus: PushClientsManager.ClientStatus?,
        cacheIfUnavailable: Boolean
    ): PushServiceMiPushPayloadDispatchPlan {
        val plan = when {
            hasActiveChannel && clientStatus == PushClientsManager.ClientStatus.binded -> {
                PushServiceMiPushPayloadDispatchPlan(
                    action = PushServiceMiPushPayloadDispatchAction.SendNow,
                    eventAction = "mipush_payload_send_now"
                )
            }
            cacheIfUnavailable -> {
                PushServiceMiPushPayloadDispatchPlan(
                    action = PushServiceMiPushPayloadDispatchAction.QueueOnly,
                    eventAction = if (hasActiveChannel) {
                        "mipush_payload_queue_wait_bind"
                    } else {
                        "mipush_payload_queue_wait_channel"
                    }
                )
            }
            else -> {
                PushServiceMiPushPayloadDispatchPlan(
                    action = PushServiceMiPushPayloadDispatchAction.Drop,
                    eventAction = if (hasActiveChannel) {
                        "mipush_payload_drop_unbound"
                    } else {
                        "mipush_payload_drop_no_channel"
                    }
                )
            }
        }
        MagiskOtel.event(
            name = "push.dispatch",
            attributes = mapOf(
                "result" to when (plan.action) {
                    PushServiceMiPushPayloadDispatchAction.SendNow -> "ok"
                    PushServiceMiPushPayloadDispatchAction.QueueOnly -> "skip"
                    PushServiceMiPushPayloadDispatchAction.Drop -> "skip"
                },
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "payload_dispatch_plan",
                "reason" to plan.eventAction,
            ),
            statusOk = plan.action != PushServiceMiPushPayloadDispatchAction.Drop,
        )
        return plan
    }

    @JvmStatic
    fun decideResetConnection(
        channelId: String?,
        requestedSecurity: String?,
        client: PushClientsManager.ClientLoginInfo?,
        connectionReadable: Boolean
    ): PushServiceResetConnectionPlan {
        val corePlan = io.github.magisk317.mipush.runtime.core.PushServiceResetConnectionPlanFactory.planReset(
            hasChannelId = !channelId.isNullOrBlank(),
            hasClient = client != null,
            securityMatches = client?.security == requestedSecurity,
            isClientBound = client?.status == PushClientsManager.ClientStatus.binded,
            connectionReadable = connectionReadable,
        )
        return PushServiceResetConnectionPlan(
            action = PushServiceResetConnectionAction.valueOf(corePlan.action.name),
            reason = corePlan.reason,
        )
    }
}
