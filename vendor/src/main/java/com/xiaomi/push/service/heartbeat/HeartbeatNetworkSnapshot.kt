package com.xiaomi.push.service.heartbeat

/**
 * Minimal network identity for stock 7.4.67-C stable intelligent heartbeat.
 *
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/push/service/u.java
 * method e(f5.f) plus wifi-digest hook h(String).
 *
 * type mirrors android.net.NetworkInfo.getType():
 * - 0 mobile
 * - 1 wifi
 * - 6 also treated as wifi in stock u.e
 * - other / null clears the net id
 */
data class HeartbeatNetworkSnapshot(
    val type: Int,
    val subtypeName: String? = null,
    val wifiDigest: String? = null,
) {
    companion object {
        const val TYPE_MOBILE = 0
        const val TYPE_WIFI = 1
        const val TYPE_WIFI_ALT = 6
        const val TYPE_NONE = -1
    }
}
