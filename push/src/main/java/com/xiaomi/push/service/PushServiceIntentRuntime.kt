package com.xiaomi.push.service

data class PushServiceCloseRequest(
    val packageName: String?,
    val channelId: String?,
    val userId: String?
)

enum class PushServiceCloseAction {
    Ignore,
    ClosePackageChannels,
    CloseChannel,
    CloseSingleUserChannel
}

data class PushServiceClosePlan(
    val action: PushServiceCloseAction,
    val channelIds: List<String> = emptyList(),
    val userId: String? = null
)

data class PushServiceRegisterAppPlan(
    val packageName: String?,
    val payload: ByteArray?,
    val shouldClearAccountCache: Boolean,
    val envType: Int
)

enum class PushServiceMiPushAppAction {
    Unsupported,
    SendMessage,
    Unregister
}

data class PushServiceMiPushAppPlan(
    val action: PushServiceMiPushAppAction,
    val packageName: String?,
    val payload: ByteArray?,
    val cacheMessage: Boolean
)

enum class PushServiceMiPushPayloadDispatchAction {
    Drop,
    QueueOnly,
    SendNow
}

data class PushServiceMiPushPayloadDispatchPlan(
    val action: PushServiceMiPushPayloadDispatchAction,
    val eventAction: String
)

enum class PushServiceResetConnectionAction {
    Ignore,
    Reset
}

data class PushServiceResetConnectionPlan(
    val action: PushServiceResetConnectionAction,
    val reason: String
)

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
                channelIds = listOf(request.channelId)
            )
            else -> PushServiceClosePlan(
                action = PushServiceCloseAction.CloseSingleUserChannel,
                channelIds = listOf(request.channelId),
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
        return when {
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
    }

    @JvmStatic
    fun decideResetConnection(
        channelId: String?,
        requestedSecurity: String?,
        client: PushClientsManager.ClientLoginInfo?,
        connectionReadable: Boolean
    ): PushServiceResetConnectionPlan {
        return when {
            channelId.isNullOrBlank() -> PushServiceResetConnectionPlan(
                action = PushServiceResetConnectionAction.Ignore,
                reason = "missing_channel"
            )
            client == null -> PushServiceResetConnectionPlan(
                action = PushServiceResetConnectionAction.Ignore,
                reason = "missing_client"
            )
            client.security != requestedSecurity -> PushServiceResetConnectionPlan(
                action = PushServiceResetConnectionAction.Ignore,
                reason = "security_mismatch"
            )
            client.status != PushClientsManager.ClientStatus.binded -> PushServiceResetConnectionPlan(
                action = PushServiceResetConnectionAction.Ignore,
                reason = "client_not_bound"
            )
            connectionReadable -> PushServiceResetConnectionPlan(
                action = PushServiceResetConnectionAction.Ignore,
                reason = "connection_alive"
            )
            else -> PushServiceResetConnectionPlan(
                action = PushServiceResetConnectionAction.Reset,
                reason = "stale_connection"
            )
        }
    }
}
