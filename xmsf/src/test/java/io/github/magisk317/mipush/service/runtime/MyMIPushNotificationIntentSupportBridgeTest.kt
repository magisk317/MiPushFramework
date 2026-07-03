package io.github.magisk317.mipush.service.runtime

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Tests for the BridgeActivity click route added to fix the Samsung/AOSP
 * "Background start not allowed" failure.
 *
 * When the target app exposes its own `com.xiaomi.mipush.sdk.BridgeActivity`, the click
 * PendingIntent must launch that Activity (a foreground user action, allowed by AMS) carrying the
 * target-pointed PushMessageHandler intent as [PushConstants.MIPUSH_EXTRA_INTENT_PAYLOAD], instead
 * of XMSF starting the target's `PushMessageHandler` from the background.
 *
 * When the target app has no BridgeActivity, behavior must fall back to the legacy XMSF service
 * path (covered by MyMIPushNotificationIntentSupportPreservationTest).
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class MyMIPushNotificationIntentSupportBridgeTest {

    private val bridgeClass = "com.xiaomi.mipush.sdk.BridgeActivity"

    @Test
    fun `non-business message routes through target BridgeActivity when available`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"
        shadowOf(context.packageManager)
            .addActivityIfNotPresent(ComponentName(targetPackage, bridgeClass))

        val container = XmPushActionContainer().apply {
            packageName = targetPackage
            metaInfo = PushMetaInfo().apply {
                setId("x1234567890123456789ab") // not a business message
                setNotifyId(6)
                extra = mutableMapOf("title" to "Hello")
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(1, 2, 3),
            notificationId = 42,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isActivity, "Should produce an Activity PendingIntent for BridgeActivity")
        assertFalse(shadow.isService, "Should NOT produce a Service PendingIntent when BridgeActivity is available")
        assertEquals(
            ComponentName(targetPackage, bridgeClass),
            shadow.savedIntent.component
        )

        val payloadIntent = shadow.savedIntent.parcelableExtraCompat<Intent>(
            PushConstants.MIPUSH_EXTRA_INTENT_PAYLOAD
        )
        assertNotNull(payloadIntent, "BridgeActivity intent must carry the target PushMessageHandler payload")
        assertEquals(
            ComponentName(targetPackage, "com.xiaomi.mipush.sdk.PushMessageHandler"),
            payloadIntent!!.component
        )
        assertEquals(
            PushConstants.MIPUSH_ACTION_NEW_MESSAGE,
            payloadIntent.action,
            "Non-business payload intent should use MIPUSH_ACTION_NEW_MESSAGE"
        )
    }

    @Test
    fun `business message routes through xmsf PushMessageHandler payload via BridgeActivity`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.biz"
        shadowOf(context.packageManager)
            .addActivityIfNotPresent(ComponentName(targetPackage, bridgeClass))

        val container = XmPushActionContainer().apply {
            packageName = targetPackage
            metaInfo = PushMetaInfo().apply {
                setId("s1234567890123456789ab") // business message: valid id + ignoreRegInfo
                setNotifyId(5)
                setIgnoreRegInfo(true)
                extra = mutableMapOf("some_key" to "some_value")
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(4, 5, 6),
            notificationId = 43,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isActivity)
        assertEquals(ComponentName(targetPackage, bridgeClass), shadow.savedIntent.component)

        val payloadIntent = shadow.savedIntent.parcelableExtraCompat<Intent>(
            PushConstants.MIPUSH_EXTRA_INTENT_PAYLOAD
        )
        assertNotNull(payloadIntent)
        // Business messages are handled by XMSF's own PushMessageHandler, mirroring stock.
        assertEquals(
            ComponentName(PushConstants.PUSH_SERVICE_PACKAGE_NAME, "com.xiaomi.mipush.sdk.PushMessageHandler"),
            payloadIntent!!.component
        )
    }

    @Test
    fun `falls back to xmsf service when target has no BridgeActivity`() {
        val context = RuntimeEnvironment.getApplication()
        // Deliberately do NOT register a BridgeActivity for the target package.
        val container = XmPushActionContainer().apply {
            packageName = "com.example.nobridge"
            metaInfo = PushMetaInfo().apply {
                setId("x1234567890123456789ab")
                setNotifyId(6)
                extra = mutableMapOf("title" to "Hello")
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(7, 8, 9),
            notificationId = 44,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isService, "Without a BridgeActivity the legacy XMSF service path must be used")
        assertFalse(shadow.isActivity)
        assertEquals(
            ComponentName("com.xiaomi.xmsf", "com.xiaomi.push.sdk.MyPushMessageHandler"),
            shadow.savedIntent.component
        )
    }

    private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
        }
    }
}
