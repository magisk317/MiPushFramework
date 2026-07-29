package io.github.magisk317.mipush.service.runtime

import android.app.Application
import android.graphics.Bitmap
import com.xiaomi.mipush.sdk.aidl.RemoteNotificationContent
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.Target
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.platform.support.XMPushUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ExtensionNotificationContractTest {
    @Test
    fun `hyper os eligibility matches stock 3 point 1 boundary`() {
        assertFalse(ExtensionNotificationContract.isSupportedHyperOs("2", "OS2.0.999", "3.9"))
        assertFalse(ExtensionNotificationContract.isSupportedHyperOs("3", "OS3.0.099", "3.9"))
        assertTrue(ExtensionNotificationContract.isSupportedHyperOs("3", "OS3.0.100", null))
        assertTrue(ExtensionNotificationContract.isSupportedHyperOs("3", "malformed", "3.1"))
        assertFalse(ExtensionNotificationContract.isSupportedHyperOs("3", "malformed", "3.0"))
        assertTrue(ExtensionNotificationContract.isSupportedHyperOs("4", null, null))
    }

    @Test
    fun `only the stock extension process name is eligible`() {
        assertTrue(
            ExtensionNotificationContract.hasExpectedProcess(
                TARGET_PACKAGE,
                "$TARGET_PACKAGE:pushExtensionService",
            ),
        )
        assertFalse(ExtensionNotificationContract.hasExpectedProcess(TARGET_PACKAGE, TARGET_PACKAGE))
        assertFalse(ExtensionNotificationContract.hasExpectedProcess(TARGET_PACKAGE, "$TARGET_PACKAGE:push"))
    }

    @Test
    fun `remote info maps stock extension keys and image fallback`() {
        val container = container().apply {
            metaInfo.extra["_target_name"] = "token"
            metaInfo.extra["notification_large_icon_uri"] = ""
            metaInfo.extra["notification_bigPic_uri"] = "content://large"
            metaInfo.extra["hyper_crypt"] = "opaque"
            metaInfo.extra["hyper_click_type"] = "2"
            metaInfo.extra["web_uri"] = "https://example.test/path"
        }

        val info = ExtensionNotificationContract.createRemoteInfo(container)

        assertEquals(0, info.type)
        assertEquals("token", info.token)
        assertEquals("old title", info.title)
        assertEquals("old body", info.body)
        assertEquals("content://large", info.image)
        assertEquals(7L, info.notifyId)
        assertEquals(2, info.clickType)
        assertEquals("https://example.test/path", info.clickUrl)
        assertEquals("opaque", info.extraData)
        assertEquals(MESSAGE_ID, info.msgId)
    }

    @Test
    fun `callback mutations preserve stock payload and temporary icon ordering`() {
        val original = container()
        val originalPayload = XMPushUtils.packToBytes(original)
        val icon = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)

        val applied = ExtensionNotificationContract.applyContent(
            container = original,
            originalPayload = originalPayload,
            content = RemoteNotificationContent(
                title = "new title",
                body = "new body",
                image = icon,
                badgeOperateType = 2,
                badgeNum = 8,
                clickType = 1,
                clickUrl = "intent:#Intent;action=test;end",
            ),
        )

        assertEquals("old title", original.metaInfo.title)
        assertEquals("new title", applied.container.metaInfo.title)
        assertEquals("new body", applied.container.metaInfo.description)
        assertEquals("8", applied.container.metaInfo.extra["message_count"])
        assertEquals("2", applied.container.metaInfo.extra["notify_effect"])
        assertEquals("intent:#Intent;action=test;end", applied.container.metaInfo.extra["intent_uri"])
        assertNotNull(applied.container.metaInfo.extra[ExtensionNotificationContract.TEMP_LARGE_ICON])

        val clickContainer = XMPushUtils.packToContainer(applied.payload)
        assertNotNull(clickContainer)
        assertEquals("new title", clickContainer!!.metaInfo.title)
        assertNull(clickContainer.metaInfo.extra[ExtensionNotificationContract.TEMP_LARGE_ICON])

        val decoded = ExtensionNotificationContract.decodeTemporaryLargeIcon(applied.container.metaInfo)
        assertNotNull(decoded)
        assertEquals(32, decoded!!.width)
        assertEquals(24, decoded.height)
    }

    @Test
    fun `oversized callback image is ignored at the stock pixel limit`() {
        val original = container()
        val applied = ExtensionNotificationContract.applyContent(
            original,
            XMPushUtils.packToBytes(original),
            RemoteNotificationContent(
                image = Bitmap.createBitmap(256, 192, Bitmap.Config.ARGB_8888),
            ),
        )

        assertNull(applied.container.metaInfo.extra[ExtensionNotificationContract.TEMP_LARGE_ICON])
    }

    @Test
    fun `suppression callback wins once and consumes pending fallback`() {
        var now = 100L
        val registry = ExtensionPendingRegistry(elapsedRealtime = { now })
        registry.register(MESSAGE_ID, TARGET_PACKAGE, container(), byteArrayOf(1)) { _, _ -> }

        now = 9_999L
        val completion = registry.complete(
            MESSAGE_ID,
            RemoteNotificationContent(isShowNotification = false),
        )

        assertNotNull(completion)
        assertFalse(completion!!.content!!.isShowNotification)
        assertNull(registry.timeout(MESSAGE_ID))
        assertNull(registry.complete(MESSAGE_ID, RemoteNotificationContent()))
    }

    @Test
    fun `null and late callbacks leave the original notification for timeout fallback`() {
        var now = 0L
        val registry = ExtensionPendingRegistry(elapsedRealtime = { now })
        registry.register(MESSAGE_ID, TARGET_PACKAGE, container(), byteArrayOf(1, 2)) { _, _ -> }

        assertNull(registry.complete(MESSAGE_ID, null))
        now = ExtensionNotificationContract.INITIAL_TIMEOUT_MS
        assertNull(registry.complete(MESSAGE_ID, RemoteNotificationContent(title = "late")))

        val fallback = registry.timeout(MESSAGE_ID)
        assertNotNull(fallback)
        assertNull(fallback!!.content)
        assertTrue(fallback.entry.payload.contentEquals(byteArrayOf(1, 2)))
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
