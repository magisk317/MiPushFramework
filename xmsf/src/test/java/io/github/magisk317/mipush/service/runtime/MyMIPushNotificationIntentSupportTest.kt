package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
