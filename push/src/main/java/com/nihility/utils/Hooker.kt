package com.nihility.utils

import android.content.Context
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.nihility.Configurations
import com.nihility.Dependencies
import com.nihility.Global
import com.nihility.HookedMethodHandler
import com.nihility.OuterDependencies
import com.nihility.service.XMPushServiceAbility
import com.nihility.service.XMPushServiceListener
import com.xiaomi.channel.commonutils.android.Region
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.network.HostManager
import com.xiaomi.push.service.AppRegionStorage
import com.xiaomi.push.service.XMPushService
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.smack.SmackConfiguration

object Hooker {
    private val logger = XLog.tag("Hooker").build()

    @JvmStatic
    fun hook(context: Context) {
        initMiPushHookLib(context)
        hookMiPushSDK(context)
    }

    @JvmStatic
    fun setLogger(context: Context) {
        val logger = buildMiSDKLogger()
        initMiSdkLogger(logger)
        initPushLogger(context, logger)
    }

    private fun initMiPushHookLib(context: Context) {
        val configurations = object : Configurations {
            override fun getXMPPServer(): String =
                Global.ConfigCenter().getXMPPServer(context.applicationContext).orEmpty()
        }
        Dependencies.set(object : OuterDependencies {
            override fun configuration(): Configurations = configurations

            override fun serviceListener(pushService: XMPushService): XMPushServiceListener =
                XMPushServiceAbility(pushService)

            override fun hookedMethodHandler(): HookedMethodHandler = Global.HookHandler()
        })
    }

    private fun hookMiPushSDK(context: Context) {
        try {
            hookField(SmackConfiguration::class.java, "pingInterval", 3 * 60 * 1000)
            hookMiPushServerHost()
            val regionStorage = AppRegionStorage.getInstance(context.applicationContext)
            regionStorage.setRegion(Region.China.name)
            regionStorage.setCountryCode("CN")
        } catch (e: Throwable) {
            logger.e(e.message, e)
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
            logger.e(e.message, e)
        }
    }

    private fun buildMiSDKLogger(): LoggerInterface {
        return object : LoggerInterface {
            private var logger: Logger = XLog.tag(TAG).build()

            override fun setTag(tag: String) {
                logger = XLog.tag("$TAG-$tag").build()
            }

            override fun log(content: String, t: Throwable) {
                logger.i(content, t)
            }

            override fun log(content: String) {
                logger.i(content)
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
