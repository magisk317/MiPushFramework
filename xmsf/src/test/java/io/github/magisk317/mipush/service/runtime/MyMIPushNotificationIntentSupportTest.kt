package io.github.magisk317.mipush.service.runtime

import android.content.ComponentName
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class MyMIPushNotificationIntentSupportTest {

    @Test
    fun `style type 6 uses stock custom button keys`() {
        val extra = mapOf(
            "notification_style_type" to "6",
            "cust_btn_2_n" to "Answer"
        )

        val keys = MyMIPushNotificationIntentSupport.styleActionKeys(2, extra)

        assertEquals("cust_btn_2_n", keys.name)
        assertEquals("cust_btn_2_ne", keys.notifyEffect)
        assertEquals("cust_btn_2_iu", keys.intentUri)
        assertEquals("cust_btn_2_ic", keys.intentClass)
        assertEquals("cust_btn_2_wu", keys.webUri)
        assertEquals("Answer", MyMIPushNotificationIntentSupport.getStyleActionTitle(2, extra))
    }

    @Test
    fun `non voip style keeps legacy button keys`() {
        val extra = mapOf("notification_style_button_right_name" to "Open")

        val keys = MyMIPushNotificationIntentSupport.styleActionKeys(3, extra)

        assertEquals("notification_style_button_right_name", keys.name)
        assertEquals("notification_style_button_right_notify_effect", keys.notifyEffect)
        assertEquals("notification_style_button_right_intent_uri", keys.intentUri)
        assertEquals("notification_style_button_right_intent_class", keys.intentClass)
        assertEquals("notification_style_button_right_web_uri", keys.webUri)
        assertEquals("Open", MyMIPushNotificationIntentSupport.getStyleActionTitle(3, extra))
    }

    @Test
    fun `notification click uses sdk activity whenever sdk intent is available`() {
        assertEquals(true, MyMIPushNotificationIntentSupport.shouldUseSdkActivityClick(sdkIntentAvailable = true))
        assertEquals(false, MyMIPushNotificationIntentSupport.shouldUseSdkActivityClick(sdkIntentAvailable = false))
    }

    @Test
    fun `clicked notification prefers sdk activity over service callback when sdk intent resolves`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.target"
        val targetClass = "com.example.target.ChatActivity"
        val targetComponent = ComponentName(targetPackage, targetClass)
        val container = XmPushActionContainer().apply {
            packageName = targetPackage
            metaInfo = PushMetaInfo().apply {
                setId("message-id")
                setNotifyId(7)
                extra = mutableMapOf(
                    PushConstants.EXTRA_PARAM_NOTIFY_EFFECT to PushConstants.NOTIFICATION_CLICK_INTENT,
                    PushConstants.EXTRA_PARAM_CLASS_NAME to targetClass,
                )
            }
        }

        shadowOf(context.packageManager).addActivityIfNotPresent(targetComponent)

        val pendingIntent = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(1, 2, 3),
            notificationId = 42,
            extra = null,
        )

        assertNotNull(pendingIntent)
        val shadow = shadowOf(pendingIntent!!)
        assertTrue(shadow.isActivity)
        assertFalse(shadow.isService)
        assertEquals(targetComponent, shadow.savedIntent.component)
    }
}
