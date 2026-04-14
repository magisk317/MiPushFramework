package io.github.magisk317.mipush.framework.lifecycle.runtime

import com.xiaomi.push.service.PushClientsManager

typealias PushServiceCloseRequest = com.xiaomi.push.service.PushServiceCloseRequest
typealias PushServiceCloseAction = com.xiaomi.push.service.PushServiceCloseAction
typealias PushServiceClosePlan = com.xiaomi.push.service.PushServiceClosePlan
typealias PushServiceRegisterAppPlan = com.xiaomi.push.service.PushServiceRegisterAppPlan
typealias PushServiceMiPushAppAction = com.xiaomi.push.service.PushServiceMiPushAppAction
typealias PushServiceMiPushAppPlan = com.xiaomi.push.service.PushServiceMiPushAppPlan
typealias PushServiceMiPushPayloadDispatchAction = com.xiaomi.push.service.PushServiceMiPushPayloadDispatchAction
typealias PushServiceMiPushPayloadDispatchPlan = com.xiaomi.push.service.PushServiceMiPushPayloadDispatchPlan
typealias PushServiceResetConnectionAction = com.xiaomi.push.service.PushServiceResetConnectionAction
typealias PushServiceResetConnectionPlan = com.xiaomi.push.service.PushServiceResetConnectionPlan

object PushServiceIntentRuntime {
    @JvmStatic
    fun resolveCloseChannelPlan(
        request: PushServiceCloseRequest,
        packageChannelIds: List<String>,
    ): PushServiceClosePlan {
        return io.github.magisk317.mipush.service.runtime.PushServiceIntentRuntime.resolveCloseChannelPlan(request, packageChannelIds)
    }

    @JvmStatic
    fun resolveRegisterAppPlan(
        packageName: String?,
        payload: ByteArray?,
        envChanged: Boolean,
        envType: Int,
        servicePackageName: String,
    ): PushServiceRegisterAppPlan {
        return io.github.magisk317.mipush.service.runtime.PushServiceIntentRuntime.resolveRegisterAppPlan(
            packageName,
            payload,
            envChanged,
            envType,
            servicePackageName,
        )
    }

    @JvmStatic
    fun resolveMiPushAppPlan(
        action: String?,
        packageName: String?,
        payload: ByteArray?,
        cacheMessage: Boolean,
    ): PushServiceMiPushAppPlan {
        return io.github.magisk317.mipush.service.runtime.PushServiceIntentRuntime.resolveMiPushAppPlan(
            action,
            packageName,
            payload,
            cacheMessage,
        )
    }

    @JvmStatic
    fun decideMiPushPayloadDispatch(
        hasActiveChannel: Boolean,
        clientStatus: PushClientsManager.ClientStatus?,
        cacheIfUnavailable: Boolean,
    ): PushServiceMiPushPayloadDispatchPlan {
        return io.github.magisk317.mipush.service.runtime.PushServiceIntentRuntime.decideMiPushPayloadDispatch(
            hasActiveChannel,
            clientStatus,
            cacheIfUnavailable,
        )
    }

    @JvmStatic
    fun decideResetConnection(
        channelId: String?,
        requestedSecurity: String?,
        client: PushClientsManager.ClientLoginInfo?,
        connectionReadable: Boolean,
    ): PushServiceResetConnectionPlan {
        return io.github.magisk317.mipush.service.runtime.PushServiceIntentRuntime.decideResetConnection(
            channelId,
            requestedSecurity,
            client,
            connectionReadable,
        )
    }
}
