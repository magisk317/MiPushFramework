package com.xiaomi.push.service.heartbeat

/**
 * Heartbeat strategy surface from stock XMSF 7.4.67-C.
 *
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/push/service/x.java
 * (extends w + ia.i). Older product code used a fixed SmackConfiguration.pingInterval only.
 */
interface HeartbeatStrategy {
    /** Stock x.c / strategy id: 0 stable, 1 dynamic, 2 hardware. */
    fun strategyId(): Int

    fun prepare()

    fun release()

    /** Stock ia.i.i: interval used when registering the next ping alarm. */
    fun pingIntervalMs(): Long

    /** Stock ia.i.f: last interval returned by [pingIntervalMs]. */
    fun lastPingIntervalMs(): Long

    fun onNetworkChanged(snapshot: HeartbeatNetworkSnapshot?)

    fun onWifiDigest(digest: String?)

    /** Stock w.j: capture current net id just before sending a client ping. */
    fun onPingSent()

    /** Stock w.g: ping-pong timeout path; may learn a short interval. */
    fun onPingTimeout()

    /**
     * Stock w.d: success / report path. Telemetry upload stays inert; only local counter reset
     * that affects later interval selection is retained.
     */
    fun onPingSuccessOrReport()

    /** Stock x.b: keep short HB effective for the given number of days. */
    fun keepShortHeartbeatEffectiveDays(days: Int)
}

