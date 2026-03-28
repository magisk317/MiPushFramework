package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.os.Messenger
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.MD5
import com.xiaomi.xmsf.runtime.PushChannelState

data class PushChannelOpenRequest(
    val channelId: String?,
    val userId: String?,
    val token: String?,
    val packageName: String?,
    val clientExtra: String?,
    val cloudExtra: String?,
    val kick: Boolean,
    val security: String?,
    val session: String?,
    val authMethod: String?,
    val messenger: Messenger?
)

enum class PushChannelOpenAction {
    OpenFailedNoNetwork,
    ScheduleConnect,
    Bind,
    Rebind,
    AlreadyBinding,
    AlreadyBound,
    NoAction
}

data class PushChannelOpenPlan(
    val action: PushChannelOpenAction,
    val state: PushChannelState,
    val sourceSuffix: String,
    val reasonCode: Int? = null,
    val reasonMessage: String? = null
)

object PushChannelOpenRuntime {
    @JvmStatic
    fun requestFromIntent(intent: Intent): PushChannelOpenRequest {
        return PushChannelOpenRequest(
            channelId = intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID),
            userId = intent.getStringExtra(PushConstants.EXTRA_USER_ID),
            token = intent.getStringExtra(PushConstants.EXTRA_TOKEN),
            packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME),
            clientExtra = intent.getStringExtra(PushConstants.EXTRA_CLIENT_ATTR),
            cloudExtra = intent.getStringExtra(PushConstants.EXTRA_CLOUD_ATTR),
            kick = intent.getBooleanExtra(PushConstants.EXTRA_KICK, false),
            security = intent.getStringExtra(PushConstants.EXTRA_SECURITY),
            session = intent.getStringExtra(PushConstants.EXTRA_SESSION),
            authMethod = intent.getStringExtra(PushConstants.EXTRA_AUTH_METHOD),
            messenger = intent.getParcelableExtra(PushConstants.EXTRA_MESSENGER)
        )
    }

    @JvmStatic
    fun shouldRebind(
        existingClient: PushClientsManager.ClientLoginInfo?,
        request: PushChannelOpenRequest
    ): Boolean {
        return shouldRebind(
            channelId = request.channelId,
            existingSession = existingClient?.session,
            requestedSession = request.session,
            existingSecurity = existingClient?.security,
            requestedSecurity = request.security
        )
    }

    @JvmStatic
    fun shouldRebind(
        channelId: String?,
        existingSession: String?,
        requestedSession: String?,
        existingSecurity: String?,
        requestedSecurity: String?
    ): Boolean {
        if (channelId.isNullOrBlank()) {
            return false
        }
        var shouldRebind = false
        if (!existingSession.isNullOrEmpty() && existingSession != requestedSession) {
            safeWarn(
                "session changed. old session=$existingSession, new session=$requestedSession chid = $channelId"
            )
            shouldRebind = true
        }
        if (requestedSecurity != existingSecurity) {
            safeWarn(
                "security changed. chid = $channelId sechash = ${MD5.MD5_32(requestedSecurity ?: "")}"
            )
            shouldRebind = true
        }
        return shouldRebind
    }

    @JvmStatic
    fun decideOpenPlan(
        hasNetwork: Boolean,
        isConnected: Boolean,
        clientStatus: PushClientsManager.ClientStatus?,
        shouldRebind: Boolean
    ): PushChannelOpenPlan {
        val effectiveStatus = clientStatus ?: PushClientsManager.ClientStatus.unbind
        return when {
            !hasNetwork -> PushChannelOpenPlan(
                action = PushChannelOpenAction.OpenFailedNoNetwork,
                state = PushChannelState.OpenFailed,
                sourceSuffix = "no_network",
                reasonCode = 2,
                reasonMessage = "network_unavailable"
            )
            !isConnected -> PushChannelOpenPlan(
                action = PushChannelOpenAction.ScheduleConnect,
                state = PushChannelState.Binding,
                sourceSuffix = "schedule_connect"
            )
            effectiveStatus == PushClientsManager.ClientStatus.unbind -> PushChannelOpenPlan(
                action = PushChannelOpenAction.Bind,
                state = PushChannelState.Binding,
                sourceSuffix = "bind"
            )
            shouldRebind -> PushChannelOpenPlan(
                action = PushChannelOpenAction.Rebind,
                state = PushChannelState.Binding,
                sourceSuffix = "rebind"
            )
            effectiveStatus == PushClientsManager.ClientStatus.binding -> PushChannelOpenPlan(
                action = PushChannelOpenAction.AlreadyBinding,
                state = PushChannelState.Binding,
                sourceSuffix = "already_binding"
            )
            effectiveStatus == PushClientsManager.ClientStatus.binded -> PushChannelOpenPlan(
                action = PushChannelOpenAction.AlreadyBound,
                state = PushChannelState.Bound,
                sourceSuffix = "already_bound"
            )
            else -> PushChannelOpenPlan(
                action = PushChannelOpenAction.NoAction,
                state = PushChannelState.Unbound,
                sourceSuffix = "noop"
            )
        }
    }

    @JvmStatic
    fun applyClientUpdate(
        client: PushClientsManager.ClientLoginInfo,
        request: PushChannelOpenRequest,
        clientEventDispatcher: ClientEventDispatcher,
        context: Context
    ) {
        client.chid = request.channelId
        client.userId = request.userId
        client.token = request.token
        client.pkgName = request.packageName
        client.clientExtra = request.clientExtra
        client.cloudExtra = request.cloudExtra
        client.kick = request.kick
        client.security = request.security
        client.session = request.session
        client.authMethod = request.authMethod
        client.mClientEventDispatcher = clientEventDispatcher
        client.watch(request.messenger)
        client.context = context
    }

    private fun safeWarn(message: String) {
        runCatching { MyLog.w(message) }
    }
}
