package com.xiaomi.push.service.heartbeat

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ConfigKey
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Contract tests for stock 7.4.67-C stable intelligent heartbeat (com.xiaomi.push.service.u).
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [35], application = Application::class)
class StableIntelligentHeartbeatStrategyTest {
    private lateinit var context: Application

    /** Stock u only enables intelligent HB when package == com.xiaomi.xmsf. */
    private fun xmsfContext(): Context {
        return object : ContextWrapper(context) {
            override fun getPackageName(): String = PushConstants.PUSH_SERVICE_PACKAGE_NAME
            override fun getApplicationContext(): Context = this
        }
    }

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Ensure package name matches stock g.p / MIUIUtils.isXMSF gate.
        // Robolectric application package is typically the test package; force prefs isolation
        // per test by clearing hb_record and resetting the manager singleton.
        context.getSharedPreferences("hb_record", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("mipush_oc", Context.MODE_PRIVATE).edit().clear().commit()
        HeartbeatStrategyManager.resetForTests()
        OnlineConfig.getInstance(context) // warm singleton against cleared prefs
    }

    @AfterEach
    fun tearDown() {
        HeartbeatStrategyManager.resetForTests()
        context.getSharedPreferences("hb_record", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `default ping interval is 600 seconds without learned short values`() {
        val strategy = StableIntelligentHeartbeatStrategy(xmsfContext())
        strategy.onNetworkChanged(
            HeartbeatNetworkSnapshot(
                type = HeartbeatNetworkSnapshot.TYPE_WIFI,
                wifiDigest = "test-digest",
            ),
        )

        assertEquals(
            StableIntelligentHeartbeatStrategy.DEFAULT_LONG_INTERVAL_MS,
            strategy.pingIntervalMs(),
        )
    }

    @Test
    fun `learned short interval is used on wifi after enough timeouts`() {
        val strategy = StableIntelligentHeartbeatStrategy(xmsfContext())
        strategy.onNetworkChanged(
            HeartbeatNetworkSnapshot(
                type = HeartbeatNetworkSnapshot.TYPE_WIFI,
                wifiDigest = "office",
            ),
        )

        repeat(3) {
            strategy.onPingSent()
            strategy.onPingTimeout()
        }

        assertEquals(
            StableIntelligentHeartbeatStrategy.SHORT_INTERVAL_MS,
            strategy.pingIntervalMs(),
        )
    }

    @Test
    fun `mobile short interval stays gated off by default OC`() {
        val strategy = StableIntelligentHeartbeatStrategy(xmsfContext())
        strategy.onNetworkChanged(
            HeartbeatNetworkSnapshot(
                type = HeartbeatNetworkSnapshot.TYPE_MOBILE,
                subtypeName = "LTE",
            ),
        )

        // Even if a short value is already stored, default mobile OC gate is false.
        context.getSharedPreferences("hb_record", Context.MODE_PRIVATE)
            .edit()
            .putInt("HB_M-LTE", StableIntelligentHeartbeatStrategy.SHORT_INTERVAL_MS.toInt())
            .commit()
        // Re-apply network so learning flags refresh against the stored value.
        strategy.onNetworkChanged(
            HeartbeatNetworkSnapshot(
                type = HeartbeatNetworkSnapshot.TYPE_MOBILE,
                subtypeName = "LTE",
            ),
        )

        assertEquals(
            StableIntelligentHeartbeatStrategy.DEFAULT_LONG_INTERVAL_MS,
            strategy.pingIntervalMs(),
        )
    }

    @Test
    fun `manager exposes stable strategy interval through facade`() {
        val manager = HeartbeatStrategyManager.getInstance(xmsfContext())
        manager.onNetworkChanged(
            HeartbeatNetworkSnapshot(
                type = HeartbeatNetworkSnapshot.TYPE_WIFI,
                wifiDigest = "cafe",
            ),
        )
        assertEquals(HeartbeatStrategyManager.STRATEGY_STABLE, manager.strategyId())
        assertEquals(
            StableIntelligentHeartbeatStrategy.DEFAULT_LONG_INTERVAL_MS,
            manager.pingIntervalMs(),
        )
        assertTrue(manager.lastPingIntervalMs() > 0L)
    }

    @Test
    fun `config key ids for intelligent heartbeat remain decodable`() {
        assertEquals(116, ConfigKey.IntelligentHeartbeatSwitchBoolean.value)
        assertEquals(118, ConfigKey.IntelligentHeartbeatNATCountInt.value)
        assertEquals(130, ConfigKey.ShortHeartbeatEffectivePeriodMsLong.value)
        assertEquals(145, ConfigKey.IntelligentHeartbeatStrategy.value)
    }
}
