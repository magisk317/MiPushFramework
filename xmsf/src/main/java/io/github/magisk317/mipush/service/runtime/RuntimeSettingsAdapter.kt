package io.github.magisk317.mipush.service.runtime

import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.smack.ConnectionConfiguration
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.manager.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.network.NetworkPolicyCompat
import io.github.magisk317.mipush.platform.support.InternalMessenger
import io.github.magisk317.mipush.platform.support.PushServiceBroadcastActions
import kotlinx.coroutines.runBlocking

class RuntimeSettingsAdapter constructor(
    private val appContext: Context,
    private val configCenter: ConfigCenter,
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
}
