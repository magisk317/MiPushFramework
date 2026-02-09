package com.magisk317.network

import android.content.Context
import android.net.Uri
import com.elvishew.xlog.XLog
import com.magisk317.Global
import com.xiaomi.channel.commonutils.android.Region
import com.xiaomi.network.HostFilter
import com.xiaomi.network.HostManager
import com.xiaomi.push.service.AppRegionStorage
import com.xiaomi.smack.ConnectionConfiguration
import java.lang.reflect.Field

/**
 * Runtime compatibility for behaviors that were previously provided via AspectJ.
 */
object NetworkPolicyCompat {
    private val logger = XLog.tag("NetworkPolicyCompat").build()
    @Volatile
    private var wrappedFactoryIdentity: Int? = null

    @JvmStatic
    fun applyAll(context: Context) {
        enforceCnRegion(context)
        installCountryCodeUrlRewrite()
        applyXmppHostOverride(context)
    }

    @JvmStatic
    fun enforceCnRegion(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            AppRegionStorage.getInstance(appContext).apply {
                setRegion(Region.China.name)
                setCountryCode("CN")
            }
            // Best-effort fallback for code paths reading system properties directly.
            forceSystemCountryCode("CN")
        }.onFailure {
            logger.w("enforce CN region failed: ${it.message}")
        }
    }

    @JvmStatic
    fun applyXmppHostOverride(context: Context) {
        val configured = Global.ConfigCenter().getXMPPServer(context.applicationContext).orEmpty().trim()
        val defaultHost = ConnectionConfiguration.XMPP_SERVER_CHINA_HOST_P
        if (configured.isEmpty()) {
            ConnectionConfiguration.setXmppServerHost(defaultHost)
            return
        }
        val parsed = parseHostPort(configured)
        ConnectionConfiguration.setXmppServerHost(parsed.host)
        addReservedHost(parsed)
    }

    private fun addReservedHost(hostPort: HostPort) {
        val host = hostPort.host
        val withPort = hostPort.withPort
        runCatching {
            HostManager.addReservedHost(host, withPort)
            HostManager.addReservedHost(ConnectionConfiguration.getXmppServerHost(), withPort)
        }.onFailure {
            logger.w("add reserved host failed: ${it.message}")
        }
    }

    private fun forceSystemCountryCode(countryCode: String) {
        val keys = arrayOf(
            "ro.miui.region",
            "ro.miui.country.code",
            "ro.product.locale.region",
            "persist.sys.country"
        )
        for (key in keys) {
            runCatching {
                val clazz = Class.forName("android.os.SystemProperties")
                val setMethod = clazz.getDeclaredMethod("set", String::class.java, String::class.java)
                setMethod.isAccessible = true
                setMethod.invoke(null, key, countryCode)
            }
        }
    }

    @JvmStatic
    fun installCountryCodeUrlRewrite() {
        runCatching {
            val factoryField: Field = HostManager::class.java.getDeclaredField("factory").apply {
                isAccessible = true
            }
            val currentFactory = factoryField.get(null) as? HostManager.HostManagerFactory ?: return
            val identity = System.identityHashCode(currentFactory)
            if (wrappedFactoryIdentity == identity) return
            val wrapped = object : HostManager.HostManagerFactory {
                override fun createHostManager(
                    context: Context,
                    hostFilter: HostFilter,
                    httpGet: HostManager.HttpGet,
                    userId: String
                ): HostManager {
                    val rewrittenHttpGet = HostManager.HttpGet { originalUrl ->
                        httpGet.doGet(rewriteCountryCode(originalUrl))
                    }
                    return currentFactory.createHostManager(context, hostFilter, rewrittenHttpGet, userId)
                }
            }
            HostManager.setHostManagerFactory(wrapped)
            wrappedFactoryIdentity = identity
        }.onFailure {
            logger.w("install countrycode URL rewrite failed: ${it.message}")
        }
    }

    private fun rewriteCountryCode(url: String): String {
        var newUrl = url
        newUrl = newUrl.replaceFirst("&countrycode=[^&]+".toRegex(), "")
        newUrl = newUrl.replaceFirst("\\?countrycode=[^&]+".toRegex(), "?")
        return Uri.parse(newUrl).buildUpon()
            .appendQueryParameter("countrycode", "CN")
            .toString()
    }

    private data class HostPort(val host: String, val withPort: String)

    private fun parseHostPort(raw: String): HostPort {
        val trimmed = raw.trim().removePrefix("http://").removePrefix("https://")
        if (!trimmed.contains("/")) {
            val direct = trimmed.substringBefore("?").substringBefore("#")
            val host = direct.substringBefore(":")
            val port = direct.substringAfter(":", "5222")
            return HostPort(host, "$host:$port")
        }
        val uri = Uri.parse(trimmed)
        val host = uri.host ?: trimmed.substringBefore("/").substringBefore(":")
        val port = if (uri.port > 0) uri.port else 5222
        return HostPort(host, "$host:$port")
    }
}
