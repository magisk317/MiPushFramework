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
 * This test encodes the EXPECTED behavior:
 * - `shouldUseSdkActivityClick(true)` should return `true` (use Activity path when SDK intent available)
 * - `shouldUseSdkActivityClick(false)` should return `false` (use Service path when no SDK intent)
 * - `buildClickedPendingIntent()` should return an Activity PendingIntent targeting the resolved
 *   SDK intent for regular packages when an SDK intent is available
 * - known problematic packages should bypass the sdk_activity route and use the safer service path
 * - `buildClickedPendingIntent()` should return a Service PendingIntent when no SDK intent is available
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
@DisplayName("Bug Condition: SDK Intent Notifications Path Selection")
class MyMIPushNotificationIntentSupportBugConditionTest {

    @Test
    @DisplayName("shouldUseSdkActivityClick(true) returns true (use Activity path for SDK intents)")
    fun `shouldUseSdkActivityClick with sdkIntentAvailable true returns true`() {
        val result = MyMIPushNotificationIntentSupport.shouldUseSdkActivityClick(sdkIntentAvailable = true)
        assertTrue(result,
            "shouldUseSdkActivityClick(true) should return true to use Activity PendingIntent " +
            "when SDK intent is available, enabling direct deep-link navigation."
        )
    }

    @Test
    @DisplayName("shouldUseSdkActivityClick(false) returns false (use Service path without SDK intents)")
    fun `shouldUseSdkActivityClick with sdkIntentAvailable false returns false`() {
        val result = MyMIPushNotificationIntentSupport.shouldUseSdkActivityClick(sdkIntentAvailable = false)
        assertFalse(result,
            "shouldUseSdkActivityClick(false) should return false to use Service PendingIntent " +
            "when no SDK intent is available."
        )
    }

    @Test
    @DisplayName("buildClickedPendingIntent returns Activity PendingIntent for regular SDK intent with class_name")
    fun `buildClickedPendingIntent returns Activity PendingIntent when SDK intent is available with explicit class`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.example.target"
        val targetClass = "com.example.target.ChatDetailActivity"
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

        assertTrue(shadow.isActivity,
            "buildClickedPendingIntent() should return an Activity PendingIntent when SDK intent is available, " +
            "enabling direct deep-link navigation to the target Activity."
        )
        assertFalse(shadow.isService,
            "buildClickedPendingIntent() should NOT return a Service PendingIntent for SDK intent notifications."
        )
        assertEquals(
            targetComponent,
            shadow.savedIntent.component,
            "Activity PendingIntent should target the resolved SDK intent component."
        )
    }

    @Test
    @DisplayName("buildClickedPendingIntent bypasses sdk_activity for known white-screen packages")
    fun `buildClickedPendingIntent returns Service PendingIntent for idlefish even when SDK intent resolves`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.taobao.idlefish"
        val targetClass = "com.taobao.idlefish.ChatDetailActivity"
        val targetComponent = ComponentName(targetPackage, targetClass)

        val container = XmPushActionContainer().apply {
            packageName = targetPackage
            metaInfo = PushMetaInfo().apply {
                setId("msg-001-idlefish")
                setNotifyId(1)
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
            notificationId = 101,
            extra = null,
        )

        assertNotNull(pendingIntent, "PendingIntent should not be null for blacklisted SDK intent notification")
        val shadow = shadowOf(pendingIntent!!)

        assertTrue(
            shadow.isService,
            "Known white-screen packages should bypass sdk_activity and use the service path."
        )
        assertFalse(
            shadow.isActivity,
            "Known white-screen packages should not directly launch the resolved SDK Activity."
        )
    }

    @Test
    @DisplayName("known QQ click route uses the real launcher activity")
    fun `buildClickedPendingIntent uses explicit launcher for QQ`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.tencent.mobileqq"
        val launcherClass = "com.tencent.mobileqq.activity.SplashActivity"
        val launcherComponent = ComponentName(targetPackage, launcherClass)
        shadowOf(context.packageManager).addActivityIfNotPresent(launcherComponent)
        shadowOf(context.packageManager).addIntentFilterForActivity(
            launcherComponent,
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_MAIN)
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            },
        )

        val container = XmPushActionContainer().apply {
            packageName = targetPackage
            metaInfo = PushMetaInfo().apply {
                setId("msg-qq-launcher")
                setNotifyId(3)
                extra = mutableMapOf(
                    PushConstants.EXTRA_PARAM_NOTIFY_EFFECT to PushConstants.NOTIFICATION_CLICK_INTENT,
                    PushConstants.EXTRA_PARAM_CLASS_NAME to "com.tencent.mobileqq.activity.JumpActivity",
                )
            }
        }

        val pendingIntent = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(7, 8, 9),
            notificationId = 301,
            extra = null,
        )

        assertNotNull(pendingIntent)
        val shadow = shadowOf(pendingIntent!!)
        assertTrue(shadow.isActivity)
        assertEquals(launcherComponent, shadow.savedIntent.component)
    }

    @Test
    @DisplayName("buildClickedPendingIntent returns Service PendingIntent when no SDK intent (notify_effect=1, no launch intent)")
    fun `buildClickedPendingIntent returns Service PendingIntent when no SDK intent available`() {
        val context = RuntimeEnvironment.getApplication()
        val targetPackage = "com.taobao.idlefish"

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

        // Do NOT register a launch intent — getSdkIntent() will return null

        val pendingIntent = MyMIPushNotificationIntentSupport.buildClickedPendingIntent(
            context = context,
            container = container,
            decryptedContent = byteArrayOf(4, 5, 6),
            notificationId = 200,
            extra = null,
        )

        assertNotNull(pendingIntent, "PendingIntent should not be null even without SDK intent")
        val shadow = shadowOf(pendingIntent!!)

        assertTrue(shadow.isService,
            "buildClickedPendingIntent() should return a Service PendingIntent when no SDK intent is available."
        )
        assertFalse(shadow.isActivity,
            "buildClickedPendingIntent() should NOT return an Activity PendingIntent when no SDK intent is available."
        )
    }
}
