package io.github.magisk317.mipush.service.runtime

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.xiaomi.push.service.PushConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.lang.reflect.Method

/**
 * Preservation property tests for Style Action buttons.
 *
 * These tests capture the EXISTING correct behavior of the unfixed code that must
 * remain unchanged after the style action bugfix is applied. They are written BEFORE
 * the fix and must PASS on unfixed code.
 *
 * **Validates: Requirements 4.2**
 *
 * Property 2: Preservation - Style Action Web Page Buttons Continue Using Activity PendingIntent
 *
 * Observed behavior on UNFIXED code:
 * - Style button with notify_effect = WEB_PAGE and valid web_uri → Activity PendingIntent with ACTION_VIEW
 * - Style button with no notify_effect (empty/null) → null (no PendingIntent created)
 * - Style button with notify_effect = WEB_PAGE and invalid/empty web_uri → null
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
@DisplayName("Preservation: Style Action Web Page Buttons Continue Using Activity PendingIntent")
class StyleActionPreservationTest {

    /**
     * Uses reflection to invoke the private `getStylePendingIntent` method.
     */
    private fun invokeGetStylePendingIntent(
        context: Context,
        pkgName: String,
        place: Int,
        metaExtra: Map<String, String>?
    ): PendingIntent? {
        val method: Method = MyMIPushNotificationIntentSupport::class.java.getDeclaredMethod(
            "getStylePendingIntent",
            Context::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Map::class.java
        )
        method.isAccessible = true
        return method.invoke(MyMIPushNotificationIntentSupport, context, pkgName, place, metaExtra) as PendingIntent?
    }

    // =========================================================================
    // Property: Style button with notify_effect = WEB_PAGE and valid HTTP/HTTPS URI
    // returns Activity PendingIntent with ACTION_VIEW
    // Validates: Requirements 4.2
    // =========================================================================

    @Test
    @DisplayName("Style button with notify_effect=WEB_PAGE and valid HTTPS URI returns Activity PendingIntent with ACTION_VIEW")
    fun `getStylePendingIntent returns Activity PendingIntent with ACTION_VIEW for WEB_PAGE with valid https URI`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"
        val webUri = "https://www.example.com/promo"

        // Register an activity that can handle ACTION_VIEW for the URI
        val browserActivity = ComponentName("com.android.browser", "com.android.browser.BrowserActivity")
        shadowOf(context.packageManager).addActivityIfNotPresent(browserActivity)
        shadowOf(context.packageManager).addIntentFilterForActivity(
            browserActivity,
            IntentFilter().apply {
                addAction(Intent.ACTION_VIEW)
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            }
        )

        // Style button left with notify_effect = "3" (WEB_PAGE) and valid web_uri
        val metaExtra = mapOf(
            "notification_style_button_left_name" to "View Promo",
            "notification_style_button_left_notify_effect" to PushConstants.NOTIFICATION_CLICK_WEB_PAGE,
            "notification_style_button_left_web_uri" to webUri
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 1, metaExtra)

        assertNotNull(pendingIntent,
            "getStylePendingIntent() should return non-null PendingIntent for WEB_PAGE with valid HTTPS URI")

        val shadow = shadowOf(pendingIntent!!)
        assertTrue(shadow.isActivity,
            "WEB_PAGE style button should produce Activity PendingIntent (browser intents don't cause cold-start white screen)")
        assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action,
            "WEB_PAGE style button should have ACTION_VIEW intent action")
        assertEquals(webUri, shadow.savedIntent.data.toString(),
            "WEB_PAGE style button should preserve the web URI in intent data")
    }

    @Test
    @DisplayName("Style button with notify_effect=WEB_PAGE and valid HTTP URI returns Activity PendingIntent with ACTION_VIEW")
    fun `getStylePendingIntent returns Activity PendingIntent with ACTION_VIEW for WEB_PAGE with valid http URI`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"
        val webUri = "http://m.example.com/page"

        // Register an activity that can handle ACTION_VIEW for the URI
        val browserActivity = ComponentName("com.android.browser", "com.android.browser.BrowserActivity")
        shadowOf(context.packageManager).addActivityIfNotPresent(browserActivity)
        shadowOf(context.packageManager).addIntentFilterForActivity(
            browserActivity,
            IntentFilter().apply {
                addAction(Intent.ACTION_VIEW)
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("http")
            }
        )

        // Style button mid with notify_effect = "3" (WEB_PAGE) and valid web_uri
        val metaExtra = mapOf(
            "notification_style_button_mid_name" to "Open Link",
            "notification_style_button_mid_notify_effect" to PushConstants.NOTIFICATION_CLICK_WEB_PAGE,
            "notification_style_button_mid_web_uri" to webUri
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 2, metaExtra)

        assertNotNull(pendingIntent,
            "getStylePendingIntent() should return non-null PendingIntent for WEB_PAGE with valid HTTP URI")

        val shadow = shadowOf(pendingIntent!!)
        assertTrue(shadow.isActivity,
            "WEB_PAGE style button with HTTP URI should produce Activity PendingIntent")
        assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action,
            "WEB_PAGE style button should have ACTION_VIEW intent action")
        assertEquals(webUri, shadow.savedIntent.data.toString(),
            "WEB_PAGE style button should preserve the HTTP URI in intent data")
    }

    @Test
    @DisplayName("Style button with notify_effect=WEB_PAGE and URI without scheme gets http:// prepended")
    fun `getStylePendingIntent normalizes URI without scheme for WEB_PAGE style button`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"
        val rawUri = "www.example.com/page"
        val normalizedUri = "http://$rawUri"

        // Register an activity that can handle ACTION_VIEW for the normalized URI
        val browserActivity = ComponentName("com.android.browser", "com.android.browser.BrowserActivity")
        shadowOf(context.packageManager).addActivityIfNotPresent(browserActivity)
        shadowOf(context.packageManager).addIntentFilterForActivity(
            browserActivity,
            IntentFilter().apply {
                addAction(Intent.ACTION_VIEW)
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("http")
            }
        )

        // Style button right with notify_effect = "3" (WEB_PAGE) and URI without scheme
        val metaExtra = mapOf(
            "notification_style_button_right_name" to "Visit Site",
            "notification_style_button_right_notify_effect" to PushConstants.NOTIFICATION_CLICK_WEB_PAGE,
            "notification_style_button_right_web_uri" to rawUri
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 3, metaExtra)

        assertNotNull(pendingIntent,
            "getStylePendingIntent() should return non-null PendingIntent for WEB_PAGE with normalizable URI")

        val shadow = shadowOf(pendingIntent!!)
        assertTrue(shadow.isActivity,
            "WEB_PAGE style button with normalized URI should produce Activity PendingIntent")
        assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action,
            "WEB_PAGE style button should have ACTION_VIEW intent action")
        assertEquals(normalizedUri, shadow.savedIntent.data.toString(),
            "WEB_PAGE style button should normalize URI by prepending http://")
    }

    // =========================================================================
    // Property: Style button with empty/null notify_effect returns null
    // Validates: Requirements 4.2
    // =========================================================================

    @Test
    @DisplayName("Style button with null metaExtra returns null")
    fun `getStylePendingIntent returns null when metaExtra is null`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 1, null)

        assertNull(pendingIntent,
            "getStylePendingIntent() should return null when metaExtra is null")
    }

    @Test
    @DisplayName("Style button with empty notify_effect returns null")
    fun `getStylePendingIntent returns null when notify_effect is empty string`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"

        // Style button left with empty notify_effect
        val metaExtra = mapOf(
            "notification_style_button_left_name" to "Some Button",
            "notification_style_button_left_notify_effect" to ""
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 1, metaExtra)

        assertNull(pendingIntent,
            "getStylePendingIntent() should return null when notify_effect is empty string")
    }

    @Test
    @DisplayName("Style button with no notify_effect key returns null")
    fun `getStylePendingIntent returns null when notify_effect key is absent`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"

        // Style button with name but no notify_effect key at all
        val metaExtra = mapOf(
            "notification_style_button_left_name" to "Some Button",
            "notification_style_button_left_web_uri" to "https://example.com"
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 1, metaExtra)

        assertNull(pendingIntent,
            "getStylePendingIntent() should return null when notify_effect key is absent from metaExtra")
    }

    @Test
    @DisplayName("Style button mid with no notify_effect key returns null")
    fun `getStylePendingIntent returns null for mid button when notify_effect key is absent`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"

        // Style button mid with name but no notify_effect key
        val metaExtra = mapOf(
            "notification_style_button_mid_name" to "Action",
            "notification_style_button_mid_web_uri" to "https://example.com"
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 2, metaExtra)

        assertNull(pendingIntent,
            "getStylePendingIntent() should return null for mid button when notify_effect key is absent")
    }

    // =========================================================================
    // Property: Style button with WEB_PAGE effect but invalid/empty web_uri returns null
    // Validates: Requirements 4.2
    // =========================================================================

    @Test
    @DisplayName("Style button with notify_effect=WEB_PAGE and empty web_uri returns null")
    fun `getStylePendingIntent returns null for WEB_PAGE with empty web_uri`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"

        // Style button with WEB_PAGE effect but empty web_uri
        val metaExtra = mapOf(
            "notification_style_button_left_name" to "Open Link",
            "notification_style_button_left_notify_effect" to PushConstants.NOTIFICATION_CLICK_WEB_PAGE,
            "notification_style_button_left_web_uri" to ""
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 1, metaExtra)

        assertNull(pendingIntent,
            "getStylePendingIntent() should return null for WEB_PAGE with empty web_uri (normalizeWebUri returns null)")
    }

    @Test
    @DisplayName("Style button with notify_effect=WEB_PAGE and no web_uri key returns null")
    fun `getStylePendingIntent returns null for WEB_PAGE with missing web_uri key`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"

        // Style button with WEB_PAGE effect but no web_uri key at all
        val metaExtra = mapOf(
            "notification_style_button_left_name" to "Open Link",
            "notification_style_button_left_notify_effect" to PushConstants.NOTIFICATION_CLICK_WEB_PAGE
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 1, metaExtra)

        assertNull(pendingIntent,
            "getStylePendingIntent() should return null for WEB_PAGE when web_uri key is missing")
    }

    @Test
    @DisplayName("Style button with notify_effect=WEB_PAGE and invalid URI (ftp scheme) returns null")
    fun `getStylePendingIntent returns null for WEB_PAGE with non-http URI scheme`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"

        // Style button with WEB_PAGE effect but invalid URI (ftp:// is not http/https)
        val metaExtra = mapOf(
            "notification_style_button_right_name" to "Download",
            "notification_style_button_right_notify_effect" to PushConstants.NOTIFICATION_CLICK_WEB_PAGE,
            "notification_style_button_right_web_uri" to "ftp://files.example.com/data.zip"
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 3, metaExtra)

        assertNull(pendingIntent,
            "getStylePendingIntent() should return null for WEB_PAGE with non-http/https URI (normalizeWebUri rejects ftp)")
    }

    // =========================================================================
    // Property-based style: Multiple valid web URIs all produce Activity PendingIntent
    // Validates: Requirements 4.2
    // =========================================================================

    @Test
    @DisplayName("All style buttons with WEB_PAGE and various valid HTTP/HTTPS URIs produce Activity PendingIntent with ACTION_VIEW")
    fun `all style buttons with WEB_PAGE and valid URIs produce Activity PendingIntent with ACTION_VIEW`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"

        // Register browser activity for both http and https
        val browserActivity = ComponentName("com.android.browser", "com.android.browser.BrowserActivity")
        shadowOf(context.packageManager).addActivityIfNotPresent(browserActivity)
        shadowOf(context.packageManager).addIntentFilterForActivity(
            browserActivity,
            IntentFilter().apply {
                addAction(Intent.ACTION_VIEW)
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            }
        )
        shadowOf(context.packageManager).addIntentFilterForActivity(
            browserActivity,
            IntentFilter().apply {
                addAction(Intent.ACTION_VIEW)
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("http")
            }
        )

        // Test various valid URIs across different button positions
        data class TestCase(val place: Int, val uri: String, val expectedUri: String)

        val testCases = listOf(
            TestCase(1, "https://shop.example.com/sale", "https://shop.example.com/sale"),
            TestCase(2, "https://news.example.org/article/123", "https://news.example.org/article/123"),
            TestCase(3, "http://m.example.com/promo?id=456", "http://m.example.com/promo?id=456"),
            TestCase(1, "https://deep.link.example.com/path/to/resource", "https://deep.link.example.com/path/to/resource")
        )

        val positionKeyPrefix = mapOf(
            1 to "notification_style_button_left",
            2 to "notification_style_button_mid",
            3 to "notification_style_button_right"
        )

        testCases.forEach { testCase ->
            val prefix = positionKeyPrefix[testCase.place]!!
            val metaExtra = mapOf(
                "${prefix}_name" to "Link",
                "${prefix}_notify_effect" to PushConstants.NOTIFICATION_CLICK_WEB_PAGE,
                "${prefix}_web_uri" to testCase.uri
            )

            val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, testCase.place, metaExtra)

            assertNotNull(pendingIntent,
                "Place ${testCase.place}, URI '${testCase.uri}': should return non-null PendingIntent")

            val shadow = shadowOf(pendingIntent!!)
            assertTrue(shadow.isActivity,
                "Place ${testCase.place}, URI '${testCase.uri}': WEB_PAGE should produce Activity PendingIntent")
            assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action,
                "Place ${testCase.place}, URI '${testCase.uri}': should have ACTION_VIEW")
            assertEquals(testCase.expectedUri, shadow.savedIntent.data.toString(),
                "Place ${testCase.place}, URI '${testCase.uri}': should preserve URI in intent data")
        }
    }

    // =========================================================================
    // Property-based style: Multiple empty/null notify_effect cases all return null
    // Validates: Requirements 4.2
    // =========================================================================

    @Test
    @DisplayName("All style buttons with empty/null notify_effect return null regardless of position")
    fun `all style buttons with empty or missing notify_effect return null`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.app"

        val positionKeyPrefix = mapOf(
            1 to "notification_style_button_left",
            2 to "notification_style_button_mid",
            3 to "notification_style_button_right"
        )

        // Test all positions with missing notify_effect
        for (place in 1..3) {
            val prefix = positionKeyPrefix[place]!!

            // Case 1: notify_effect key absent
            val metaExtraNoKey = mapOf(
                "${prefix}_name" to "Button $place"
            )
            val result1 = invokeGetStylePendingIntent(context, targetPackage, place, metaExtraNoKey)
            assertNull(result1,
                "Place $place: should return null when notify_effect key is absent")

            // Case 2: notify_effect is empty string
            val metaExtraEmpty = mapOf(
                "${prefix}_name" to "Button $place",
                "${prefix}_notify_effect" to ""
            )
            val result2 = invokeGetStylePendingIntent(context, targetPackage, place, metaExtraEmpty)
            assertNull(result2,
                "Place $place: should return null when notify_effect is empty string")
        }
    }

    // =========================================================================
    // VoIP style button preservation
    // Validates: Requirements 4.2
    // =========================================================================

    @Test
    @DisplayName("VoIP style button with notify_effect=WEB_PAGE and valid URI returns Activity PendingIntent")
    fun `VoIP style button with WEB_PAGE and valid URI returns Activity PendingIntent with ACTION_VIEW`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.voip"
        val webUri = "https://call.example.com/join/room123"

        // Register browser activity
        val browserActivity = ComponentName("com.android.browser", "com.android.browser.BrowserActivity")
        shadowOf(context.packageManager).addActivityIfNotPresent(browserActivity)
        shadowOf(context.packageManager).addIntentFilterForActivity(
            browserActivity,
            IntentFilter().apply {
                addAction(Intent.ACTION_VIEW)
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            }
        )

        // VoIP style (notification_style_type = "6") uses different key format: cust_btn_N_*
        val metaExtra = mapOf(
            "notification_style_type" to "6",
            "cust_btn_1_n" to "Join Call",
            "cust_btn_1_ne" to PushConstants.NOTIFICATION_CLICK_WEB_PAGE,
            "cust_btn_1_wu" to webUri
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 1, metaExtra)

        assertNotNull(pendingIntent,
            "VoIP style button with WEB_PAGE and valid URI should return non-null PendingIntent")

        val shadow = shadowOf(pendingIntent!!)
        assertTrue(shadow.isActivity,
            "VoIP WEB_PAGE style button should produce Activity PendingIntent")
        assertEquals(Intent.ACTION_VIEW, shadow.savedIntent.action,
            "VoIP WEB_PAGE style button should have ACTION_VIEW")
        assertEquals(webUri, shadow.savedIntent.data.toString(),
            "VoIP WEB_PAGE style button should preserve URI")
    }

    @Test
    @DisplayName("VoIP style button with empty notify_effect returns null")
    fun `VoIP style button with empty notify_effect returns null`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.voip"

        // VoIP style with empty notify_effect
        val metaExtra = mapOf(
            "notification_style_type" to "6",
            "cust_btn_1_n" to "Action",
            "cust_btn_1_ne" to ""
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 1, metaExtra)

        assertNull(pendingIntent,
            "VoIP style button with empty notify_effect should return null")
    }
}
