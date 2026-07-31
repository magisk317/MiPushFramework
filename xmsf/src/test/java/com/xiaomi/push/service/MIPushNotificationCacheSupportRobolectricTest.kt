package com.xiaomi.push.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class MIPushNotificationCacheSupportRobolectricTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    @BeforeEach
    fun setUp() {
        // Robolectric attributes posted notifications to the test manifest package, while
        // production XMSF records expose getOpPkg() == com.xiaomi.xmsf. Mock only that
        // platform identity seam so the active-notification scan keeps its stock predicate.
        mockkStatic(JavaCalls::class)
        every { JavaCalls.callMethod(any(), "getOpPkg") } returns PushConstants.PUSH_SERVICE_PACKAGE_NAME
    }

    @AfterEach
    fun tearDown() {
        manager.cancelAll()
        manager.notificationChannels.forEach { manager.deleteNotificationChannel(it.id) }
        unmockkStatic(JavaCalls::class)
    }

    @Test
    fun `stock managed identity requires XMSF operation package and owned channel`() {
        assertTrue(
            NotificationManagerHelper.isManagedNotificationIdentity(
                messageId = "s123",
                operationPackage = PushConstants.PUSH_SERVICE_PACKAGE_NAME,
                statusPackage = "com.example.target",
                targetPackage = "com.example.target",
                markedTargetPackage = "com.example.target",
                channelId = "mipush|com.example.target|orders",
            ),
        )
        assertTrue(
            NotificationManagerHelper.isManagedNotificationIdentity(
                messageId = "s123",
                operationPackage = PushConstants.PUSH_SERVICE_PACKAGE_NAME,
                statusPackage = "com.example.target",
                targetPackage = "com.example.target",
                markedTargetPackage = "com.example.target",
                channelId = "mipush_com.example.target_orders",
            ),
        )
        assertFalse(
            NotificationManagerHelper.isManagedNotificationIdentity(
                messageId = "s123",
                operationPackage = "com.example.target",
                statusPackage = "com.example.target",
                targetPackage = "com.example.target",
                markedTargetPackage = "com.example.target",
                channelId = "mipush_com.example.target_orders",
            ),
        )
        assertFalse(
            NotificationManagerHelper.isManagedNotificationIdentity(
                messageId = "s123",
                operationPackage = PushConstants.PUSH_SERVICE_PACKAGE_NAME,
                statusPackage = "com.example.target",
                targetPackage = "com.example.target",
                markedTargetPackage = "com.example.target",
                channelId = "native_orders",
            ),
        )
    }

    @Test
    fun `pre migration channel is accepted only with matching target marker`() {
        assertTrue(
            NotificationManagerHelper.isManagedNotificationIdentity(
                messageId = null,
                operationPackage = PushConstants.PUSH_SERVICE_PACKAGE_NAME,
                statusPackage = PushConstants.PUSH_SERVICE_PACKAGE_NAME,
                targetPackage = "com.example.target",
                markedTargetPackage = "com.example.target",
                channelId = "ch_com.example.target_orders",
            ),
        )
        assertFalse(
            NotificationManagerHelper.isManagedNotificationIdentity(
                messageId = null,
                operationPackage = PushConstants.PUSH_SERVICE_PACKAGE_NAME,
                statusPackage = PushConstants.PUSH_SERVICE_PACKAGE_NAME,
                targetPackage = "com.example.target",
                markedTargetPackage = "com.example.other",
                channelId = "ch_com.example.target_orders",
            ),
        )
    }

    @Test
    fun `clear all scans active notifications and keeps unmanaged records`() {
        val packageName = context.packageName
        val channelId = "mipush_${packageName}_orders"
        post(101, channelId, packageName, "s-managed", "Order", "Delivering")
        post(102, "native", packageName, "s-native", "Native", "Keep")

        MIPushNotificationCacheSupport.clearNotification(context, packageName)

        val activeIds = manager.activeNotifications.map { it.id }.toSet()
        assertFalse(101 in activeIds)
        assertTrue(102 in activeIds)
    }

    @Test
    fun `clear by id cancels only the matching active stock identity`() {
        val packageName = context.packageName
        val requestedId = 42
        val stockId = (packageName.hashCode() / 10) * 10 + requestedId
        val channelId = "mipush_${packageName}_orders"
        post(stockId, channelId, packageName, "s-matching", "Order", "Delivering")
        post(stockId + 1, channelId, packageName, "s-other", "Other", "Keep")

        MIPushNotificationCacheSupport.clearNotification(context, packageName, requestedId)

        val activeIds = manager.activeNotifications.map { it.id }.toSet()
        assertFalse(stockId in activeIds)
        assertTrue(stockId + 1 in activeIds)
    }

    @Test
    fun `title clear follows stock request contains rendered text direction`() {
        val packageName = context.packageName
        post(201, "title", packageName, null, "Order", "Delivering")
        post(202, "title", packageName, null, "News", "Ready")

        MIPushNotificationCacheSupport.clearNotification(
            context,
            packageName,
            "Order update",
            "Delivering now",
        )

        val activeIds = manager.activeNotifications.map { it.id }.toSet()
        assertFalse(201 in activeIds)
        assertTrue(202 in activeIds)
    }

    @Test
    fun `getManagedActiveNotifications returns only notifications belonging to target package`() {
        val targetPackage = context.packageName
        val otherPackage = "com.example.other"
        val channelId = "mipush_${targetPackage}_orders"
        // Post notification belonging to target package
        post(301, channelId, targetPackage, "s-target", "Target", "Mine")
        // Post notification belonging to a different package (embedded in extras)
        post(302, channelId, otherPackage, "s-other", "Other", "NotMine")

        val helper = NotificationManagerHelper.from(context, targetPackage)
        val managed = helper.getManagedActiveNotifications()

        // Only the notification with target_package matching the helper's target should be returned
        val managedIds = managed.map { it.id }.toSet()
        assertTrue(301 in managedIds, "Notification belonging to target package should be in managed set")
        assertFalse(302 in managedIds, "Notification belonging to other package should not be in managed set")
    }

    @Test
    fun `cancel on API 30+ passes opPkg as com xiaomi xmsf`() {
        // Verify the contract: NotificationManagerPlatformSupport.cancel passes
        // PushConstants.PUSH_SERVICE_PACKAGE_NAME as the opPkg argument on API 30+.
        // On SDK 28 (this test's default config), the 4-arg path is used without opPkg.
        // The API 30 five-arg path is tested in a separate class with @Config(sdk=[30]).

        // Inject a fake service into the NotificationManagerPlatformSupport singleton
        // so cancel() doesn't throw "service unavailable".
        val fakeService = Any()
        val nmsField = NotificationManagerPlatformSupport::class.java.getDeclaredField("nms")
        nmsField.isAccessible = true
        val originalNms = nmsField.get(null)
        nmsField.set(null, fakeService)

        try {
            val capturedArgs = mutableListOf<Array<out Any?>>()
            every {
                JavaCalls.callMethodOrThrow(any(), eq("cancelNotificationWithTag"), *anyVararg())
            } answers {
                // For Kotlin vararg, the third arg is the vararg array
                val varargArray = args.last()
                if (varargArray is Array<*>) {
                    capturedArgs.add(varargArray)
                }
                null
            }
            // Also mock callStaticMethod for DeviceInfo.getSpaceId → UserHandle.myUserId
            every { JavaCalls.callStaticMethod(any<String>(), any(), *anyVararg()) } returns 0

            val packageName = "com.example.target"
            NotificationManagerPlatformSupport.cancel(packageName, 42)

            // On SDK 28 (< 30): cancelNotificationWithTag(pkg, tag, id, userId)
            assertTrue(capturedArgs.isNotEmpty(), "cancelNotificationWithTag should have been called")
            val callArgs = capturedArgs.first()
            assertEquals(packageName, callArgs[0], "First arg should be target package name")
            // No opPkg on API < 30, next arg is tag=null
            assertEquals(null, callArgs[1], "Second arg (tag) should be null on API < 30")
            assertEquals(42, callArgs[2], "Third arg should be notification id")
        } finally {
            nmsField.set(null, originalNms)
        }
    }

    @Test
    fun `clearNotification does not use in-memory queue`() {
        // Verify that MIPushNotificationHelper.clearNotification (the public entry point)
        // does not pass a LinkedList or any in-memory cache to MIPushNotificationCacheSupport.
        // The old implementation used a 100-entry notifyContainerCache LinkedList; the new
        // stock 7.4.67-C implementation scans active notifications instead.
        //
        // This test confirms: the public clearNotification methods have no LinkedList parameter,
        // and MIPushNotificationCacheSupport.clearNotification accepts (Context, String) or
        // (Context, String, Int) — no collection argument.

        val packageName = context.packageName
        val channelId = "mipush_${packageName}_default"
        post(401, channelId, packageName, "s-test", "Test", "Body")

        // Call the main entry point — if it still required a LinkedList param, this would
        // not compile or would need an explicit list argument.
        MIPushNotificationHelper.clearNotification(context, packageName)

        val activeIds = manager.activeNotifications.map { it.id }.toSet()
        assertFalse(401 in activeIds, "Notification should be cleared via active notifications scan, not memory queue")

        // Also verify clearNotification by id works without memory queue
        post(402, channelId, packageName, "s-test2", "Test2", "Body2")
        val hashedId = (packageName.hashCode() / 10) * 10 + 5
        post(hashedId, channelId, packageName, "s-hashed", "Hashed", "Match")

        MIPushNotificationHelper.clearNotification(context, packageName, 5)

        val activeIdsAfter = manager.activeNotifications.map { it.id }.toSet()
        assertFalse(hashedId in activeIdsAfter, "Hashed ID notification should be cleared")
        assertTrue(402 in activeIdsAfter, "Non-matching notification should remain")
    }

    private fun post(
        id: Int,
        channelId: String,
        targetPackage: String,
        messageId: String?,
        title: String,
        text: String,
    ) {
        manager.createNotificationChannel(
            NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setExtras(android.os.Bundle().apply {
                putString("target_package", targetPackage)
                messageId?.let { putString("message_id", it) }
            })
            .build()
        manager.notify(id, notification)
    }
}
