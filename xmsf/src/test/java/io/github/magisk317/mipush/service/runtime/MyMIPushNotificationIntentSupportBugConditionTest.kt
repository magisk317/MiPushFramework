package io.github.magisk317.mipush.service.runtime

import android.content.ComponentName
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.junit.jupiter.api.Assertions.assertEquals
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

/**
 * Bug Condition Exploration Test
 *
 * **Validates: Requirements 1.1, 2.1, 2.2**
 *
 * Property 1: Bug Condition - SDK Intent Notifications Use Activity PendingIntent (White Screen)
 *
 * This test encodes the EXPECTED (correct) behavior:
 * - `shouldUseSdkActivityClick(true)` should return `false`
 * - `buildClickedPendingIntent()` should return a Service PendingIntent targeting MyPushMessageHandler
 *   when an SDK intent is available (container has `notify_effect` and no URL)
 *
 * On UNFIXED code, these tests FAIL because:
 * - `shouldUseSdkActivityClick(true)` currently returns `true` (not `false`)
 * - `buildClickedPendingIntent()` currently returns Activity PendingIntent (not Service PendingIntent)
 *
 * Test failure confirms the bug exists: `shouldUseSdkActivityClick(true)` returns `true`,
 * causing `PendingIntent.getActivity()` to be used for SDK intent notifications,
 * which leads to white screen on cold start.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
@DisplayName("Bug Condition: SDK Intent Notifications Should Use Service PendingIntent")
class MyMIPushNotificationIntentSupportBugConditionTest {

    /**
     * **Validates: Requirements 2.1, 2.2**
     *
     * Property: shouldUseSdkActivityClick(true) MUST return false to prevent direct Activity launch.
     *
     * Bug condition: On unfixed code, this returns `true`, causing Activity PendingIntent creation
     * which leads to white screen when the target app is not running (cold start).
     */
    @Test
    @DisplayName("shouldUseSdkActivityClick(true) returns false (prevents Activity PendingIntent for SDK intents)")
    fun `shouldUseSdkActivityClick with sdkIntentAvailable true returns false`() {
        val result = MyMIPushNotificationIntentSupport.shouldUseSdkActivityClick(sdkIntentAvailable = true)

        // Expected behavior after fix: always return false to force Service path
        // Bug condition: currently returns true, causing direct Activity launch → white screen
        assertFalse(result,
            "shouldUseSdkActivityClick(true) should return false to prevent Activity PendingIntent. " +
            "Returning true causes PendingIntent.getActivity() for SDK intent notifications, " +
            "leading to white screen on cold start because the app's Application class " +
            "has not finished initialization."
        )
    }

    /**
     * **Validates: Requirements 1.1, 2.1, 2.2**
     *
     * Property: buildClickedPendingIntent() MUST return a Service PendingIntent targeting
     * MyPushMessageHandler when an SDK intent is available (notify_effect=NOTIFICATION_CLICK_INTENT,
     * class_name specified, no URL).
     *
     * Bug condition: On unfixed code, this returns an Activity PendingIntent targeting the
     * app's deep-link Activity directly, which causes white screen on cold start.
     */
    @Test
    @DisplayName("buildClickedPendingIntent returns Service PendingIntent for SDK intent with class_name (notify_effect=2)")
    fun `buildClickedPendingIntent returns Service PendingIntent when SDK intent is available with explicit class`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.taobao.idlefish"
        val targetClass = "com.taobao.idlefish.ChatDetailActivity"
        val targetComponent = ComponentName(targetPackage, targetClass)

        val container = XmPushActionContainer().apply {
            packageName = targetPackage
            metaInfo = PushMetaInfo().apply {
                setId("msg-001")
                setNotifyId(1)
                extra = mutableMapOf(
                    PushConstants.EXTRA_PARAM_NOTIFY_EFFECT to PushConstants.NOTIFICATION_CLICK_INTENT,
                    PushConstants.EXTRA_PARAM_CLASS_NAME to targetClass,
                )
            }
        }

        // Register the target activity so getSdkIntent() resolves it
        shadowOf(context.packageManager).addActivityIfNotPresent(targetComponent)

        val pendingIntent = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(1, 2, 3),
            notificationId = 100,
            extra = null,
        )

        assertNotNull(pendingIntent, "PendingIntent should not be null for SDK intent notification")
        val shadow = shadowOf(pendingIntent!!)

        // Expected behavior: Service PendingIntent targeting MyPushMessageHandler
        assertTrue(shadow.isService,
            "buildClickedPendingIntent() should return a Service PendingIntent when SDK intent is available. " +
            "Bug: currently returns Activity PendingIntent which directly launches the deep-link Activity, " +
            "causing white screen on cold start because Application class hasn't initialized."
        )
        assertFalse(shadow.isActivity,
            "buildClickedPendingIntent() should NOT return an Activity PendingIntent for SDK intent notifications. " +
            "Activity PendingIntent causes direct Activity launch → white screen on cold start."
        )
        assertEquals(
            "com.xiaomi.push.sdk.MyPushMessageHandler",
            shadow.savedIntent.component?.className,
            "Service PendingIntent should target MyPushMessageHandler for proper app initialization via pullUpApp()"
        )
    }

    /**
     * **Validates: Requirements 1.1, 2.1, 2.2**
     *
     * Property: buildClickedPendingIntent() MUST return a Service PendingIntent targeting
     * MyPushMessageHandler when SDK intent is available with notify_effect=1 (default launch).
     *
     * This covers the case where notify_effect="1" (NOTIFICATION_CLICK_DEFAULT) resolves
     * to the app's launch intent. On unfixed code, this also produces an Activity PendingIntent.
     */
    @Test
    @DisplayName("buildClickedPendingIntent returns Service PendingIntent for SDK intent with default launch (notify_effect=1)")
    fun `buildClickedPendingIntent returns Service PendingIntent when SDK intent is available with default launch`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.taobao.idlefish"
        val launcherActivity = "com.taobao.idlefish.MainLauncherActivity"

        val container = XmPushActionContainer().apply {
            packageName = targetPackage
            metaInfo = PushMetaInfo().apply {
                setId("msg-002")
                setNotifyId(2)
                extra = mutableMapOf(
                    PushConstants.EXTRA_PARAM_NOTIFY_EFFECT to PushConstants.NOTIFICATION_CLICK_DEFAULT,
                )
            }
        }

        // Register a launch intent for the target package
        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            component = ComponentName(targetPackage, launcherActivity)
        }
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

        val pendingIntent = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(4, 5, 6),
            notificationId = 200,
            extra = null,
        )

        // If getSdkIntent returns non-null (launch intent resolved), the fix should use Service path
        // If getSdkIntent returns null (launch intent not resolved), Service path is already used
        // Either way, the result should be a Service PendingIntent
        if (pendingIntent != null) {
            val shadow = shadowOf(pendingIntent)
            assertTrue(shadow.isService,
                "buildClickedPendingIntent() should return a Service PendingIntent for notify_effect=1 (default launch). " +
                "Bug: currently returns Activity PendingIntent which directly launches the app's main Activity, " +
                "causing white screen on cold start."
            )
            assertFalse(shadow.isActivity,
                "buildClickedPendingIntent() should NOT return an Activity PendingIntent for default launch notifications."
            )
        }
        // If pendingIntent is null, getSdkIntent didn't resolve (acceptable - no bug to demonstrate)
    }
}
