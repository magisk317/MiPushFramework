package io.github.magisk317.mipush.service.runtime
import com.xiaomi.push.service.*
import com.xiaomi.smack.packet.*
import com.xiaomi.smack.*
import com.xiaomi.slim.*
import com.xiaomi.push.service.timers.*
import com.xiaomi.push.service.*

object NetworkCheckupRuntime {
    @JvmStatic
    fun shouldRunConnectivityTest(
        activeCount: Int,
        nowMs: Long,
        lastCheckTimeMs: Long,
        allowStats: Boolean,
        testHostsCount: Int,
    ): Boolean = io.github.magisk317.mipush.runtime.core.PushNetworkCheckPlanFactory.shouldRunConnectivityTest(
        activeCount = activeCount,
        nowMs = nowMs,
        lastCheckTimeMs = lastCheckTimeMs,
        allowStats = allowStats,
        testHostsCount = testHostsCount,
    )

    @JvmStatic
    fun extractGateway(routeLine: String?): String? =
        io.github.magisk317.mipush.runtime.core.PushNetworkCheckPlanFactory.extractGateway(routeLine)
}
