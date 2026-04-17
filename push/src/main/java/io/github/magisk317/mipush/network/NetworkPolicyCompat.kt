package io.github.magisk317.mipush.network

import android.content.Context
import android.net.Uri
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.platform.support.Global
import com.xiaomi.channel.commonutils.android.Region
import com.xiaomi.network.HostFilter
import com.xiaomi.network.HostManager
import com.xiaomi.push.service.AppRegionStorage
import com.xiaomi.smack.ConnectionConfiguration
import java.lang.reflect.Field
import kotlinx.coroutines.runBlocking

/**
 * Runtime compatibility for behaviors that were previously provided via AspectJ.
 */
object NetworkPolicyCompat {
    private val logger = object {
        fun w(msg: String) = Napier.w(msg, tag = "NetworkPolicyCompat")
    }

    @JvmStatic
    fun applyAll(context: Context) {
        enforceCnRegion(context)
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
        val configured = runBlocking { Global.configCenter().getXMPPServerAsync() }.orEmpty().trim()
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
