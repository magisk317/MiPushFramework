package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.network.NetworkPolicyCompat
import io.github.magisk317.mipush.hook.Configurations
import io.github.magisk317.mipush.hook.Dependencies
import io.github.magisk317.mipush.hook.HookedMethodHandler
import io.github.magisk317.mipush.hook.OuterDependencies
import io.github.magisk317.mipush.service.XMPushServiceAbility
import io.github.magisk317.mipush.service.XMPushServiceListener
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.network.HostManager
import com.xiaomi.push.service.XMPushService
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.smack.SmackConfiguration
import kotlinx.coroutines.runBlocking

object Hooker {

    @JvmStatic
    fun hook(context: Context) {
        logD("Hooker.hook() called")
        runCatching {
            logD("Initializing MiPushHookLib...")
            initMiPushHookLib(context)
            logD("Hooking MiPushSDK...")
            hookMiPushSDK(context)
            logD("Hooker.hook() finished successfully")
        }.onFailure {
            logE("Hook init skipped/failed: ${it.message}", it)
        }
    }

    @JvmStatic
    fun setLogger(context: Context) {
        runCatching {
            val logger = buildMiSDKLogger()
            initMiSdkLogger(logger)
            initPushLogger(context, logger)
        }.onFailure {
            logE("Push logger init skipped: ${it.message}", it)
        }
    }

    private fun initMiPushHookLib(context: Context) {
        val configurations = object : Configurations {
            override fun getXMPPServer(): String =
                runBlocking { Global.configCenter().getXMPPServerAsync() }.orEmpty()
        }
        Dependencies.set(object : OuterDependencies {
            override fun configuration(): Configurations = configurations

            override fun serviceListener(pushService: XMPushService): XMPushServiceListener =
                XMPushServiceAbility(pushService)

            override fun hookedMethodHandler(): HookedMethodHandler = Global.hookHandler()
        })
    }

    private fun hookMiPushSDK(context: Context) {
        try {
            hookField(SmackConfiguration::class.java, "pingInterval", 3 * 60 * 1000)
            hookMiPushServerHost()
            NetworkPolicyCompat.applyAll(context.applicationContext)
        } catch (e: Throwable) {
            logE(e.message, e)
        }
    }

    private fun hookMiPushServerHost() {
        addReservedHost(
            ConnectionConfiguration.XMPP_SERVER_CHINA_HOST_P,
            arrayOf(
                ConnectionConfiguration.XMPP_SERVER_CHINA_HOST_P,
                "220.181.106.151:5222",
                "220.181.106.151:443",
                "220.181.106.152:5222",
                "118.26.252.226:443",
                "118.26.252.225:443",
                "58.83.177.235:5222",
                "58.83.177.220:5222"
            )
        )

        val resolver = "resolver.msg.xiaomi.net"
        addReservedHost(
            resolver,
            arrayOf(
                resolver,
                "111.13.142.153:5222",
                "118.26.252.209:5222",
                "39.156.150.162:5222",
                "111.13.142.153:80",
                "39.156.150.162:80",
                "123.125.102.48:5222",
                "220.181.106.150:5222",
                "118.26.252.209:5222"
            )
        )
    }

    private fun addReservedHost(host: String, hosts: Array<String>) {
        for (h in hosts) {
            HostManager.addReservedHost(host, h)
        }
    }

    private fun hookField(klass: Class<*>, field: String, value: Any) {
        try {
            val target = klass.getDeclaredField(field)
            target.isAccessible = true
            target.set(null, value)
        } catch (e: Throwable) {
            logE(e.message, e)
        }
    }

    private fun buildMiSDKLogger(): LoggerInterface {
        return object : LoggerInterface {
            override fun setTag(str: String) {
                innerTag = "$TAG-$str"
            }

            private var innerTag = TAG

            override fun log(str: String, th: Throwable) {
                Napier.d(str, th, tag = innerTag)
            }

            override fun log(str: String) {
                Napier.d(str, tag = innerTag)
            }
        }
    }

    private fun initPushLogger(context: Context, logger: LoggerInterface) {
        com.xiaomi.mipush.sdk.Logger.setLogger(context, logger)
    }

    private fun initMiSdkLogger(logger: LoggerInterface) {
        MyLog.setLogger(logger)
        MyLog.setLogLevel(MyLog.INFO)
    }

    private const val TAG = "PushCore"
}
