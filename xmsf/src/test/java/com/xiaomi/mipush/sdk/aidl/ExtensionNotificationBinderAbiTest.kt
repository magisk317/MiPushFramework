package com.xiaomi.mipush.sdk.aidl

import android.app.Application
import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ExtensionNotificationBinderAbiTest {
    @Test
    fun `remote info parcel order matches stock 7 point 4 point 67`() {
        val original = RemoteNotificationInfo(
            type = 3,
            token = "token",
            title = "title",
            body = "body",
            image = "image",
            notifyId = 42L,
            clickType = 2,
            clickUrl = "url",
            extraData = "extra",
            msgId = "message",
        )

        val restored = roundTrip(original, RemoteNotificationInfo.CREATOR)

        assertEquals(3, restored.type)
        assertEquals("token", restored.token)
        assertEquals("title", restored.title)
        assertEquals("body", restored.body)
        assertEquals("image", restored.image)
        assertEquals(42L, restored.notifyId)
        assertEquals(2, restored.clickType)
        assertEquals("url", restored.clickUrl)
        assertEquals("extra", restored.extraData)
        assertEquals("message", restored.msgId)
    }

    @Test
    fun `remote content parcel order preserves bitmap and stock defaults`() {
        val defaults = RemoteNotificationContent()
        assertTrue(defaults.isShowNotification)
        assertEquals(-1, defaults.badgeOperateType)
        assertEquals(-1, defaults.badgeNum)
        assertEquals(-1, defaults.clickType)

        val original = RemoteNotificationContent(
            isShowNotification = false,
            title = "title",
            body = "body",
            image = Bitmap.createBitmap(7, 9, Bitmap.Config.ARGB_8888),
            badgeOperateType = 2,
            badgeNum = 6,
            clickType = 1,
            clickUrl = "intent",
        )

        val restored = roundTrip(original, RemoteNotificationContent.CREATOR)

        assertFalse(restored.isShowNotification)
        assertEquals("title", restored.title)
        assertEquals("body", restored.body)
        assertEquals(7, restored.image?.width)
        assertEquals(9, restored.image?.height)
        assertEquals(2, restored.badgeOperateType)
        assertEquals(6, restored.badgeNum)
        assertEquals(1, restored.clickType)
        assertEquals("intent", restored.clickUrl)
    }

    @Test
    fun `extension requests use stock transactions and one way flags`() {
        val remote = RecordingBinder()
        val proxy = requireNotNull(IExtensionInterface.Stub.asInterface(remote))

        proxy.baseReceiveRemoteNotification(RemoteNotificationInfo(), null)
        assertEquals(1, remote.lastCode)
        assertEquals(IBinder.FLAG_ONEWAY, remote.lastFlags)
        assertNull(remote.lastReply)

        proxy.baseExtensionTimeWillExpire(RemoteNotificationInfo(), null)
        assertEquals(2, remote.lastCode)
        assertEquals(IBinder.FLAG_ONEWAY, remote.lastFlags)
        assertNull(remote.lastReply)
    }

    @Test
    fun `extension callback uses synchronous stock transaction`() {
        val remote = RecordingBinder()
        val proxy = requireNotNull(IExtensionCallback.Stub.asInterface(remote))

        proxy.onFinish(RemoteNotificationContent())

        assertEquals(1, remote.lastCode)
        assertEquals(0, remote.lastFlags)
        assertTrue(remote.lastReply != null)
    }

    private fun <T : android.os.Parcelable> roundTrip(
        value: T,
        creator: android.os.Parcelable.Creator<T>,
    ): T {
        val parcel = Parcel.obtain()
        return try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private class RecordingBinder : Binder() {
        var lastCode: Int = -1
        var lastFlags: Int = -1
        var lastReply: Parcel? = null

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            lastCode = code
            lastFlags = flags
            lastReply = reply
            reply?.writeNoException()
            return true
        }
    }
}
