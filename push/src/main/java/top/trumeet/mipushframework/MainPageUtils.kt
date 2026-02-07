package top.trumeet.mipushframework

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.nihility.Global
import com.nihility.InternalMessenger
import com.nihility.service.XMPushServiceListener
import com.xiaomi.push.service.XMPushServiceMessenger

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
        Log.i(TAG, String.format("[hook_res] MIUIUtils.getIsMIUI() -> [%s]", invokeStatic("com.xiaomi.channel.commonutils.android.MIUIUtils", "getIsMIUI")))
        Log.i(TAG, String.format("[hook_res] DeviceInfo.quicklyGetIMEI() -> [%s]", invokeStatic("com.xiaomi.channel.commonutils.android.DeviceInfo", "quicklyGetIMEI", null)))
        Log.i(TAG, String.format("[hook_res] DeviceInfo.getMacAddress() -> [%s]", invokeStatic("com.xiaomi.channel.commonutils.android.DeviceInfo", "getMacAddress", null)))
        Log.i(
            TAG,
            String.format(
                "[hook_res] ConnectionConfiguration.getXmppServerHost() -> [%s]",
                invokeStatic("com.xiaomi.smack.ConnectionConfiguration", "getXmppServerHost")
            )
        )
    }

    private fun invokeStatic(className: String, methodName: String, vararg args: Any?): Any? {
        return runCatching {
            val clazz = Class.forName(className)
            val method = clazz.methods.firstOrNull {
                it.name == methodName && it.parameterTypes.size == args.size
            } ?: return "<method_missing>"
            method.invoke(null, *args)
        }.getOrElse { "<unavailable>" }
    }

    companion object {
        private val TAG: String = MainPageUtils::class.java.simpleName
    }
}
