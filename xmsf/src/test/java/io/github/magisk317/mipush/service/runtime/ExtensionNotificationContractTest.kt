package io.github.magisk317.mipush.service.runtime

import com.xiaomi.mipush.sdk.aidl.RemoteNotificationContent
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.Target
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionNotificationContractTest {
    @Test
    fun `hyper os eligibility matches stock boundary`() {
        assertFalse(ExtensionNotificationContract.isSupportedHyperOs("2", "OS2.0.999", "3.9"))
        assertFalse(ExtensionNotificationContract.isSupportedHyperOs("3", "OS3.0.099", "3.9"))
        assertTrue(ExtensionNotificationContract.isSupportedHyperOs("3", "OS3.0.100", null))
        assertTrue(ExtensionNotificationContract.isSupportedHyperOs("3", "malformed", "3.1"))
        assertFalse(ExtensionNotificationContract.isSupportedHyperOs("3", "malformed", "3.0"))
        assertTrue(ExtensionNotificationContract.isSupportedHyperOs("4", null, null))
    }

    @Test
    fun `only the stock extension process name is eligible`() {
        assertTrue(ExtensionNotificationContract.hasExpectedProcess(TARGET_PACKAGE, "$TARGET_PACKAGE:pushExtensionService"))
        assertFalse(ExtensionNotificationContract.hasExpectedProcess(TARGET_PACKAGE, TARGET_PACKAGE))
        assertFalse(ExtensionNotificationContract.hasExpectedProcess(TARGET_PACKAGE, "$TARGET_PACKAGE:push"))
    }

    @Test
    fun `suppression callback wins once and consumes pending fallback`() {
        var now = 100L
        val registry = ExtensionPendingRegistry(elapsedRealtime = { now })
        registry.register(TARGET_PACKAGE, MESSAGE_ID, container(), byteArrayOf(1), userId = 0) { _, _ -> }

        now = 9_999L
        val completion = registry.complete(
            TARGET_PACKAGE,
            MESSAGE_ID,
            RemoteNotificationContent(isShowNotification = false),
            userId = 0,
        )

        assertNotNull(completion)
        assertFalse(completion!!.content!!.isShowNotification)
        assertNull(registry.timeout(TARGET_PACKAGE, MESSAGE_ID, userId = 0))
        assertNull(registry.complete(TARGET_PACKAGE, MESSAGE_ID, RemoteNotificationContent(), userId = 0))
    }

    @Test
    fun `null and late callbacks leave original notification for timeout fallback`() {
        var now = 0L
        val registry = ExtensionPendingRegistry(elapsedRealtime = { now })
        registry.register(TARGET_PACKAGE, MESSAGE_ID, container(), byteArrayOf(1, 2), userId = 0) { _, _ -> }

        assertNull(registry.complete(TARGET_PACKAGE, MESSAGE_ID, null, userId = 0))
        now = ExtensionNotificationContract.INITIAL_TIMEOUT_MS
        assertNull(registry.complete(TARGET_PACKAGE, MESSAGE_ID, RemoteNotificationContent(title = "late"), userId = 0))

        val fallback = registry.timeout(TARGET_PACKAGE, MESSAGE_ID, userId = 0)
        assertNotNull(fallback)
        assertNull(fallback!!.content)
        assertTrue(fallback.entry.payload.contentEquals(byteArrayOf(1, 2)))
    }

    @Test
    fun `same message id remains isolated between packages and users`() {
        val registry = ExtensionPendingRegistry(elapsedRealtime = { 100L })
        registry.register("com.example.one", MESSAGE_ID, container(), byteArrayOf(1), userId = 0) { _, _ -> }
        registry.register("com.example.two", MESSAGE_ID, container(), byteArrayOf(2), userId = 0) { _, _ -> }
        registry.register(TARGET_PACKAGE, MESSAGE_ID, container(), byteArrayOf(3), userId = 999) { _, _ -> }

        assertEquals(1, registry.complete("com.example.one", MESSAGE_ID, RemoteNotificationContent(), 0)!!.entry.payload[0])
        assertEquals(2, registry.timeout("com.example.two", MESSAGE_ID, 0)!!.entry.payload[0])
        assertEquals(3, registry.timeout(TARGET_PACKAGE, MESSAGE_ID, 999)!!.entry.payload[0])
    }

    private fun container(): XmPushActionContainer = XmPushActionContainer().apply {
        target = Target().apply {
            channelId = 5L
            userId = "user"
        }
        action = ActionType.SendMessage
        isRequest = false
        packageName = TARGET_PACKAGE
        appid = "app-id"
        setEncryptAction(false)
        setPushAction(byteArrayOf(1, 2, 3))
        metaInfo = PushMetaInfo().apply {
            id = MESSAGE_ID
            title = "old title"
            description = "old body"
            notifyId = 7
            extra = mutableMapOf(ExtensionNotificationContract.HYPER_TYPE to "1")
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.extension"
        const val MESSAGE_ID = "message-id"
    }
}
