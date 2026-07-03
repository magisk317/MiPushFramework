package io.github.magisk317.mipush.service.runtime

import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.push.sdk.PushMessageProcessor
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.XMPushServiceProxy
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.smack.SmackConfiguration
import com.xiaomi.smack.SocketConnection
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.common.manager.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.network.NetworkPolicyCompat
import io.github.magisk317.mipush.platform.support.InternalMessenger
import io.github.magisk317.mipush.platform.support.PushServiceBroadcastActions
import io.github.magisk317.mipush.runtime.PushRuntime
import kotlinx.coroutines.runBlocking

class RuntimeSettingsAdapter constructor(
    private val appContext: Context,
    private val configCenter: ConfigCenter,
    private val pushMessageProcessor: PushMessageProcessor,
) {
    fun startMiPushServiceAsForegroundService(context: Context = appContext) {
        InternalMessenger(context).send(Intent(PushServiceBroadcastActions.START_FOREGROUND))
    }

    fun sendXmppReconnectRequest(context: Context = appContext) {
        InternalMessenger(context).send(Intent(PushConstants.ACTION_RESET_CONNECTION))
    }

    fun setXmppServer(context: Context = appContext, newHost: String) {
        runBlocking { configCenter.setXMPPServerAsync(newHost) }
        NetworkPolicyCompat.applyXmppHostOverride(context.applicationContext)
        sendXmppReconnectRequest(context)
    }

    fun getXmppServerHint(): String {
        return ConnectionConfiguration.getXmppServerHost() + ":" + PushServiceConstants.XMPP_SERVER_PORT
    }

    fun getRuntimeEnvironmentSnapshot(context: Context = appContext): ManagerRuntimeEnvironmentSnapshot {
        val appContext = context.applicationContext
        return ManagerRuntimeEnvironmentSnapshot(
            isMiui = MIUIUtils.getIsMIUI(),
            imei = DeviceInfo.quicklyGetIMEI(appContext),
            macAddress = DeviceInfo.getMacAddress(appContext),
            xmppServerHost = ConnectionConfiguration.getXmppServerHost(),
        )
    }

    fun resetTopActivityCache() {
        pushMessageProcessor.resetTopActivityCache()
    }

    fun getConnectionSnapshot(): ManagerConnectionSnapshot {
        val snapshot = PushRuntime.connectionSnapshot()
        val sanitizedLastDisconnected = if (
            snapshot.connectionState == "Connected" &&
            snapshot.lastDisconnectedAtMs > snapshot.connectedAtMs &&
            snapshot.connectedAtMs > 0L
        ) {
            0L
        } else {
            snapshot.lastDisconnectedAtMs
        }
        val resolvedIp = snapshot.resolvedIp ?: runCatching {
            val service = XMPushServiceProxy.get()
            val connection = service?.currentConnection
            (connection as? SocketConnection)?.resolvedIp
        }.getOrNull()

        return ManagerConnectionSnapshot(
            connectionState = snapshot.connectionState,
            connectedAtMs = snapshot.connectedAtMs,
            lastDisconnectedAtMs = sanitizedLastDisconnected,
            connectionSessionCount = snapshot.connectionSessionCount,
            serverHost = snapshot.serverHost ?: ConnectionConfiguration.getXmppServerHost(),
            serverIp = resolvedIp,
            keepAliveIntervalMs = SmackConfiguration.keepAliveInterval,
            pingIntervalMs = SmackConfiguration.pingInterval,
            downstreamMessageCount = snapshot.downstreamMessageCount,
            deliveredToAppCount = snapshot.deliveredToAppCount,
            duplicateMessageCount = snapshot.duplicateMessageCount,
            ackMessageCount = snapshot.ackMessageCount,
            registeredPackageCount = snapshot.registeredPackageCount,
            trackedChannelCount = snapshot.trackedChannelCount,
            boundChannelCount = snapshot.boundChannelCount,
        )
    }
}
