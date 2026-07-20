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
    fun `parses stock strategy schema and drives a binding decision`() {
        val strategy = KeepAliveRuntimeAdapter.parseStrategy(
            """
                {
                  "package":"com.example.target",
                  "class":"com.example.target.KeepAliveService",
                  "process":"com.example.target:remote",
                  "app_list":["com.example.trigger"],
                  "bind_even_alive":false
                }
            """.trimIndent(),
        )

        requireNotNull(strategy)
        assertEquals("com.example.target", strategy.targetPackage)
        assertEquals(
            "com.example.trigger",
            KeepAliveRuntimeAdapter.bindingTrigger(strategy, setOf("com.example.trigger")),
        )
        assertNull(
            KeepAliveRuntimeAdapter.bindingTrigger(
                strategy,
                setOf("com.example.trigger", "com.example.target:remote"),
            ),
        )
        assertEquals(
            "com.example.trigger",
            KeepAliveRuntimeAdapter.bindingTrigger(
                strategy,
                setOf("com.example.trigger", "com.example.target:remote"),
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
                setOf("com.example.trigger", "com.example.target"),
            ),
        )
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
            assertTrue(KeepAliveRuntimeAdapter.snapshot().active)
            assertTrue(applicationShadow.boundServiceConnections.size > boundCount)
        } finally {
            KeepAliveRuntimeAdapter.shutdown()
            idleAdapterHandler()
        }
    }

    @Test
    fun `accepted strategy is consumed by the reduced polling binder`() {
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

        assertTrue(applicationShadow.boundServiceConnections.isNotEmpty())
        shadowOf(activityManager).setProcesses(emptyList())
        invokeReconcileNow()
        assertTrue(applicationShadow.unboundServiceConnections.isNotEmpty())
        KeepAliveRuntimeAdapter.shutdown()
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
    fun `rejects incomplete or malformed strategy`() {
        assertNull(KeepAliveRuntimeAdapter.parseStrategy("{}"))
        assertNull(KeepAliveRuntimeAdapter.parseStrategy("not-json"))
        assertNull(
            KeepAliveRuntimeAdapter.parseStrategy(
                """{"package":"invalid","class":"Target"}""",
            ),
        )
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
}
