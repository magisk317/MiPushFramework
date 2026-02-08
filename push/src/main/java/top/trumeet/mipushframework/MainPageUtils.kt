package top.trumeet.mipushframework

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.elvishew.xlog.XLog
import com.magisk317.Global
import com.magisk317.service.ConnectionStatus
import com.magisk317.InternalMessenger
import com.magisk317.service.XMPushServiceListener
import com.xiaomi.push.service.XMPushServiceMessenger

class MainPageUtils {
    private val logger = XLog.tag("MainPageUtils").build()
    private var messenger: InternalMessenger? = null

    fun interface ConnectionStatusChanged {
        fun onChange(status: ConnectionStatus)
    }

    fun initOnCreate(context: Context, connectionStatusChanged: ConnectionStatusChanged) {
        val appContext = context.applicationContext
        messenger = InternalMessenger(appContext).apply {
            register(IntentFilter(XMPushServiceMessenger.IntentSetConnectionStatus))
            addListener { intent ->
                val status = intent.getStringExtra("status") ?: return@addListener
                connectionStatusChanged.onChange(ConnectionStatus.valueOf(status))
            }
        }

        printHookResultForCheck()
        Global.ConfigCenter().loadConfigurations(appContext)
        messenger?.send(Intent(XMPushServiceMessenger.IntentGetConnectionStatus))
    }

    fun printHookResultForCheck() {
        logger.i(String.format("[hook_res] MIUIUtils.getIsMIUI() -> [%s]", invokeStatic("com.xiaomi.channel.commonutils.android.MIUIUtils", "getIsMIUI")))
        logger.i(String.format("[hook_res] DeviceInfo.quicklyGetIMEI() -> [%s]", invokeStatic("com.xiaomi.channel.commonutils.android.DeviceInfo", "quicklyGetIMEI", null)))
        logger.i(String.format("[hook_res] DeviceInfo.getMacAddress() -> [%s]", invokeStatic("com.xiaomi.channel.commonutils.android.DeviceInfo", "getMacAddress", null)))
        logger.i(
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
