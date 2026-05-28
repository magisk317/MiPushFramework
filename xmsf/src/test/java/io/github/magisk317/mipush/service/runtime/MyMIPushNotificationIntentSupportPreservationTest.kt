package io.github.magisk317.mipush.service.runtime

import android.content.ComponentName
import android.content.Intent
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Preservation property tests for MyMIPushNotificationIntentSupport.
 *
 * These tests capture the EXISTING correct behavior of the unfixed code that must
 * remain unchanged after the bugfix is applied. They are written BEFORE the fix
 * and must PASS on unfixed code.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class MyMIPushNotificationIntentSupportPreservationTest {

    // =========================================================================
    // Property: shouldUseSdkActivityClick(false) always returns false
    // Validates: Requirements 3.4 (no SDK intent means Service path is used)
    // =========================================================================

    @Test
    fun `shouldUseSdkActivityClick with false always returns false`() {
        // Observation: when no SDK intent is available, shouldUseSdkActivityClick(false) = false
        // This means the Service path is taken for notifications without notify_effect
        val result = MyMIPushNotificationIntentSupport.shouldUseSdkActivityClick(sdkIntentAvailable = false)
        assertFalse(result)
    }

    // =========================================================================
    // Property: Null metaInfo returns null
    // Validates: Requirements 3.4
    // =========================================================================

    @Test
    fun `buildClickedPendingIntent with null metaInfo returns null`() {
        val context = RuntimeEnvironment.getApplication()
        val container = XmPushActionContainer().apply {
            packageName = "com.example.app"
            metaInfo = null
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(1, 2, 3),
            notificationId = 1,
            extra = null
        )

        assertNull(result)
    }

    // =========================================================================
    // Property: URL in metaInfo.url returns Activity PendingIntent with ACTION_VIEW
    // Validates: Requirements 3.1
    // =========================================================================

    @Test
    fun `buildClickedPendingIntent with metaInfo url returns Activity PendingIntent with ACTION_VIEW`() {
        val context = RuntimeEnvironment.getApplication()
        val testUrl = "https://www.example.com/page"
        val container = XmPushActionContainer().apply {
            packageName = "com.example.app"
            metaInfo = PushMetaInfo().apply {
                setId("s1234567890123456789ab")
                setNotifyId(1)
                url = testUrl
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(1, 2, 3),
            notificationId = 10,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isActivity, "URL notification should produce Activity PendingIntent")
        assertFalse(shadow.isService, "URL notification should NOT produce Service PendingIntent")
        assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action)
        assertEquals(testUrl, shadow.savedIntent.data.toString())
    }

    @Test
    fun `buildClickedPendingIntent with http url returns Activity PendingIntent with ACTION_VIEW`() {
        val context = RuntimeEnvironment.getApplication()
        val testUrl = "http://example.org/path?q=1"
        val container = XmPushActionContainer().apply {
            packageName = "com.example.app"
            metaInfo = PushMetaInfo().apply {
                setId("s1234567890123456789ab")
                setNotifyId(2)
                url = testUrl
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(4, 5, 6),
            notificationId = 11,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isActivity, "HTTP URL notification should produce Activity PendingIntent")
        assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action)
        assertEquals(testUrl, shadow.savedIntent.data.toString())
    }

    // =========================================================================
    // Property: web_uri extra returns Activity PendingIntent with ACTION_VIEW
    // Validates: Requirements 3.1
    // =========================================================================

    @Test
    fun `buildClickedPendingIntent with web_uri extra returns Activity PendingIntent with ACTION_VIEW`() {
        val context = RuntimeEnvironment.getApplication()
        val webUri = "https://m.example.com/deep/link"
        val container = XmPushActionContainer().apply {
            packageName = "com.example.app"
            metaInfo = PushMetaInfo().apply {
                setId("s1234567890123456789ab")
                setNotifyId(3)
                extra = mutableMapOf(
                    PushConstants.EXTRA_PARAM_WEB_URI to webUri
                )
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(7, 8, 9),
            notificationId = 12,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isActivity, "web_uri notification should produce Activity PendingIntent")
        assertFalse(shadow.isService, "web_uri notification should NOT produce Service PendingIntent")
        assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action)
        assertEquals(webUri, shadow.savedIntent.data.toString())
    }

    @Test
    fun `buildClickedPendingIntent with web_uri extra without scheme uses raw value and returns Activity PendingIntent`() {
        val context = RuntimeEnvironment.getApplication()
        val webUri = "www.example.com/page"
        val container = XmPushActionContainer().apply {
            packageName = "com.example.app"
            metaInfo = PushMetaInfo().apply {
                setId("s1234567890123456789ab")
                setNotifyId(4)
                extra = mutableMapOf(
                    PushConstants.EXTRA_PARAM_WEB_URI to webUri
                )
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(10, 11, 12),
            notificationId = 13,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isActivity, "web_uri without scheme should produce Activity PendingIntent")
        assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action)
        // resolveClickedUrl returns raw web_uri value without normalization
        assertEquals(webUri, shadow.savedIntent.data.toString())
    }

    // =========================================================================
    // Property: Business message without URL and without notify_effect routes
    // through com.xiaomi.mipush.sdk.PushMessageHandler via Service PendingIntent
    // Validates: Requirements 3.3
    // =========================================================================

    @Test
    fun `buildClickedPendingIntent for business message without URL routes through PushMessageHandler service`() {
        val context = RuntimeEnvironment.getApplication()
        // Business message: isIdVaild(metaInfo) && metaInfo.isIgnoreRegInfo
        // isIdVaild: id.length == 22 && MESSAGE_TYPE_INDEX.indexOf(id[0]) >= 0
        // MESSAGE_TYPE_INDEX = "satuigmo"
        val container = XmPushActionContainer().apply {
            packageName = "com.example.app"
            metaInfo = PushMetaInfo().apply {
                setId("s1234567890123456789ab") // 22 chars, starts with 's' (in MESSAGE_TYPE_INDEX)
                setNotifyId(5)
                setIgnoreRegInfo(true) // makes it a business message
                // No URL, no notify_effect in extra
                extra = mutableMapOf("some_key" to "some_value")
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(13, 14, 15),
            notificationId = 14,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isService, "Business message should produce Service PendingIntent")
        assertFalse(shadow.isActivity, "Business message should NOT produce Activity PendingIntent")
        assertEquals(
            ComponentName("com.xiaomi.xmsf", "com.xiaomi.mipush.sdk.PushMessageHandler"),
            shadow.savedIntent.component
        )
    }

    // =========================================================================
    // Property: Non-business message without URL and without notify_effect routes
    // through com.xiaomi.push.sdk.MyPushMessageHandler via Service PendingIntent
    // Validates: Requirements 3.4
    // =========================================================================

    @Test
    fun `buildClickedPendingIntent for non-business message without URL and without notify_effect routes through MyPushMessageHandler service`() {
        val context = RuntimeEnvironment.getApplication()
        // Non-business message: either id is not valid or ignoreRegInfo is false
        val container = XmPushActionContainer().apply {
            packageName = "com.example.app"
            metaInfo = PushMetaInfo().apply {
                setId("x1234567890123456789ab") // 22 chars, starts with 'x' (NOT in MESSAGE_TYPE_INDEX "satuigmo")
                setNotifyId(6)
                // No URL, no notify_effect in extra
                extra = mutableMapOf("title" to "Hello")
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(16, 17, 18),
            notificationId = 15,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isService, "Non-business message without notify_effect should produce Service PendingIntent")
        assertFalse(shadow.isActivity, "Non-business message without notify_effect should NOT produce Activity PendingIntent")
        assertEquals(
            ComponentName("com.xiaomi.xmsf", "com.xiaomi.push.sdk.MyPushMessageHandler"),
            shadow.savedIntent.component
        )
    }

    @Test
    fun `buildClickedPendingIntent for non-business message with ignoreRegInfo false routes through MyPushMessageHandler`() {
        val context = RuntimeEnvironment.getApplication()
        // Valid ID but ignoreRegInfo = false → not a business message
        val container = XmPushActionContainer().apply {
            packageName = "com.example.target"
            metaInfo = PushMetaInfo().apply {
                setId("s1234567890123456789ab") // valid ID format
                setNotifyId(7)
                setIgnoreRegInfo(false) // not a business message
                // No URL, no notify_effect
                extra = mutableMapOf("key" to "value")
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(19, 20, 21),
            notificationId = 16,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isService, "Non-business message (ignoreRegInfo=false) should produce Service PendingIntent")
        assertFalse(shadow.isActivity)
        assertEquals(
            ComponentName("com.xiaomi.xmsf", "com.xiaomi.push.sdk.MyPushMessageHandler"),
            shadow.savedIntent.component
        )
    }

    // =========================================================================
    // Property: Containers with null extra (no notify_effect possible) use Service path
    // Validates: Requirements 3.4
    // =========================================================================

    @Test
    fun `buildClickedPendingIntent with null extra and no URL uses Service path`() {
        val context = RuntimeEnvironment.getApplication()
        val container = XmPushActionContainer().apply {
            packageName = "com.example.app"
            metaInfo = PushMetaInfo().apply {
                setId("x1234567890123456789ab")
                setNotifyId(8)
                // extra is null by default, no URL
            }
        }

        val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(22, 23, 24),
            notificationId = 17,
            extra = null
        )

        assertNotNull(result)
        val shadow = shadowOf(result!!)
        assertTrue(shadow.isService, "Null extra should produce Service PendingIntent (no SDK intent possible)")
        assertFalse(shadow.isActivity)
        assertEquals(
            ComponentName("com.xiaomi.xmsf", "com.xiaomi.push.sdk.MyPushMessageHandler"),
            shadow.savedIntent.component
        )
    }

    // =========================================================================
    // Property: Multiple URL variations all produce Activity PendingIntent
    // (property-based style: testing across multiple inputs)
    // Validates: Requirements 3.1
    // =========================================================================

    @Test
    fun `all containers with various URLs produce Activity PendingIntent with ACTION_VIEW`() {
        val context = RuntimeEnvironment.getApplication()
        val urls = listOf(
            "https://www.taobao.com/item/123",
            "http://m.jd.com/product/456",
            "https://deep.link.example.com/path/to/resource?param=value",
            "https://example.org"
        )

        urls.forEachIndexed { index, url ->
            val container = XmPushActionContainer().apply {
                packageName = "com.example.app"
                metaInfo = PushMetaInfo().apply {
                    setId("s1234567890123456789ab")
                    setNotifyId(index + 100)
                    this.url = url
                }
            }

            val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
                context = context,
                container = container,
                decryptedContent = byteArrayOf(1),
                notificationId = index + 100,
                extra = null
            )

            assertNotNull(result, "URL '$url' should produce non-null PendingIntent")
            val shadow = shadowOf(result!!)
            assertTrue(shadow.isActivity, "URL '$url' should produce Activity PendingIntent")
            assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action, "URL '$url' should have ACTION_VIEW")
            assertEquals(url, shadow.savedIntent.data.toString(), "URL '$url' should be preserved in intent data")
        }
    }

    @Test
    fun `all containers with various web_uri extras produce Activity PendingIntent with ACTION_VIEW`() {
        val context = RuntimeEnvironment.getApplication()
        val webUris = listOf(
            "https://example.com/promo",
            "http://shop.example.com/sale",
            "https://news.example.org/article/789"
        )

        webUris.forEachIndexed { index, uri ->
            val container = XmPushActionContainer().apply {
                packageName = "com.example.app"
                metaInfo = PushMetaInfo().apply {
                    setId("s1234567890123456789ab")
                    setNotifyId(index + 200)
                    extra = mutableMapOf(
                        PushConstants.EXTRA_PARAM_WEB_URI to uri
                    )
                }
            }

            val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
                context = context,
                container = container,
                decryptedContent = byteArrayOf(2),
                notificationId = index + 200,
                extra = null
            )

            assertNotNull(result, "web_uri '$uri' should produce non-null PendingIntent")
            val shadow = shadowOf(result!!)
            assertTrue(shadow.isActivity, "web_uri '$uri' should produce Activity PendingIntent")
            assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action, "web_uri '$uri' should have ACTION_VIEW")
            assertEquals(uri, shadow.savedIntent.data.toString(), "web_uri '$uri' should be preserved in intent data")
        }
    }

    // =========================================================================
    // Property: Multiple non-URL, non-notify_effect containers all use Service path
    // (property-based style: testing across multiple inputs)
    // Validates: Requirements 3.4
    // =========================================================================

    @Test
    fun `all containers without URL and without notify_effect produce Service PendingIntent`() {
        val context = RuntimeEnvironment.getApplication()
        // Various configurations that should all result in Service PendingIntent
        val containers = listOf(
            // Non-business, no extra
            XmPushActionContainer().apply {
                packageName = "com.app.one"
                metaInfo = PushMetaInfo().apply {
                    setId("x1234567890123456789ab")
                    setNotifyId(1)
                }
            },
            // Non-business, extra without notify_effect
            XmPushActionContainer().apply {
                packageName = "com.app.two"
                metaInfo = PushMetaInfo().apply {
                    setId("x9876543210987654321ab")
                    setNotifyId(2)
                    extra = mutableMapOf("custom_key" to "custom_value", "another" to "data")
                }
            },
            // Non-business, empty extra map
            XmPushActionContainer().apply {
                packageName = "com.app.three"
                metaInfo = PushMetaInfo().apply {
                    setId("x0000000000000000000ab")
                    setNotifyId(3)
                    extra = mutableMapOf()
                }
            }
        )

        containers.forEachIndexed { index, container ->
            val result = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
                context = context,
                container = container,
                decryptedContent = byteArrayOf(index.toByte()),
                notificationId = index + 300,
                extra = null
            )

            assertNotNull(result, "Container $index should produce non-null PendingIntent")
            val shadow = shadowOf(result!!)
            assertTrue(shadow.isService, "Container $index without URL/notify_effect should produce Service PendingIntent")
            assertFalse(shadow.isActivity, "Container $index without URL/notify_effect should NOT produce Activity PendingIntent")
            assertEquals(
                ComponentName("com.xiaomi.xmsf", "com.xiaomi.push.sdk.MyPushMessageHandler"),
                shadow.savedIntent.component,
                "Container $index should target MyPushMessageHandler"
            )
        }
    }
}
