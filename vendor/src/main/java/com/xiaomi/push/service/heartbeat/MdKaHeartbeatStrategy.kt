package com.xiaomi.push.service.heartbeat

/**
 * Hardware (strategy id 2) heartbeat from stock XMSF 7.5.79-C: the modem carries the
 * TCP keep-alive for the long connection, so the AP-side ping timer drops to the
 * 59-minute db.i.e interval while the modem session is alive.
 *
 * Stock reference: com.xiaomi.xmsf 7.5.79-C (versionCode 70005079),
 * base.apk sha256 edaa580b41db1a2bc5b0cc914fb27d4dcb5a5d0603d56c352311853e6f1fdc2d,
 * JADX path: com.xiaomi.xmsf/stock-7.5.79-C/sources/com/xiaomi/push/service/hardware/heartbeat/b.java
 * (implements the heartbeat contract c0 with all callbacks inert; the real session work
 * lives in the c/e state machine, driven from XMPushService socket lifecycle hooks).
 */
class MdKaHeartbeatStrategy(
    private val controller: MdKaController,
) : HeartbeatStrategy {
    override fun strategyId(): Int = HeartbeatStrategyManager.STRATEGY_HARDWARE

    override fun prepare() {
        controller.initializeIfNeeded()
    }

    override fun release() {
        // Stock b.destroy() is intentionally empty: the modem session is torn down by the
        // socket-closed hook, not by strategy reselection.
    }

    override fun pingIntervalMs(): Long = controller.modemPingIntervalMs()

    override fun lastPingIntervalMs(): Long = controller.modemPingIntervalMs()

    override fun onNetworkChanged(snapshot: HeartbeatNetworkSnapshot?) {
        controller.onNetworkChanged(snapshot)
    }

    override fun onWifiDigest(digest: String?) {
        // Wifi digest learning is a stable-strategy concern; the modem ignores digests.
    }

    override fun onPingSent() {
        controller.onPingStart()
    }

    override fun onPingTimeout() {
        // Under the hardware strategy a ping-pong timeout means the modem session already
        // failed; MdKaController.onModemIndication runs the fail-too-much circuit breaker
        // and HeartbeatStrategyManager falls back to the stable strategy on next selection.
    }

    override fun onPingSuccessOrReport() {
        controller.onPingStop()
    }

    override fun keepShortHeartbeatEffectiveDays(days: Int) {
        // No short-interval learning under the hardware strategy.
    }
}
