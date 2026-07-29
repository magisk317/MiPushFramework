package io.github.magisk317.mipush.notification

import android.app.Application
import android.os.Bundle
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Unit tests for [AndroidWGroupStrategy.shouldSkipForceGroup] — pure logic, no Android context needed.
 *
 * Validates:
 * - Requirements 15.2: miui_skipForceGroup flag bypass
 * - Requirements 15.3: Without flag, strategy-based grouping applies
 */
class AndroidWGroupStrategyTest {

    @Test
    fun `strategy 2 always skips force group regardless of sourceGroup`() {
        assertTrue(AndroidWGroupStrategy.shouldSkipForceGroup(null, 2))
        assertTrue(AndroidWGroupStrategy.shouldSkipForceGroup("", 2))
        assertTrue(AndroidWGroupStrategy.shouldSkipForceGroup("my_group", 2))
    }

    @Test
    fun `strategy 3 never skips force group regardless of sourceGroup`() {
        assertFalse(AndroidWGroupStrategy.shouldSkipForceGroup(null, 3))
        assertFalse(AndroidWGroupStrategy.shouldSkipForceGroup("", 3))
        assertFalse(AndroidWGroupStrategy.shouldSkipForceGroup("my_group", 3))
    }

    @Test
    fun `strategy 1 skips when sourceGroup is non-empty`() {
        assertTrue(AndroidWGroupStrategy.shouldSkipForceGroup("notifications", 1))
        assertTrue(AndroidWGroupStrategy.shouldSkipForceGroup("chat_group", 1))
    }

    @Test
    fun `strategy 1 does not skip when sourceGroup is null or empty`() {
        assertFalse(AndroidWGroupStrategy.shouldSkipForceGroup(null, 1))
        assertFalse(AndroidWGroupStrategy.shouldSkipForceGroup("", 1))
    }

    @Test
    fun `unknown strategy values fall back to strategy 1 behavior`() {
        // Non-empty group → skip
        assertTrue(AndroidWGroupStrategy.shouldSkipForceGroup("group", 0))
        assertTrue(AndroidWGroupStrategy.shouldSkipForceGroup("group", 99))
        assertTrue(AndroidWGroupStrategy.shouldSkipForceGroup("group", -1))
        // Null/empty group → don't skip
        assertFalse(AndroidWGroupStrategy.shouldSkipForceGroup(null, 0))
        assertFalse(AndroidWGroupStrategy.shouldSkipForceGroup("", 99))
    }
}

/**
 * Robolectric tests for [AndroidWGroupStrategy.applyIfEligible] on API 36 (Android 16).
 *
 * Validates:
 * - Requirements 15.1: Android 16+ uses AndroidWGroupStrategy
 * - Requirements 15.2: miui_skipForceGroup flag bypass
 * - Requirements 15.3: Strategy-based grouping applies
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [36], application = Application::class)
class AndroidWGroupStrategyApi36Test {

    private lateinit var context: Application

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Clear via the singleton's own prefs reference to avoid stale-context issues
        OnlineConfig.getInstance(context).preferences.edit().clear().commit()
    }

    @Test
    fun `isApplicable returns true on API 36`() {
        assertTrue(AndroidWGroupStrategy.isApplicable)
    }

    @Test
    fun `applies skipForceGroup flag when strategy 2 always skip`() {
        setOnlineConfigStrategy(2)
        val extras = Bundle()

        val applied = AndroidWGroupStrategy.applyIfEligible(context, null, extras)

        assertTrue(applied)
        assertTrue(extras.getBoolean("miui_skipForceGroup", false))
    }

    @Test
    fun `applies skipForceGroup flag with default strategy and non-empty group`() {
        setOnlineConfigStrategy(1)
        val extras = Bundle()

        val applied = AndroidWGroupStrategy.applyIfEligible(context, "my_group", extras)

        assertTrue(applied)
        assertTrue(extras.getBoolean("miui_skipForceGroup", false))
    }

    @Test
    fun `does not apply flag when strategy 3 never skip`() {
        setOnlineConfigStrategy(3)
        val extras = Bundle()

        val applied = AndroidWGroupStrategy.applyIfEligible(context, "my_group", extras)

        assertFalse(applied)
        assertFalse(extras.containsKey("miui_skipForceGroup"))
    }

    @Test
    fun `does not apply flag with default strategy and null group`() {
        setOnlineConfigStrategy(1)
        val extras = Bundle()

        val applied = AndroidWGroupStrategy.applyIfEligible(context, null, extras)

        assertFalse(applied)
        assertFalse(extras.containsKey("miui_skipForceGroup"))
    }

    @Test
    fun `uses default strategy 1 when no config is set`() {
        // No config set — getIntValue returns default 1
        val extras = Bundle()

        val applied = AndroidWGroupStrategy.applyIfEligible(context, "group", extras)

        assertTrue(applied)
        assertTrue(extras.getBoolean("miui_skipForceGroup", false))
    }

    private fun setOnlineConfigStrategy(strategy: Int) {
        val key = "normal_oc_${ConfigKey.AndroidWGroupStrategy.value}"
        OnlineConfig.getInstance(context).preferences
            .edit()
            .putInt(key, strategy)
            .commit()
    }
}

/**
 * Robolectric tests for [AndroidWGroupStrategy.applyIfEligible] on API 28 (pre-Android 16).
 *
 * Validates:
 * - Requirements 15.4: Android < 16 does not enable AndroidWGroupStrategy
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class AndroidWGroupStrategyPreApi36Test {

    private lateinit var context: Application

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        OnlineConfig.getInstance(context).preferences.edit().clear().commit()
    }

    @Test
    fun `isApplicable returns false on API below 36`() {
        assertFalse(AndroidWGroupStrategy.isApplicable)
    }

    @Test
    fun `applyIfEligible is no-op on Android below API 36`() {
        // Even with strategy 2 (always skip), should be a no-op on pre-36
        val key = "normal_oc_${ConfigKey.AndroidWGroupStrategy.value}"
        OnlineConfig.getInstance(context).preferences
            .edit()
            .putInt(key, 2)
            .commit()
        val extras = Bundle()

        val applied = AndroidWGroupStrategy.applyIfEligible(context, "group", extras)

        assertFalse(applied)
        assertFalse(extras.containsKey("miui_skipForceGroup"))
    }
}
