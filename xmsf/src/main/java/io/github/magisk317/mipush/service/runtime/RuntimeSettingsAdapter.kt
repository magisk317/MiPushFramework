package io.github.magisk317.mipush.service.runtime

import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.smack.ConnectionConfiguration
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.network.NetworkPolicyCompat
import io.github.magisk317.mipush.platform.support.InternalMessenger
import kotlinx.coroutines.runBlocking

class RuntimeSettingsAdapter constructor(
    private val appContext: Context,
    private val configCenter: ConfigCenter,
) {
    fun startMiPushServiceAsForegroundService(context: Context = appContext) {
        InternalMessenger(context).send(Intent(XMPushServiceMessenger.IntentStartForeground))
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
}
