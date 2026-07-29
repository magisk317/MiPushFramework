package com.xiaomi.push.service.heartbeat

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey

/**
 * Heartbeat strategy selector from stock XMSF 7.4.67-C `com.xiaomi.push.service.v`.
 *
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/push/service/v.java
 *
 * Stock may select:
 * - 0 stable intelligent (`u`) — default and current port
 * - 1 dynamic explore/stable (`r`) when OC strategy=1 and device conditions pass
 * - 2 hardware modem keep-alive when MdKa is available
 *
 * Dynamic `r` and hardware MdKa are large, modem/platform-specific surfaces that are only
 * partially decompiled and include telemetry. Until those stock surfaces are ported cleanly,
 * OC strategy 1/2 fall back to the stable strategy instead of inventing incomplete replacements.
 * Interval providers still flow through this facade so Alarm refresh and ping hooks stay stock-shaped.
 */
class HeartbeatStrategyManager private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext

    @Volatile
    private var strategy: HeartbeatStrategy? = null

    @Synchronized
    private fun selectStrategy(): HeartbeatStrategy {
        var requested = OnlineConfig.getInstance(appContext).getIntValue(
            ConfigKey.IntelligentHeartbeatStrategy.value,
            STRATEGY_STABLE,
        )
        // Stock: strategy 1 is only kept when r.u(context) is true; otherwise it falls through to 0.
        if (requested == STRATEGY_DYNAMIC) {
            MyLog.w("HB dynamic strategy requested but not ported; using stable strategy.")
            requested = STRATEGY_STABLE
        }
        // Stock forces strategy 2 when hardware.heartbeat.e.b() is true. Without a clean MdKa
        // surface, keep stable rather than inventing modem keep-alive.
        if (requested == STRATEGY_HARDWARE) {
            MyLog.w("HB hardware strategy requested but MdKa is unavailable; using stable strategy.")
            requested = STRATEGY_STABLE
        }

        val current = strategy
        if (current != null && current.strategyId() == requested) {
            return current
        }
        current?.release()
        val next = StableIntelligentHeartbeatStrategy(appContext)
        next.prepare()
        strategy = next
        return next
    }

    fun pingIntervalMs(): Long = selectStrategy().pingIntervalMs()

    fun lastPingIntervalMs(): Long = selectStrategy().lastPingIntervalMs()

    fun strategyId(): Int = selectStrategy().strategyId()

    fun onNetworkChanged(snapshot: HeartbeatNetworkSnapshot?) {
        selectStrategy().onNetworkChanged(snapshot)
    }

    fun onWifiDigest(digest: String?) {
        selectStrategy().onWifiDigest(digest)
    }

    fun onPingSent() {
        selectStrategy().onPingSent()
    }

    fun onPingTimeout() {
        selectStrategy().onPingTimeout()
    }

    fun onPingSuccessOrReport() {
        selectStrategy().onPingSuccessOrReport()
    }

    fun keepShortHeartbeatEffectiveDays(days: Int) {
        selectStrategy().keepShortHeartbeatEffectiveDays(days)
    }

    companion object {
        const val STRATEGY_STABLE = 0
        const val STRATEGY_DYNAMIC = 1
        const val STRATEGY_HARDWARE = 2

        @Volatile
        private var instance: HeartbeatStrategyManager? = null

        @JvmStatic
        fun getInstance(context: Context): HeartbeatStrategyManager {
            val existing = instance
            if (existing != null) {
                return existing
            }
            return synchronized(this) {
                instance ?: HeartbeatStrategyManager(context).also { instance = it }
            }
        }

        /** Test-only reset so unit tests do not leak strategy state across cases. */
        @JvmStatic
        fun resetForTests() {
            synchronized(this) {
                instance?.strategy?.release()
                instance = null
            }
        }
    }
}

