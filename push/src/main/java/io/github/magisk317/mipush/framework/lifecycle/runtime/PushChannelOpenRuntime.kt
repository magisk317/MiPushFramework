package io.github.magisk317.mipush.framework.lifecycle.runtime

import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.ClientEventDispatcher
import com.xiaomi.push.service.PushClientsManager

typealias PushChannelOpenRequest = com.xiaomi.push.service.PushChannelOpenRequest
typealias PushChannelOpenAction = com.xiaomi.push.service.PushChannelOpenAction
typealias PushChannelOpenPlan = com.xiaomi.push.service.PushChannelOpenPlan

object PushChannelOpenRuntime {
    @JvmStatic
    fun requestFromIntent(intent: Intent): PushChannelOpenRequest {
        return io.github.magisk317.mipush.service.runtime.PushChannelOpenRuntime.requestFromIntent(intent)
    }

    @JvmStatic
    fun shouldRebind(
        existingClient: PushClientsManager.ClientLoginInfo?,
        request: PushChannelOpenRequest,
    ): Boolean {
        return io.github.magisk317.mipush.service.runtime.PushChannelOpenRuntime.shouldRebind(existingClient, request)
    }

    @JvmStatic
    fun shouldRebind(
        channelId: String?,
        existingSession: String?,
        requestedSession: String?,
        existingSecurity: String?,
        requestedSecurity: String?,
    ): Boolean {
        return io.github.magisk317.mipush.service.runtime.PushChannelOpenRuntime.shouldRebind(
            channelId,
            existingSession,
            requestedSession,
            existingSecurity,
            requestedSecurity,
        )
    }

    @JvmStatic
    fun decideOpenPlan(
        hasNetwork: Boolean,
        isConnected: Boolean,
        clientStatus: PushClientsManager.ClientStatus?,
        shouldRebind: Boolean,
    ): PushChannelOpenPlan {
        return io.github.magisk317.mipush.service.runtime.PushChannelOpenRuntime.decideOpenPlan(
            hasNetwork,
            isConnected,
            clientStatus,
            shouldRebind,
        )
    }

    @JvmStatic
    fun applyClientUpdate(
        client: PushClientsManager.ClientLoginInfo,
        request: PushChannelOpenRequest,
        clientEventDispatcher: ClientEventDispatcher,
        context: Context,
    ) {
        io.github.magisk317.mipush.service.runtime.PushChannelOpenRuntime.applyClientUpdate(
            client,
            request,
            clientEventDispatcher,
            context,
        )
    }
}
