package io.github.magisk317.mipush.push.pipeline

import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.MIPushAppAbsentManager
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import com.xiaomi.xmsf.stock.StockProfileIdStore
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class PackageDataClearedCoordinatorTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @BeforeEach
    fun setUp() {
        listOf(
            PushServiceConstants.PREF_KEY_REGISTERED_PKGS,
            "pref_pending_registration",
            "pref_notify_type",
            "mipush_profile_id",
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        PushRuntimeRegistrationTaskStore.clear()
        PushRuntimePendingPacketStore.clearForTests()
    }

    @Test
    fun `data clear invalidates old state and emits the stock 7x payload`() {
        val packageName = "com.example.data.clear"
        MIPushAppAbsentManager.rememberRegisteredPackage(context, packageName, "confirmed-app-id")
        MIPushAppAbsentManager.rememberPendingRegistration(context, packageName, "pending-app-id")
        MIPushNotificationHelper.setLocalNotifyType(context, packageName, 7)
        context.getSharedPreferences("mipush_profile_id", Context.MODE_PRIVATE)
            .edit()
            .putString(packageName, "profile-a")
            .commit()
        PushRuntimeRegistrationTaskStore.cache(
            packageName,
            Intent("com.example.REGISTER"),
            "PackageDataClearedCoordinatorTest",
        )
        PushRuntimePendingPacketStore.cacheRegistrationRequest(packageName, byteArrayOf(1))
        PushRuntimePendingPacketStore.addPendingMessage(packageName, byteArrayOf(2))

        var dispatchedPayload: ByteArray? = null
        val result = PackageDataClearedCoordinator.handle(
            context,
            packageName,
            AppDataClearedPacketDispatcher { _, payload ->
                dispatchedPayload = payload.copyOf()
                true
            },
        )

        assertTrue(result.appIdPresent)
        assertTrue(result.packetAccepted)
        assertEquals(0, result.cleanupFailureCount)
        assertNull(MIPushAppAbsentManager.getRememberedAppId(context, packageName))
        assertNull(MIPushAppAbsentManager.getPendingRegistrationAppId(context, packageName))
        assertFalse(MIPushNotificationHelper.hasLocalNotifyType(context, packageName))
        assertTrue(StockProfileIdStore.read(context, packageName).isEmpty())
        assertTrue(PushRuntimeRegistrationTaskStore.pendingTasks().isEmpty())
        assertEquals(0, PushRuntimePendingPacketStore.pendingRegistrationCount())
        assertEquals(0, PushRuntimePendingPacketStore.pendingMessageCount())

        val container = XmPushActionContainer()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, requireNotNull(dispatchedPayload))
        assertEquals(ActionType.Notification, container.action)
        assertEquals(packageName, container.packageName)
        assertEquals("confirmed-app-id", container.appid)
        assertTrue(container.isIsRequest)

        val notification = XmPushActionNotification()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(notification, container.getPushAction())
        assertEquals("confirmed-app-id", notification.appId)
        assertEquals(PackageDataClearedCoordinator.APP_DATA_CLEARED_TYPE, notification.type)
        assertFalse(notification.isRequireAck)
    }

    @Test
    fun `pending registration alone is cleared without claiming a server registration`() {
        val packageName = "com.example.pending.only"
        MIPushAppAbsentManager.rememberPendingRegistration(context, packageName, "pending-app-id")
        var dispatchCount = 0

        val result = PackageDataClearedCoordinator.handle(
            context,
            packageName,
            AppDataClearedPacketDispatcher { _, _ ->
                dispatchCount += 1
                true
            },
        )

        assertFalse(result.appIdPresent)
        assertFalse(result.packetAccepted)
        assertEquals(0, result.cleanupFailureCount)
        assertEquals(0, dispatchCount)
        assertNull(MIPushAppAbsentManager.getPendingRegistrationAppId(context, packageName))
    }
}
