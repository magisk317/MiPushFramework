package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.magisk317.mipush.runtime.core.ConnectionStatus
import io.github.magisk317.mipush.platform.support.InternalMessenger
import com.xiaomi.push.service.XMPushServiceMessenger

class MainActivityUtils {
    private val TAG = "MainActivityUtils"
    private var messenger: InternalMessenger? = null

    fun interface ConnectionStatusChanged {
        fun onChange(status: ConnectionStatus)
    }

    fun initOnCreate(
        context: Context,
        loadConfigurations: (Context) -> Unit,
        connectionStatusChanged: ConnectionStatusChanged,
    ) {
        val appContext = context.applicationContext
        messenger = InternalMessenger(appContext).apply {
            register(IntentFilter(XMPushServiceMessenger.IntentSetConnectionStatus))
            addListener { intent ->
                val status = intent.getStringExtra("status") ?: return@addListener
                connectionStatusChanged.onChange(ConnectionStatus.valueOf(status))
            }
        }

        printHookResultForCheck()
        loadConfigurations(appContext)
        messenger?.send(Intent(XMPushServiceMessenger.IntentGetConnectionStatus))
    }

    fun printHookResultForCheck() {
        logI(String.format("[hook_res] MIUIUtils.getIsMIUI() -> [%s]", invokeStatic("com.xiaomi.channel.commonutils.android.MIUIUtils", "getIsMIUI")))
        logI(String.format("[hook_res] DeviceInfo.quicklyGetIMEI() -> [%s]", invokeStatic("com.xiaomi.channel.commonutils.android.DeviceInfo", "quicklyGetIMEI", null)))
        logI(String.format("[hook_res] DeviceInfo.getMacAddress() -> [%s]", invokeStatic("com.xiaomi.channel.commonutils.android.DeviceInfo", "getMacAddress", null)))
        logI(
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
        private val TAG: String = MainActivityUtils::class.java.simpleName
    }
}
