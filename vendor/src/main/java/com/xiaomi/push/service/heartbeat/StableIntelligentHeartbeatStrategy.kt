package com.xiaomi.push.service.heartbeat

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stable intelligent heartbeat strategy from stock XMSF 7.4.67-C com.xiaomi.push.service.u.
 *
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256
 * 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/push/service/u.java
 *
 * Older project behavior always used SmackConfiguration.pingInterval (600_000) for AlarmManager
 * registration, so learned short intervals and OC gates never affected ping timing. This port keeps
 * the stock prefs shape (hb_record, HB_/HB_dead_time_ keys), learning short interval 235_000 after
 * enough consecutive timeouts, and OC gates via ConfigKey 116-119/130/143. OneTrack/category_hb_*
 * upload from stock u.d/u.k stays intentionally inert under the product telemetry boundary.
 */
class StableIntelligentHeartbeatStrategy(
    context: Context,
) : HeartbeatStrategy {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val isXmsfPackage: Boolean = MIUIUtils.isXMSF(appContext)
    private val intelligentHeartbeatSwitch: Boolean =
        OnlineConfig.getInstance(appContext).getBooleanValue(
            ConfigKey.IntelligentHeartbeatSwitchBoolean.value,
            true,
        )
    private val unsupportWifiDigestSwitch: Boolean =
        OnlineConfig.getInstance(appContext).getBooleanValue(
            ConfigKey.IntelligentHeartbeatForUnsupportWifiDigestBoolean.value,
            true,
        )

    private val consecutiveTimeouts = AtomicInteger(0)
    private val shortIntervalSampleCount = AtomicInteger(0)
    private val longIntervalSampleCount = AtomicInteger(0)

    @Volatile
    private var networkId: String? = null

    @Volatile
    private var learningEnabled: Boolean = false

    @Volatile
    private var pingSentNetworkId: String? = null

    @Volatile
    private var networkKind: Int = HeartbeatNetworkSnapshot.TYPE_NONE

    @Volatile
    private var lastIntervalMs: Long = DEFAULT_LONG_INTERVAL_MS

    init {
        MyLog.w("HB Use stable strategy.")
        val now = System.currentTimeMillis()
        if (preferences.getLong(KEY_RECORD_HB_COUNT_START, -1L) == -1L) {
            preferences.edit().putLong(KEY_RECORD_HB_COUNT_START, now).apply()
        }
        if (preferences.getLong(KEY_RECORD_PTC_START, -1L) == -1L) {
            preferences.edit().putLong(KEY_RECORD_PTC_START, now).apply()
        }
    }

    override fun strategyId(): Int = STRATEGY_ID

    override fun prepare() {
        onNetworkChanged(currentNetworkSnapshot(appContext))
    }

    override fun release() {
        // Stock u.a is empty.
    }

    override fun lastPingIntervalMs(): Long = lastIntervalMs

    override fun pingIntervalMs(): Long {
        var interval = DEFAULT_LONG_INTERVAL_MS
        if (isXmsfPackage) {
            val netId = networkId
            val networkAllowsShort = when {
                TextUtils.isEmpty(netId) -> true
                netId!!.startsWith(MOBILE_PREFIX) ->
                    OnlineConfig.getInstance(appContext).getBooleanValue(
                        ConfigKey.IntelligentHeartbeatUseInMobileNetworkBoolean.value,
                        false,
                    )
                netId == WIFI_DEFAULT_ID ->
                    OnlineConfig.getInstance(appContext).getBooleanValue(
                        ConfigKey.IntelligentHeartbeatForUnsupportWifiDigestBoolean.value,
                        true,
                    )
                else -> true
            }
            val shortStillForced =
                preferences.getLong(KEY_KEEP_SHORT_HB_EFFECTIVE_TIME, -1L) >=
                    System.currentTimeMillis()
            val intelligentEnabled =
                OnlineConfig.getInstance(appContext).getBooleanValue(
                    ConfigKey.IntelligentHeartbeatSwitchBoolean.value,
                    true,
                ) || shortStillForced
            val learned = learnedIntervalMs(netId)
            if (networkAllowsShort && intelligentEnabled && learned != -1) {
                interval = learned.toLong()
            }
        }

        if (
            !TextUtils.isEmpty(networkId) &&
            networkId != WIFI_ID_UNKNOWN &&
            networkKind == HeartbeatNetworkSnapshot.TYPE_WIFI
        ) {
            // Stock accumulates short/long sample counters for later report. Local counters are
            // retained; the report upload path remains inert.
            val isShort = interval < SHORT_LONG_BOUNDARY_MS
            if (shouldCollectLocalHbStats()) {
                val counter = if (isShort) shortIntervalSampleCount else longIntervalSampleCount
                val sample = counter.incrementAndGet()
                val label = if (isShort) "short" else "long"
                MyLog.v("[HB] " + label + " ping interval count: " + sample)
                if (sample >= SAMPLE_FLUSH_THRESHOLD) {
                    val key = if (isShort) KEY_RECORD_SHORT_HB_COUNT else KEY_RECORD_LONG_HB_COUNT
                    val total = preferences.getInt(key, 0) + sample
                    preferences.edit().putInt(key, total).apply()
                    MyLog.w("[HB] accumulate " + label + " hb count(" + total + ") and write to file. ")
                    counter.set(0)
                }
            }
        }

        lastIntervalMs = interval
        MyLog.w("[HB] ping interval:" + interval)
        return interval
    }

    override fun onNetworkChanged(snapshot: HeartbeatNetworkSnapshot?) {
        if (!intelligentHeartbeatAllowed()) {
            return
        }
        if (snapshot == null) {
            applyNetworkId(null)
            networkKind = HeartbeatNetworkSnapshot.TYPE_NONE
            return
        }
        when (snapshot.type) {
            HeartbeatNetworkSnapshot.TYPE_MOBILE -> {
                val subtype = snapshot.subtypeName
                val id =
                    if (!TextUtils.isEmpty(subtype) && !"UNKNOWN".equals(subtype, true)) {
                        MOBILE_PREFIX + subtype
                    } else {
                        null
                    }
                applyNetworkId(id)
                networkKind = HeartbeatNetworkSnapshot.TYPE_MOBILE
            }
            HeartbeatNetworkSnapshot.TYPE_WIFI,
            HeartbeatNetworkSnapshot.TYPE_WIFI_ALT,
            -> {
                applyNetworkId(WIFI_ID_UNKNOWN)
                networkKind = HeartbeatNetworkSnapshot.TYPE_WIFI
                val digest = snapshot.wifiDigest
                if (!TextUtils.isEmpty(digest)) {
                    onWifiDigest(digest)
                }
            }
            else -> {
                applyNetworkId(null)
                networkKind = HeartbeatNetworkSnapshot.TYPE_NONE
            }
        }
    }

    override fun onWifiDigest(digest: String?) {
        if (!TextUtils.isEmpty(digest)) {
            if (!preferences.getBoolean(KEY_SUPPORT_WIFI_DIGEST, false)) {
                preferences.edit().putBoolean(KEY_SUPPORT_WIFI_DIGEST, true).apply()
            }
        }
        if (intelligentHeartbeatAllowed() && !TextUtils.isEmpty(digest)) {
            applyNetworkId(WIFI_PREFIX + digest)
            networkKind = HeartbeatNetworkSnapshot.TYPE_WIFI
        }
    }

    override fun onPingSent() {
        if (intelligentHeartbeatAllowed()) {
           pingSentNetworkId = networkId
       }
   }

    override fun onPingTimeout() {
        if (!intelligentHeartbeatAllowed()) {
            return
        }
        val ptcKey =
            when (networkKind) {
                HeartbeatNetworkSnapshot.TYPE_MOBILE -> KEY_RECORD_MOBILE_PTC
                HeartbeatNetworkSnapshot.TYPE_WIFI -> KEY_RECORD_WIFI_PTC
                else -> null
            }
        if (ptcKey != null) {
            if (preferences.getLong(KEY_RECORD_PTC_START, -1L) == -1L) {
                preferences.edit()
                    .putLong(KEY_RECORD_PTC_START, System.currentTimeMillis())
                    .apply()
            }
            preferences.edit()
                .putInt(ptcKey, preferences.getInt(ptcKey, 0) + 1)
                .apply()
        }

        if (
            learningEnabled &&
            !TextUtils.isEmpty(networkId) &&
            networkId == pingSentNetworkId
        ) {
            consecutiveTimeouts.incrementAndGet()
            MyLog.w("[HB] ping timeout count:" + consecutiveTimeouts.get())
            val natCount =
                maxOf(
                    OnlineConfig.getInstance(appContext).getIntValue(
                        ConfigKey.IntelligentHeartbeatNATCountInt.value,
                        DEFAULT_NAT_COUNT,
                    ),
                    DEFAULT_NAT_COUNT,
                )
            if (consecutiveTimeouts.get() >= natCount) {
                MyLog.w("[HB] change hb interval for net:" + networkId)
                val netId = networkId
                if (
                    !TextUtils.isEmpty(netId) &&
                    (netId!!.startsWith(WIFI_PREFIX) || netId.startsWith(MOBILE_PREFIX))
                ) {
                    preferences.edit()
                        .putInt(hbIntervalKey(netId), SHORT_INTERVAL_MS.toInt())
                        .putLong(
                            hbDeadTimeKey(netId),
                            System.currentTimeMillis() + shortHeartbeatEffectivePeriodMs(),
                        )
                        .apply()
                }
                learningEnabled = false
                consecutiveTimeouts.set(0)
                // Stock also appends record_hb_change for later OneTrack upload; keep that string
                // local-only and never send it (telemetry boundary).
                recordLocalHbChange(netId)
            }
        }
    }

    override fun onPingSuccessOrReport() {
        if (!intelligentHeartbeatAllowed()) {
            return
        }
        // Stock u.d uploads category_hb_* events when data-collect is on. Those uploads stay inert.
        // The only runtime-affecting branch is resetting the consecutive-timeout counter while still
        // learning on the current net id.
        if (learningEnabled) {
            consecutiveTimeouts.set(0)
        }
    }

    override fun keepShortHeartbeatEffectiveDays(days: Int) {
        preferences.edit()
            .putLong(
                KEY_KEEP_SHORT_HB_EFFECTIVE_TIME,
                System.currentTimeMillis() + days.toLong() * DAY_MS,
            )
            .apply()
    }

    private fun intelligentHeartbeatAllowed(): Boolean {
        val shortStillForced =
            preferences.getLong(KEY_KEEP_SHORT_HB_EFFECTIVE_TIME, -1L) >= System.currentTimeMillis()
        if (!isXmsfPackage) {
            return false
        }
        return intelligentHeartbeatSwitch || unsupportWifiDigestSwitch || shortStillForced
    }

    private fun shouldCollectLocalHbStats(): Boolean {
        // Stock gates collect+upload with China region. Local counter writes are harmless and keep
        // prefs shape aligned; upload remains disabled by product policy regardless of region.
        return intelligentHeartbeatAllowed() &&
            OnlineConfig.getInstance(appContext).getBooleanValue(
                ConfigKey.IntelligentHeartbeatDataCollectSwitchBoolean.value,
               true,
           )
   }

    private fun applyNetworkId(rawId: String?) {
        val resolved =
            when {
                rawId == WIFI_ID_UNKNOWN -> {
                    val current = networkId
                    when {
                        current != null && current.startsWith(WIFI_PREFIX) -> current
                        unsupportWifiDigestSwitch -> WIFI_DEFAULT_ID
                        else -> null
                    }
                }
                else -> rawId
            }
        networkId = resolved

        val learned = learnedIntervalMs(resolved)
        val deadAt =
            if (resolved == null) {
                -1L
            } else {
                preferences.getLong(hbDeadTimeKey(resolved), -1L)
            }
        val now = System.currentTimeMillis()
        if (learned != -1 && resolved != null) {
            if (deadAt == -1L) {
                preferences.edit()
                    .putLong(hbDeadTimeKey(resolved), now + shortHeartbeatEffectivePeriodMs())
                    .apply()
            } else if (now > deadAt) {
                preferences.edit()
                    .remove(hbIntervalKey(resolved))
                    .remove(hbDeadTimeKey(resolved))
                    .apply()
            }
        }

        consecutiveTimeouts.set(0)
        learningEnabled =
            !(TextUtils.isEmpty(networkId) || learnedIntervalMs(networkId) != -1)
        MyLog.w("[HB] network changed, netid:" + networkId + ", " + learningEnabled)
    }

    private fun learnedIntervalMs(netId: String?): Int {
        if (TextUtils.isEmpty(netId)) {
            return -1
        }
        return try {
            preferences.getInt(hbIntervalKey(netId!!), -1)
        } catch (_: Throwable) {
            -1
        }
    }

    private fun shortHeartbeatEffectivePeriodMs(): Long {
        return OnlineConfig.getInstance(appContext).getLongValue(
            ConfigKey.ShortHeartbeatEffectivePeriodMsLong.value,
            DEFAULT_SHORT_EFFECTIVE_PERIOD_MS,
        )
    }

    private fun recordLocalHbChange(netId: String?) {
        if (!shouldCollectLocalHbStats() || TextUtils.isEmpty(netId)) {
            return
        }
        val kind =
            when {
                netId!!.startsWith(WIFI_PREFIX) -> "W"
                netId.startsWith(MOBILE_PREFIX) -> "M"
                else -> return
            }
        val entry = netId + ":::" + kind + ":::" + SHORT_INTERVAL_MS + ":::" +
            (System.currentTimeMillis() / 1000L)
        val existing = preferences.getString(KEY_RECORD_HB_CHANGE, null)
        val merged = if (TextUtils.isEmpty(existing)) entry else existing + "###" + entry
        preferences.edit().putString(KEY_RECORD_HB_CHANGE, merged).apply()
    }

    companion object {
        const val STRATEGY_ID = 0
        const val DEFAULT_LONG_INTERVAL_MS = 600_000L
        const val SHORT_INTERVAL_MS = 235_000L
        private const val SHORT_LONG_BOUNDARY_MS = 300_000L
        private const val DEFAULT_NAT_COUNT = 3
        private const val SAMPLE_FLUSH_THRESHOLD = 5
        private const val DAY_MS = 86_400_000L
        private const val DEFAULT_SHORT_EFFECTIVE_PERIOD_MS = 7_776_000_000L // 90 days

        private const val PREFS_NAME = "hb_record"
        private const val KEY_RECORD_HB_COUNT_START = "record_hb_count_start"
        private const val KEY_RECORD_PTC_START = "record_ptc_start"
        private const val KEY_KEEP_SHORT_HB_EFFECTIVE_TIME = "keep_short_hb_effective_time"
        private const val KEY_SUPPORT_WIFI_DIGEST = "support_wifi_digest"
        private const val KEY_RECORD_SHORT_HB_COUNT = "record_short_hb_count"
        private const val KEY_RECORD_LONG_HB_COUNT = "record_long_hb_count"
        private const val KEY_RECORD_MOBILE_PTC = "record_mobile_ptc"
        private const val KEY_RECORD_WIFI_PTC = "record_wifi_ptc"
        private const val KEY_RECORD_HB_CHANGE = "record_hb_change"

        private const val WIFI_ID_UNKNOWN = "WIFI-ID-UNKNOWN"
        private const val WIFI_DEFAULT_ID = "W-NETWORK_ID_WIFI_DEFAULT"
        private const val WIFI_PREFIX = "W-"
        private const val MOBILE_PREFIX = "M-"

        private fun hbIntervalKey(netId: String): String = "HB_" + netId

        private fun hbDeadTimeKey(netId: String): String = "HB_dead_time_" + netId

        @JvmStatic
        fun currentNetworkSnapshot(context: Context): HeartbeatNetworkSnapshot? {
            val appContext = context.applicationContext
            return try {
                if (!Network.isConnected(appContext)) {
                    return null
                }
                val type = Network.getActiveNetworkType(appContext)
                when (type) {
                    HeartbeatNetworkSnapshot.TYPE_MOBILE -> {
                        val name = Network.getActiveNetworkName(appContext)
                        val subtype =
                            name.substringAfter("mobile-", missingDelimiterValue = "")
                                .ifEmpty { null }
                        HeartbeatNetworkSnapshot(type = type, subtypeName = subtype)
                    }
                    HeartbeatNetworkSnapshot.TYPE_WIFI ->
                        HeartbeatNetworkSnapshot(type = type)
                    else -> HeartbeatNetworkSnapshot(type = type)
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
