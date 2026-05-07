package io.github.magisk317.mipush.service.runtime
import com.xiaomi.push.service.*
import com.xiaomi.smack.packet.*
import com.xiaomi.smack.*
import com.xiaomi.slim.*
import com.xiaomi.push.service.timers.*
import com.xiaomi.push.service.*

import java.util.regex.Pattern

object NetworkCheckupRuntime {
    private const val CONNECTIVITY_CHECK_INTERVAL = 1_800_000L
    private val IP_PATTERN = Pattern.compile("([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})")

    @JvmStatic
    fun shouldRunConnectivityTest(
        activeCount: Int,
        nowMs: Long,
        lastCheckTimeMs: Long,
        allowStats: Boolean,
        testHostsCount: Int,
    ): Boolean {
        return (activeCount <= 0 || nowMs - lastCheckTimeMs >= CONNECTIVITY_CHECK_INTERVAL) &&
            allowStats &&
            testHostsCount > 0
    }

    @JvmStatic
    fun extractGateway(routeLine: String?): String? {
        if (routeLine.isNullOrEmpty() || !routeLine.startsWith("default via")) {
            return null
        }
        return routeLine
            .split(" ")
            .firstOrNull { IP_PATTERN.matcher(it).matches() }
    }
}
