package io.github.magisk317.mipush.service.runtime

import android.content.Context
import android.content.Intent
import android.os.Messenger
import androidx.core.content.IntentCompat
import com.xiaomi.channel.commonutils.string.MD5
import com.xiaomi.push.service.*
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.xposed.logging.MagiskOtel

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
            messenger = IntentCompat.getParcelableExtra(intent, PushConstants.EXTRA_MESSENGER, Messenger::class.java),
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
        val plan = io.github.magisk317.mipush.runtime.core.PushChannelOpenPlanFactory.planRebind(
            channelId = channelId,
            existingSession = existingSession,
            requestedSession = requestedSession,
            existingSecurity = existingSecurity,
            requestedSecurity = requestedSecurity,
        )
        if (plan.sessionChanged) {
            safeWarn(
                "session changed. old hash=${MD5.MD5_32(existingSession.orEmpty())}, new hash=${MD5.MD5_32(requestedSession.orEmpty())} chid = $channelId"
            )
        }
        if (plan.securityChanged) {
            safeWarn(
                "security changed. chid = $channelId sechash = ${MD5.MD5_32(requestedSecurity ?: "")}"
            )
        }
        return plan.shouldRebind
    }

    @JvmStatic
    fun decideOpenPlan(
        hasNetwork: Boolean,
        isConnected: Boolean,
        clientStatus: PushClientsManager.ClientStatus?,
        shouldRebind: Boolean
    ): PushChannelOpenPlan {
        val bindingState = when (clientStatus ?: PushClientsManager.ClientStatus.unbind) {
            PushClientsManager.ClientStatus.unbind -> io.github.magisk317.mipush.runtime.core.PushBindingState.Unbound
            PushClientsManager.ClientStatus.binding -> io.github.magisk317.mipush.runtime.core.PushBindingState.Binding
            PushClientsManager.ClientStatus.binded -> io.github.magisk317.mipush.runtime.core.PushBindingState.Bound
        }
        val corePlan = io.github.magisk317.mipush.runtime.core.PushChannelOpenPlanFactory.planOpen(
            hasNetwork = hasNetwork,
            isConnected = isConnected,
            bindingState = bindingState,
            shouldRebind = shouldRebind,
        )
        val plan = PushChannelOpenPlan(
            action = PushChannelOpenAction.valueOf(corePlan.action.name),
            state = corePlan.state,
            sourceSuffix = corePlan.sourceSuffix,
            reasonCode = corePlan.reasonCode,
            reasonMessage = corePlan.reasonMessage,
        )
        MagiskOtel.event(
            name = "push.lifecycle",
            attributes = mapOf(
                "result" to if (plan.action == PushChannelOpenAction.OpenFailedNoNetwork) "error" else "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "channel_open_plan",
                "reason" to plan.sourceSuffix,
                "source" to plan.action.name,
            ),
            statusOk = plan.action != PushChannelOpenAction.OpenFailedNoNetwork,
        )
        return plan
    }

    @JvmStatic
    fun applyClientUpdate(
        client: PushClientsManager.ClientLoginInfo,
        request: PushChannelOpenRequest,
        clientEventDispatcher: ClientEventDispatcher,
        context: Context
    ) {
        client.chid = request.channelId.orEmpty()
        client.userId = request.userId.orEmpty()
        client.token = request.token.orEmpty()
        client.pkgName = request.packageName.orEmpty()
        client.clientExtra = request.clientExtra.orEmpty()
        client.cloudExtra = request.cloudExtra.orEmpty()
        client.kick = request.kick
        client.security = request.security.orEmpty()
        client.session = request.session.orEmpty()
        client.authMethod = request.authMethod.orEmpty()
        client.mClientEventDispatcher = clientEventDispatcher
        client.watch(request.messenger)
        client.context = context
    }

    private fun safeWarn(message: String) {
        runCatching { logW(message) }
    }
}
