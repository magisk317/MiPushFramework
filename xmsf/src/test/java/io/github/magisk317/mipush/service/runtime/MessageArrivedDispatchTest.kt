package io.github.magisk317.mipush.service.runtime

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class MessageArrivedDispatchTest {
    @AfterEach
    fun clearMocks() {
        MockMessageRegistry.clearAllForTests()
    }

    @Test
    fun `running app with receiver gets exact stock message arrived broadcast`() {
        val application: Application = RuntimeEnvironment.getApplication()
        setRunningPackages(application, listOf(TARGET_PACKAGE))
        registerMessageArrivedReceiver(application)
        val context = CapturingContext(application)
        val payload = byteArrayOf(1, 2, 3)

        assertTrue(
            MyMIPushNotificationHelper.dispatchMessageArrivedIfNeeded(
                context,
                displayContainer(),
                payload,
            ),
        )
        assertEquals(PushConstants.MIPUSH_ACTION_MESSAGE_ARRIVED, context.broadcast?.action)
        assertEquals(TARGET_PACKAGE, context.broadcast?.`package`)
        assertTrue(payload.contentEquals(context.broadcast?.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)))
        assertEquals("$TARGET_PACKAGE.permission.MIPUSH_RECEIVE", context.permission)
    }

    @Test
    fun `message arrived requires running process and declared receiver`() {
        val application: Application = RuntimeEnvironment.getApplication()
        val context = CapturingContext(application)
        val container = displayContainer()

        setRunningPackages(application, emptyList())
        assertFalse(MyMIPushNotificationHelper.dispatchMessageArrivedIfNeeded(context, container, byteArrayOf()))

        setRunningPackages(application, listOf(TARGET_PACKAGE))
        assertFalse(MyMIPushNotificationHelper.dispatchMessageArrivedIfNeeded(context, container, byteArrayOf()))
        assertEquals(null, context.broadcast)
    }

    @Test
    fun `stock target package owns receiver and permission for delegated payload`() {
        val application: Application = RuntimeEnvironment.getApplication()
        setRunningPackages(application, listOf(TARGET_PACKAGE))
        registerMessageArrivedReceiver(application)
        val context = CapturingContext(application)
        val container = displayContainer().apply {
            packageName = PushConstants.PUSH_SERVICE_PACKAGE_NAME
            metaInfo.putToExtra(MIPushNotificationHelper.MIUI_PACKAGE_NAME, TARGET_PACKAGE)
        }

        assertTrue(MyMIPushNotificationHelper.dispatchMessageArrivedIfNeeded(context, container, byteArrayOf(7)))
        assertEquals(TARGET_PACKAGE, context.broadcast?.`package`)
        assertEquals("$TARGET_PACKAGE.permission.MIPUSH_RECEIVE", context.permission)
    }

    @Test
    fun `push extension service alone does not make target eligible`() {
        val application: Application = RuntimeEnvironment.getApplication()
        registerMessageArrivedReceiver(application)
        val context = CapturingContext(application)

        setRunningPackages(
            application,
            listOf(TARGET_PACKAGE),
            processName = "$TARGET_PACKAGE:pushExtensionService",
        )
        assertFalse(MyMIPushNotificationHelper.dispatchMessageArrivedIfNeeded(context, displayContainer(), byteArrayOf()))

        setRunningPackages(application, listOf(TARGET_PACKAGE), processName = "$TARGET_PACKAGE:worker")
        assertTrue(MyMIPushNotificationHelper.dispatchMessageArrivedIfNeeded(context, displayContainer(), byteArrayOf()))
    }

    @Test
    fun `notify foreground policy suppresses callback only while target is foreground`() {
        val application: Application = RuntimeEnvironment.getApplication()
        registerMessageArrivedReceiver(application)
        val context = CapturingContext(application)
        val container = displayContainer().apply {
            metaInfo.putToExtra(MIPushNotificationHelper.EXTRA_PARAM_NOTIFY_FOREGROUND, "0")
        }

        setRunningPackages(application, listOf(TARGET_PACKAGE), ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        assertFalse(MyMIPushNotificationHelper.dispatchMessageArrivedIfNeeded(context, container, byteArrayOf()))

        setRunningPackages(application, listOf(TARGET_PACKAGE), ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
        assertTrue(MyMIPushNotificationHelper.dispatchMessageArrivedIfNeeded(context, container, byteArrayOf()))
    }

    @Test
    fun `business pass through mock and direct helper paths do not dispatch`() {
        val display = displayContainer()
        val business = display.deepCopy().apply { metaInfo.isIgnoreRegInfo = true }
        val passThrough = display.deepCopy().apply { metaInfo.passThrough = 1 }

        assertFalse(MyMIPushNotificationHelper.shouldDispatchMessageArrived(display, dispatchRequested = false))
        assertFalse(MyMIPushNotificationHelper.shouldDispatchMessageArrived(business, dispatchRequested = true))
        assertFalse(MyMIPushNotificationHelper.shouldDispatchMessageArrived(passThrough, dispatchRequested = true))

        MockMessageRegistry.mark(display)
        assertFalse(MyMIPushNotificationHelper.shouldDispatchMessageArrived(display, dispatchRequested = true))
    }

    private fun registerMessageArrivedReceiver(application: Application) {
        val component = ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.MessageArrivedReceiver")
        shadowOf(application.packageManager).apply {
            addReceiverIfNotPresent(component)
            addIntentFilterForReceiver(
                component,
                IntentFilter(PushConstants.MIPUSH_ACTION_MESSAGE_ARRIVED),
            )
        }
    }

    private fun displayContainer(): XmPushActionContainer {
        return XmPushActionContainer().apply {
            packageName = TARGET_PACKAGE
            action = ActionType.SendMessage
            metaInfo = PushMetaInfo().apply {
                id = "s123456789012345678901"
                title = "Weather"
                description = "Sunny"
                passThrough = 0
            }
        }
    }

    private fun setRunningPackages(
        application: Application,
        packages: List<String>,
        importance: Int = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
        processName: String? = null,
    ) {
        val activityManager = application.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(activityManager).setProcesses(
            packages.mapIndexed { index, packageName ->
                ActivityManager.RunningAppProcessInfo(processName ?: packageName, 1000 + index, arrayOf(packageName)).apply {
                    this.importance = importance
                }
            },
        )
    }

    private class CapturingContext(base: Application) : ContextWrapper(base) {
        var broadcast: Intent? = null
        var permission: String? = null

        override fun sendBroadcast(intent: Intent, receiverPermission: String?) {
            broadcast = Intent(intent)
            permission = receiverPermission
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.example.weather"
    }
}
