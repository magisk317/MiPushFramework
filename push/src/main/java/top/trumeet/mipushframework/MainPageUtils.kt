package top.trumeet.mipushframework

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.nihility.Global
import com.nihility.InternalMessenger
import com.nihility.service.XMPushServiceListener
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.smack.ConnectionConfiguration

class MainPageUtils {
    private var messenger: InternalMessenger? = null

    fun interface ConnectionStatusChanged {
        fun onChange(status: XMPushServiceListener.ConnectionStatus)
    }

    fun initOnCreate(context: Context, connectionStatusChanged: ConnectionStatusChanged) {
        val appContext = context.applicationContext
        messenger = InternalMessenger(appContext).apply {
            register(IntentFilter(XMPushServiceMessenger.IntentSetConnectionStatus))
            addListener { intent ->
                val status = intent.getStringExtra("status") ?: return@addListener
                connectionStatusChanged.onChange(XMPushServiceListener.ConnectionStatus.valueOf(status))
            }
        }

        printHookResultForCheck()
        Global.ConfigCenter().loadConfigurations(appContext)
        messenger?.send(Intent(XMPushServiceMessenger.IntentGetConnectionStatus))
    }

    fun printHookResultForCheck() {
        Log.i(TAG, String.format("[hook_res] MIUIUtils.getIsMIUI() -> [%s]", MIUIUtils.getIsMIUI()))
        Log.i(TAG, String.format("[hook_res] DeviceInfo.quicklyGetIMEI() -> [%s]", DeviceInfo.quicklyGetIMEI(null)))
        Log.i(TAG, String.format("[hook_res] DeviceInfo.getMacAddress() -> [%s]", DeviceInfo.getMacAddress(null)))
        Log.i(
            TAG,
            String.format(
                "[hook_res] ConnectionConfiguration.getXmppServerHost() -> [%s]",
                ConnectionConfiguration.getXmppServerHost()
            )
        )
    }

    companion object {
        private val TAG: String = MainPageUtils::class.java.simpleName
    }
}
