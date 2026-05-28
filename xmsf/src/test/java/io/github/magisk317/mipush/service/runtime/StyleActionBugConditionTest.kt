package io.github.magisk317.mipush.service.runtime

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import com.xiaomi.push.service.PushConstants
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
 * Bug Condition Exploration Test for Style Action Buttons
 *
 * **Validates: Requirements 4.1**
 *
 * Property 1: Bug Condition - Style Action Buttons with notify_effect Use Activity PendingIntent (White Screen)
 *
 * This test encodes the EXPECTED (correct) behavior:
 * - `getStylePendingIntent()` should return a Service PendingIntent targeting MyPushMessageHandler
 *   when the style button has `notify_effect` = NOTIFICATION_CLICK_DEFAULT or NOTIFICATION_CLICK_INTENT
 *
 * On UNFIXED code, these tests FAIL because:
 * - `getStylePendingIntent()` currently ALWAYS calls `PendingIntent.getActivity()` regardless of `notify_effect` type
 * - Non-web style actions should use Service path but currently use Activity path
 *
 * Test failure confirms the bug exists: `getStylePendingIntent()` returns `PendingIntent.getActivity()`
 * for DEFAULT/INTENT style actions, causing white screen on cold start when the button launches
 * a deep-link Activity.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
@DisplayName("Bug Condition: Style Action Buttons with DEFAULT/INTENT Should Use Service PendingIntent")
class StyleActionBugConditionTest {

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

    /**
     * **Validates: Requirements 4.1**
     *
     * Property: getStylePendingIntent() MUST return a Service PendingIntent targeting
     * MyPushMessageHandler when the style button has notify_effect = NOTIFICATION_CLICK_DEFAULT ("1").
     *
     * Bug condition: On unfixed code, getStylePendingIntent() unconditionally calls
     * PendingIntent.getActivity() for ALL resolved intents, including DEFAULT effect.
     * This causes white screen on cold start when the button launches the app's main Activity
     * before Application class initialization completes.
     */
    @Test
    @DisplayName("Style button with notify_effect=DEFAULT returns Service PendingIntent (not Activity)")
    fun `getStylePendingIntent returns Service PendingIntent for NOTIFICATION_CLICK_DEFAULT`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.taobao.idlefish"
        val launcherActivity = "com.taobao.idlefish.MainLauncherActivity"

        // Register a launch intent for the target package so getLaunchIntent resolves
        shadowOf(context.packageManager).addActivityIfNotPresent(
            ComponentName(targetPackage, launcherActivity)
        )
        shadowOf(context.packageManager).addIntentFilterForActivity(
            ComponentName(targetPackage, launcherActivity),
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_MAIN)
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
        )

        // Style button left with notify_effect = "1" (NOTIFICATION_CLICK_DEFAULT)
        val metaExtra = mapOf(
            "notification_style_button_left_name" to "Open App",
            "notification_style_button_left_notify_effect" to PushConstants.NOTIFICATION_CLICK_DEFAULT
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 1, metaExtra)

        assertNotNull(pendingIntent,
            "getStylePendingIntent() should return a non-null PendingIntent for DEFAULT style action with resolvable launch intent")

        val shadow = shadowOf(pendingIntent!!)

        // Expected behavior after fix: Service PendingIntent targeting MyPushMessageHandler
        // Bug condition: currently returns Activity PendingIntent
        assertTrue(shadow.isService,
            "getStylePendingIntent() should return a Service PendingIntent for notify_effect=DEFAULT style button. " +
            "Bug: currently returns PendingIntent.getActivity() which directly launches the app's main Activity, " +
            "causing white screen on cold start because Application class hasn't initialized."
        )
        assertFalse(shadow.isActivity,
            "getStylePendingIntent() should NOT return an Activity PendingIntent for DEFAULT style actions. " +
            "Activity PendingIntent causes direct Activity launch → white screen on cold start."
        )
    }

    /**
     * **Validates: Requirements 4.1**
     *
     * Property: getStylePendingIntent() MUST return a Service PendingIntent targeting
     * MyPushMessageHandler when the style button has notify_effect = NOTIFICATION_CLICK_INTENT ("2")
     * and an intent_uri is specified.
     *
     * Bug condition: On unfixed code, getStylePendingIntent() unconditionally calls
     * PendingIntent.getActivity() for ALL resolved intents, including INTENT effect with intent_uri.
     * This causes white screen on cold start when the button launches a deep-link Activity
     * before Application class initialization completes.
     */
    @Test
    @DisplayName("Style button with notify_effect=INTENT and intent_uri returns Service PendingIntent (not Activity)")
    fun `getStylePendingIntent returns Service PendingIntent for NOTIFICATION_CLICK_INTENT with intent_uri`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.taobao.idlefish"
        val targetActivity = "com.taobao.idlefish.ChatDetailActivity"

        // Register the target activity so resolveActivity succeeds
        shadowOf(context.packageManager).addActivityIfNotPresent(
            ComponentName(targetPackage, targetActivity)
        )

        // intent_uri that resolves to the target activity
        val intentUri = "intent:#Intent;component=$targetPackage/$targetActivity;end"

        // Style button mid with notify_effect = "2" (NOTIFICATION_CLICK_INTENT) and intent_uri
        val metaExtra = mapOf(
            "notification_style_button_mid_name" to "View Chat",
            "notification_style_button_mid_notify_effect" to PushConstants.NOTIFICATION_CLICK_INTENT,
            "notification_style_button_mid_intent_uri" to intentUri
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 2, metaExtra)

        assertNotNull(pendingIntent,
            "getStylePendingIntent() should return a non-null PendingIntent for INTENT style action with resolvable intent_uri")

        val shadow = shadowOf(pendingIntent!!)

        // Expected behavior after fix: Service PendingIntent targeting MyPushMessageHandler
        // Bug condition: currently returns Activity PendingIntent
        assertTrue(shadow.isService,
            "getStylePendingIntent() should return a Service PendingIntent for notify_effect=INTENT style button with intent_uri. " +
            "Bug: currently returns PendingIntent.getActivity() which directly launches the deep-link Activity, " +
            "causing white screen on cold start because Application class hasn't initialized."
        )
        assertFalse(shadow.isActivity,
            "getStylePendingIntent() should NOT return an Activity PendingIntent for INTENT style actions. " +
            "Activity PendingIntent causes direct Activity launch → white screen on cold start."
        )
    }

    /**
     * **Validates: Requirements 4.1**
     *
     * Property: getStylePendingIntent() MUST return a Service PendingIntent targeting
     * MyPushMessageHandler when the style button has notify_effect = NOTIFICATION_CLICK_INTENT ("2")
     * and an intent_class is specified.
     *
     * Bug condition: On unfixed code, getStylePendingIntent() unconditionally calls
     * PendingIntent.getActivity() for ALL resolved intents, including INTENT effect with intent_class.
     * This causes white screen on cold start when the button launches a deep-link Activity
     * before Application class initialization completes.
     */
    @Test
    @DisplayName("Style button with notify_effect=INTENT and intent_class returns Service PendingIntent (not Activity)")
    fun `getStylePendingIntent returns Service PendingIntent for NOTIFICATION_CLICK_INTENT with intent_class`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.taobao.idlefish"
        val targetActivity = "com.taobao.idlefish.OrderDetailActivity"

        // Register the target activity so resolveActivity succeeds
        shadowOf(context.packageManager).addActivityIfNotPresent(
            ComponentName(targetPackage, targetActivity)
        )

        // Style button right with notify_effect = "2" (NOTIFICATION_CLICK_INTENT) and intent_class
        val metaExtra = mapOf(
            "notification_style_button_right_name" to "View Order",
            "notification_style_button_right_notify_effect" to PushConstants.NOTIFICATION_CLICK_INTENT,
            "notification_style_button_right_intent_class" to targetActivity
        )

        val pendingIntent = invokeGetStylePendingIntent(context, targetPackage, 3, metaExtra)

        assertNotNull(pendingIntent,
            "getStylePendingIntent() should return a non-null PendingIntent for INTENT style action with resolvable intent_class")

        val shadow = shadowOf(pendingIntent!!)

        // Expected behavior after fix: Service PendingIntent targeting MyPushMessageHandler
        // Bug condition: currently returns Activity PendingIntent
        assertTrue(shadow.isService,
            "getStylePendingIntent() should return a Service PendingIntent for notify_effect=INTENT style button with intent_class. " +
            "Bug: currently returns PendingIntent.getActivity() which directly launches the deep-link Activity, " +
            "causing white screen on cold start because Application class hasn't initialized."
        )
        assertFalse(shadow.isActivity,
            "getStylePendingIntent() should NOT return an Activity PendingIntent for INTENT style actions with intent_class. " +
            "Activity PendingIntent causes direct Activity launch → white screen on cold start."
        )
    }
}
