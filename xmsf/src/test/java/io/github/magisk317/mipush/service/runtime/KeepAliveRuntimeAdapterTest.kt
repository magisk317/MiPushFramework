package io.github.magisk317.mipush.service.runtime

import android.app.Application
import android.app.ActivityManager
import android.content.ComponentName
import android.os.Binder
import android.os.Build
import android.os.Handler
import com.xiaomi.xmsf.services.IMainProcBridge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.TimeUnit

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class KeepAliveRuntimeAdapterTest {
    @Test
    fun `uses stock online config ids`() {
        assertEquals(140, KeepAliveRuntimeAdapter.ONLINE_CONFIG_KEY_ONETRACK)
        assertEquals(142, KeepAliveRuntimeAdapter.ONLINE_CONFIG_KEY_KEEP_ALIVE)
    }

    @Test
    fun `refresh reads and applies both stock switches`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val requestedKeys = mutableListOf<Int>()
        val bridge = object : IMainProcBridge.Stub() {
            override fun getOnlineBooleanConfig(key: Int, defaultValue: Boolean): Boolean {
                requestedKeys += key
                return key != KeepAliveRuntimeAdapter.ONLINE_CONFIG_KEY_KEEP_ALIVE
            }

            override fun getOnlineIntConfig(key: Int, defaultValue: Int): Int = defaultValue
            override fun getOnlineStringConfig(key: Int, defaultValue: String?): String? = defaultValue
        }

        KeepAliveRuntimeAdapter.refreshOnlineConfig(context, bridge)

        assertEquals(listOf(142, 140), requestedKeys)
        assertFalse(KeepAliveRuntimeAdapter.snapshot().enabled)
        assertTrue(KeepAliveRuntimeAdapter.snapshot().oneTrackEnabled)
    }

    @Test
    fun `refresh remains unknown and inert until the main process bridge connects`() {
        val context: Application = RuntimeEnvironment.getApplication()
        KeepAliveRuntimeAdapter.shutdown()
        idleAdapterHandler()

        KeepAliveRuntimeAdapter.refreshOnlineConfig(context, bridge = null)

        assertFalse(KeepAliveRuntimeAdapter.snapshot().onlineConfigKnown)
        assertFalse(KeepAliveRuntimeAdapter.snapshot().active)
    }

    @Test
    fun `failed refresh cannot replace a resolved disabled switch with defaults`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val failingBridge = object : IMainProcBridge.Stub() {
            override fun getOnlineBooleanConfig(key: Int, defaultValue: Boolean): Boolean {
                error("bridge unavailable")
            }

            override fun getOnlineIntConfig(key: Int, defaultValue: Int): Int = defaultValue
            override fun getOnlineStringConfig(key: Int, defaultValue: String?): String? = defaultValue
        }
        KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = false, oneTrackEnabled = false)

        KeepAliveRuntimeAdapter.refreshOnlineConfig(context, failingBridge)

        assertTrue(KeepAliveRuntimeAdapter.snapshot().onlineConfigKnown)
        assertFalse(KeepAliveRuntimeAdapter.snapshot().enabled)
        assertFalse(KeepAliveRuntimeAdapter.snapshot().active)
        assertFalse(KeepAliveRuntimeAdapter.snapshot().oneTrackEnabled)
    }

    @Test
    fun `oneTrack read failure cannot block a resolved keep alive disable`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val partiallyAvailableBridge = object : IMainProcBridge.Stub() {
            override fun getOnlineBooleanConfig(key: Int, defaultValue: Boolean): Boolean {
                if (key == KeepAliveRuntimeAdapter.ONLINE_CONFIG_KEY_ONETRACK) {
                    error("OneTrack config unavailable")
                }
                return false
            }

            override fun getOnlineIntConfig(key: Int, defaultValue: Int): Int = defaultValue
            override fun getOnlineStringConfig(key: Int, defaultValue: String?): String? = defaultValue
        }
        KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = true)

        KeepAliveRuntimeAdapter.refreshOnlineConfig(context, partiallyAvailableBridge)

        assertTrue(KeepAliveRuntimeAdapter.snapshot().onlineConfigKnown)
        assertFalse(KeepAliveRuntimeAdapter.snapshot().enabled)
        assertFalse(KeepAliveRuntimeAdapter.snapshot().active)
        assertTrue(KeepAliveRuntimeAdapter.snapshot().oneTrackEnabled)
    }

    @Test
    fun `shutdown polling can be scheduled again after service restart`() {
        val context: Application = RuntimeEnvironment.getApplication()
        assertTrue(
            KeepAliveRuntimeAdapter.updateStrategy(
                context,
                """
                    {
                      "package":"com.example.restartable",
                      "action":"com.example.restartable.KEEP_ALIVE",
                      "app_list":["com.example.trigger"]
                    }
                """.trimIndent(),
            ),
        )
        KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = false)
        assertTrue(KeepAliveRuntimeAdapter.snapshot().active)
        assertTrue(KeepAliveRuntimeAdapter.snapshot().pollScheduled)

        KeepAliveRuntimeAdapter.shutdown()
        assertFalse(KeepAliveRuntimeAdapter.snapshot().active)
        assertFalse(KeepAliveRuntimeAdapter.snapshot().pollScheduled)

        KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = false)
        assertTrue(KeepAliveRuntimeAdapter.snapshot().active)
        assertTrue(KeepAliveRuntimeAdapter.snapshot().pollScheduled)
        KeepAliveRuntimeAdapter.shutdown()
    }

    @Test
    fun `queued shutdown cleanup cannot tear down a restarted runtime`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = "com.example.restart.race.${System.nanoTime()}"
        val trigger = "com.example.trigger.restart.race"
        val target = ComponentName(packageName, "$packageName.KeepAliveService")
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val applicationShadow = shadowOf(context)
        applicationShadow.setComponentNameAndServiceForBindService(target, Binder())
        shadowOf(activityManager).setProcesses(listOf(foregroundProcess(trigger, 1401)))

        try {
            KeepAliveRuntimeAdapter.shutdown()
            idleAdapterHandler()
            KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = false)
            assertTrue(
                KeepAliveRuntimeAdapter.updateStrategy(
                    context,
                    """
                        {
                          "package":"$packageName",
                          "class":"$packageName.KeepAliveService",
                          "app_list":["$trigger"]
                        }
                    """.trimIndent(),
                ),
            )
            invokeReconcileNow()
            advanceAdapterHandlerBy(5_000L)
            applicationShadow.boundServiceConnections.single().onServiceConnected(target, Binder())
            val unboundBeforeRestart = applicationShadow.unboundServiceConnections.size

            KeepAliveRuntimeAdapter.shutdown()
            KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = false)
            idleAdapterHandler()

            assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)
            assertEquals(unboundBeforeRestart, applicationShadow.unboundServiceConnections.size)
        } finally {
            KeepAliveRuntimeAdapter.shutdown()
            idleAdapterHandler()
        }
    }

    @Test
    fun `parses stock strategy schema and drives a binding decision`() {
        val strategy = KeepAliveRuntimeAdapter.parseStrategy(
            """
                {
                  "package":"com.example.target",
                  "class":"com.example.target.KeepAliveService",
                  "process":"com.example.target:remote",
                  "app_list":["com.example.trigger"],
                  "bind_even_alive":false,
                  "men_std":3072,
                  "mem_usage_rate":45,
                  "battery_low_rate":30,
                  "max_temperature":43.5,
                  "dev_black_list":["other-device"],
                  "need_stat":false,
                  "ignore_miui_lite":true,
                  "calm_down_period":2500
                }
            """.trimIndent(),
        )

        requireNotNull(strategy)
        assertEquals("com.example.target", strategy.targetPackage)
        assertEquals(3_072, strategy.memoryStandardMb)
        assertEquals(45, strategy.memoryUsageRate)
        assertEquals(30, strategy.batteryLowRate)
        assertEquals(43.5f, strategy.maxTemperatureCelsius)
        assertEquals(setOf("other-device"), strategy.deviceBlackList)
        assertFalse(strategy.needStat)
        assertTrue(strategy.ignoreMiuiLite)
        assertEquals(2_500, strategy.calmDownPeriodMs)
        assertEquals(
            "com.example.trigger",
            KeepAliveRuntimeAdapter.bindingTrigger(
                strategy,
                processSnapshot(
                    all = setOf("com.example.trigger"),
                    foreground = setOf("com.example.trigger"),
                ),
            ),
        )
        assertNull(
            KeepAliveRuntimeAdapter.bindingTrigger(
                strategy,
                processSnapshot(
                    all = setOf("com.example.trigger", "com.example.target:remote"),
                    foreground = setOf("com.example.trigger"),
                ),
            ),
        )
        assertEquals(
            "com.example.trigger",
            KeepAliveRuntimeAdapter.bindingTrigger(
                strategy,
                processSnapshot(
                    all = setOf("com.example.trigger", "com.example.target:remote"),
                    foreground = setOf("com.example.trigger"),
                ),
                alreadyBound = true,
            ),
        )
    }

    @Test
    fun `bind even alive strategy remains active while target process exists`() {
        val strategy = KeepAliveRuntimeAdapter.parseStrategy(
            """
                {
                  "package":"com.example.target",
                  "action":"com.example.target.KEEP_ALIVE",
                  "app_list":["com.example.trigger"],
                  "bind_even_alive":true
                }
            """.trimIndent(),
        )

        requireNotNull(strategy)
        assertTrue(strategy.targetClass.isBlank())
        assertEquals(
            "com.example.trigger",
            KeepAliveRuntimeAdapter.bindingTrigger(
                strategy,
                processSnapshot(
                    all = setOf("com.example.trigger", "com.example.target"),
                    foreground = setOf("com.example.trigger"),
                ),
            ),
        )
    }

    @Test
    fun `stock bind intent prefers action and preserves xmsf wake source`() {
        val strategy = requireNotNull(
            KeepAliveRuntimeAdapter.parseStrategy(
                """
                    {
                      "package":"com.example.target",
                      "class":"com.example.target.FallbackService",
                      "action":"com.example.target.KEEP_ALIVE",
                      "app_list":["com.example.trigger"]
                    }
                """.trimIndent(),
            ),
        )

        val intent = KeepAliveRuntimeAdapter.buildBindIntent(strategy, "com.example.trigger")

        assertEquals("com.example.target.KEEP_ALIVE", intent.action)
        assertNull(intent.component)
        assertEquals("com.example.target", intent.`package`)
        assertEquals("com.example.trigger", intent.getStringExtra("trigger_pkg"))
        assertEquals("com.xiaomi.xmsf", intent.getStringExtra("WakeUpSource"))
    }

    @Test
    fun `background trigger process does not request binding`() {
        val strategy = requireNotNull(
            KeepAliveRuntimeAdapter.parseStrategy(
                """
                    {
                      "package":"com.example.target",
                      "action":"com.example.target.KEEP_ALIVE",
                      "app_list":["com.example.trigger"]
                    }
                """.trimIndent(),
            ),
        )

        assertNull(
            KeepAliveRuntimeAdapter.bindingTrigger(
                strategy,
                processSnapshot(
                    all = setOf("com.example.trigger"),
                    foreground = emptySet(),
                ),
            ),
        )
    }

    @Test
    fun `stock calm down and retry constants preserve dex behavior`() {
        assertEquals(5_000L, KeepAliveRuntimeAdapter.effectiveCalmDownMs(0))
        assertEquals(5_000L, KeepAliveRuntimeAdapter.effectiveCalmDownMs(1_999))
        assertEquals(2_000L, KeepAliveRuntimeAdapter.effectiveCalmDownMs(2_000))
        assertEquals(7_500L, KeepAliveRuntimeAdapter.effectiveCalmDownMs(7_500))
        assertEquals(5_000L, KeepAliveRuntimeAdapter.BIND_RETRY_INTERVAL_MS)
        assertEquals(3, KeepAliveRuntimeAdapter.MAX_BIND_RETRY_COUNT)
    }

    @Test
    fun `strategy waits for ServiceBox KASwitch decision before binding`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = "com.example.configgate.${System.nanoTime()}"
        val target = ComponentName(packageName, "$packageName.KeepAliveService")
        val activityManager = context.getSystemService(ActivityManager::class.java)
        shadowOf(activityManager).setProcesses(
            listOf(ActivityManager.RunningAppProcessInfo("com.example.configgate.trigger", 1234, emptyArray())),
        )
        val applicationShadow = shadowOf(context)
        applicationShadow.setComponentNameAndServiceForBindService(target, Binder())

        try {
            KeepAliveRuntimeAdapter.shutdown()
            idleAdapterHandler()
            val boundCount = applicationShadow.boundServiceConnections.size

            assertTrue(
                KeepAliveRuntimeAdapter.updateStrategy(
                    context,
                    """
                        {
                          "package":"$packageName",
                          "class":"$packageName.KeepAliveService",
                          "app_list":["com.example.configgate.trigger"]
                        }
                    """.trimIndent(),
                ),
            )
            idleAdapterHandler()
            assertFalse(KeepAliveRuntimeAdapter.snapshot().active)
            assertFalse(KeepAliveRuntimeAdapter.snapshot().onlineConfigKnown)
            assertEquals(boundCount, applicationShadow.boundServiceConnections.size)

            KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = false, oneTrackEnabled = false)
            idleAdapterHandler()
            assertFalse(KeepAliveRuntimeAdapter.snapshot().active)
            assertTrue(KeepAliveRuntimeAdapter.snapshot().onlineConfigKnown)
            assertEquals(boundCount, applicationShadow.boundServiceConnections.size)

            KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = false)
            invokeReconcileNow()
            advanceAdapterHandlerBy(5_000L)
            assertTrue(KeepAliveRuntimeAdapter.snapshot().active)
            assertTrue(applicationShadow.boundServiceConnections.size > boundCount)
        } finally {
            KeepAliveRuntimeAdapter.shutdown()
            idleAdapterHandler()
        }
    }

    @Test
    fun `persisted enabled state cannot bypass unresolved online config`() {
        assertFalse(
            KeepAliveRuntimeAdapter.shouldReconcile(
                onlineConfigKnown = false,
                active = true,
                enabled = true,
                strategyCount = 1,
            ),
        )
        assertTrue(
            KeepAliveRuntimeAdapter.shouldReconcile(
                onlineConfigKnown = true,
                active = true,
                enabled = true,
                strategyCount = 1,
            ),
        )
    }

    @Test
    fun `accepted strategy is consumed by the keep alive runtime`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val target = ComponentName("com.example.bound", "com.example.bound.KeepAliveService")
        val activityManager = context.getSystemService(ActivityManager::class.java)
        shadowOf(activityManager).setProcesses(
            listOf(ActivityManager.RunningAppProcessInfo("com.example.trigger.bound", 1234, emptyArray())),
        )
        val applicationShadow = shadowOf(context)
        applicationShadow.setComponentNameAndServiceForBindService(target, Binder())

        KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = false)
        assertTrue(
            KeepAliveRuntimeAdapter.updateStrategy(
                context,
                """
                    {
                      "package":"com.example.bound",
                      "class":"com.example.bound.KeepAliveService",
                      "app_list":["com.example.trigger.bound"]
                    }
                """.trimIndent(),
            ),
        )
        invokeReconcileNow()
        advanceAdapterHandlerBy(5_000L)

        assertTrue(applicationShadow.boundServiceConnections.isNotEmpty())
        shadowOf(activityManager).setProcesses(emptyList())
        invokeReconcileNow()
        advanceAdapterHandlerBy(5_000L)
        assertTrue(applicationShadow.unboundServiceConnections.isNotEmpty())
        KeepAliveRuntimeAdapter.shutdown()
    }

    @Test
    fun `unexpected service disconnect clears binding and retries while trigger stays foreground`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = "com.example.disconnect.${System.nanoTime()}"
        val target = ComponentName(packageName, "$packageName.KeepAliveService")
        val trigger = "com.example.trigger.disconnect"
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val applicationShadow = shadowOf(context)
        applicationShadow.setComponentNameAndServiceForBindService(target, Binder())
        shadowOf(activityManager).setProcesses(
            listOf(foregroundProcess(trigger, 1301)),
        )

        try {
            KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = false)
            assertTrue(
                KeepAliveRuntimeAdapter.updateStrategy(
                    context,
                    """
                        {
                          "package":"$packageName",
                          "class":"$packageName.KeepAliveService",
                          "app_list":["$trigger"]
                        }
                    """.trimIndent(),
                ),
            )
            invokeReconcileNow()
            advanceAdapterHandlerBy(5_000L)
            val firstConnection = applicationShadow.boundServiceConnections.single()
            firstConnection.onServiceConnected(target, Binder())
            assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)

            firstConnection.onServiceDisconnected(target)
            assertFalse(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)
            advanceAdapterHandlerBy(5_000L)

            assertTrue(applicationShadow.unboundServiceConnections.isNotEmpty())
            applicationShadow.boundServiceConnections.single().onServiceConnected(target, Binder())
            assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)
        } finally {
            KeepAliveRuntimeAdapter.shutdown()
            idleAdapterHandler()
        }
    }

    @Test
    fun `binding death clears binding and retries while trigger stays foreground`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = "com.example.bindingdied.${System.nanoTime()}"
        val target = ComponentName(packageName, "$packageName.KeepAliveService")
        val trigger = "com.example.trigger.bindingdied"
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val applicationShadow = shadowOf(context)
        applicationShadow.setComponentNameAndServiceForBindService(target, Binder())
        shadowOf(activityManager).setProcesses(listOf(foregroundProcess(trigger, 1302)))

        try {
            KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = false)
            assertTrue(
                KeepAliveRuntimeAdapter.updateStrategy(
                    context,
                    """
                        {
                          "package":"$packageName",
                          "class":"$packageName.KeepAliveService",
                          "app_list":["$trigger"]
                        }
                    """.trimIndent(),
                ),
            )
            invokeReconcileNow()
            advanceAdapterHandlerBy(5_000L)
            val firstConnection = applicationShadow.boundServiceConnections.single()
            firstConnection.onServiceConnected(target, Binder())
            assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)

            firstConnection.onBindingDied(target)
            assertFalse(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)
            advanceAdapterHandlerBy(5_000L)

            assertTrue(applicationShadow.unboundServiceConnections.isNotEmpty())
            applicationShadow.boundServiceConnections.single().onServiceConnected(target, Binder())
            assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)
        } finally {
            KeepAliveRuntimeAdapter.shutdown()
            idleAdapterHandler()
        }
    }

    @Test
    fun `device blacklist update removes a previously accepted strategy`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = "com.example.blacklisted.${System.nanoTime()}"
        val target = ComponentName(packageName, "$packageName.KeepAliveService")
        val activityManager = context.getSystemService(ActivityManager::class.java)
        shadowOf(activityManager).setProcesses(
            listOf(ActivityManager.RunningAppProcessInfo("com.example.trigger", 1234, emptyArray())),
        )
        val applicationShadow = shadowOf(context)
        applicationShadow.setComponentNameAndServiceForBindService(target, Binder())
        val accepted = """
            {
              "package":"$packageName",
              "class":"$packageName.KeepAliveService",
              "app_list":["com.example.trigger"]
            }
        """.trimIndent()
        val blocked = """
            {
              "package":"$packageName",
              "class":"$packageName.KeepAliveService",
              "app_list":["com.example.trigger"],
              "dev_black_list":["${Build.DEVICE}"]
            }
        """.trimIndent()

        try {
            KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = false)
            assertTrue(KeepAliveRuntimeAdapter.updateStrategy(context, accepted))
            invokeReconcileNow()
            advanceAdapterHandlerBy(5_000L)
            applicationShadow.boundServiceConnections.single().onServiceConnected(target, Binder())
            assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)
            val boundCount = applicationShadow.boundServiceConnections.size
            val unboundCount = applicationShadow.unboundServiceConnections.size

            assertTrue(KeepAliveRuntimeAdapter.updateStrategy(context, blocked))
            idleAdapterHandler()

            assertFalse(packageName in KeepAliveRuntimeAdapter.snapshot().strategyPackages)
            assertFalse(requireNotNull(KeepAliveRuntimeAdapter.parseStrategy(blocked)).supportedOnDevice)
            assertFalse(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)
            assertTrue(applicationShadow.unboundServiceConnections.size > unboundCount)
            assertTrue(applicationShadow.boundServiceConnections.size < boundCount)
        } finally {
            KeepAliveRuntimeAdapter.shutdown()
            idleAdapterHandler()
        }
    }

    @Test
    fun `new foreground trigger takes ownership and only that owner can unbind`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = "com.example.owner.${System.nanoTime()}"
        val target = ComponentName(packageName, "$packageName.KeepAliveService")
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val applicationShadow = shadowOf(context)
        applicationShadow.setComponentNameAndServiceForBindService(target, Binder())
        val triggerOne = "com.example.trigger.one"
        val triggerTwo = "com.example.trigger.two"

        try {
            shadowOf(activityManager).setProcesses(listOf(foregroundProcess(triggerOne, 1101)))
            KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = true, oneTrackEnabled = false)
            assertTrue(
                KeepAliveRuntimeAdapter.updateStrategy(
                    context,
                    """
                        {
                          "package":"$packageName",
                          "class":"$packageName.KeepAliveService",
                          "app_list":["$triggerOne","$triggerTwo"],
                          "calm_down_period":2000
                        }
                    """.trimIndent(),
                ),
            )
            invokeReconcileNow()
            advanceAdapterHandlerBy(2_000L)
            applicationShadow.boundServiceConnections.single().onServiceConnected(target, Binder())
            assertEquals(triggerOne, KeepAliveRuntimeAdapter.snapshot().bindingOwners[packageName])

            shadowOf(activityManager).setProcesses(
                listOf(foregroundProcess(triggerOne, 1101), foregroundProcess(triggerTwo, 1102)),
            )
            invokeReconcileNow()
            assertEquals(triggerTwo, KeepAliveRuntimeAdapter.snapshot().bindingOwners[packageName])

            shadowOf(activityManager).setProcesses(listOf(foregroundProcess(triggerTwo, 1102)))
            invokeReconcileNow()
            advanceAdapterHandlerBy(2_000L)
            assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)

            shadowOf(activityManager).setProcesses(emptyList())
            invokeReconcileNow()
            advanceAdapterHandlerBy(1_999L)
            assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)
            advanceAdapterHandlerBy(1L)
            assertFalse(packageName in KeepAliveRuntimeAdapter.snapshot().boundTargetPackages)
        } finally {
            KeepAliveRuntimeAdapter.shutdown()
            idleAdapterHandler()
        }
    }

    @Test
    fun `rejects incomplete or malformed strategy`() {
        assertNull(KeepAliveRuntimeAdapter.parseStrategy("{}"))
        assertNull(KeepAliveRuntimeAdapter.parseStrategy("not-json"))
        assertNull(
            KeepAliveRuntimeAdapter.parseStrategy(
                """{"package":"invalid","class":"Target"}""",
            ),
        )
    }

    @Test
    fun `clearing package removes strategy and persisted configuration`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = "com.example.keepalive.clear"
        val config = """
            {
              "package":"$packageName",
              "class":"$packageName.KeepAliveService",
              "app_list":[]
            }
        """.trimIndent()

        KeepAliveRuntimeAdapter.updateOnlineConfig(context, keepAliveEnabled = false, oneTrackEnabled = false)
        assertTrue(KeepAliveRuntimeAdapter.updateStrategy(context, config))
        assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().strategyPackages)

        KeepAliveRuntimeAdapter.clearPackageState(context, packageName)
        idleAdapterHandler()

        assertFalse(packageName in KeepAliveRuntimeAdapter.snapshot().strategyPackages)
        assertNull(
            context.getSharedPreferences("stock_keepalive_runtime", 0)
                .getString("strategy:$packageName", null),
        )
        KeepAliveRuntimeAdapter.shutdown()
        idleAdapterHandler()
    }

    @Test
    fun `clearing package for another Android user does not mutate local strategy`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = "com.example.keepalive.foreign.user"
        val config = """
            {
              "package":"$packageName",
              "class":"$packageName.KeepAliveService",
              "app_list":[]
            }
        """.trimIndent()

        KeepAliveRuntimeAdapter.updateStrategy(context, config)
        assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().strategyPackages)

        KeepAliveRuntimeAdapter.clearPackageState(context, packageName, userId = 999)

        assertTrue(packageName in KeepAliveRuntimeAdapter.snapshot().strategyPackages)
        KeepAliveRuntimeAdapter.clearPackageState(context, packageName)
        KeepAliveRuntimeAdapter.shutdown()
        idleAdapterHandler()
    }

    private fun invokeReconcileNow() {
        KeepAliveRuntimeAdapter::class.java.getDeclaredMethod("reconcileNow")
            .apply { isAccessible = true }
            .invoke(KeepAliveRuntimeAdapter)
    }

    private fun idleAdapterHandler() {
        val handler = KeepAliveRuntimeAdapter::class.java.getDeclaredMethod("getHandler")
            .apply { isAccessible = true }
            .invoke(KeepAliveRuntimeAdapter) as Handler
        shadowOf(handler.looper).idle()
    }

    private fun advanceAdapterHandlerBy(durationMs: Long) {
        val handler = KeepAliveRuntimeAdapter::class.java.getDeclaredMethod("getHandler")
            .apply { isAccessible = true }
            .invoke(KeepAliveRuntimeAdapter) as Handler
        shadowOf(handler.looper).idleFor(durationMs, TimeUnit.MILLISECONDS)
    }

    private fun processSnapshot(
        all: Set<String>,
        foreground: Set<String>,
    ) = KeepAliveRuntimeAdapter.ProcessSnapshot(
        allProcessNames = all,
        foregroundActivityProcessNames = foreground,
    )

    private fun foregroundProcess(name: String, pid: Int): ActivityManager.RunningAppProcessInfo {
        return ActivityManager.RunningAppProcessInfo(name, pid, emptyArray()).apply {
            importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
