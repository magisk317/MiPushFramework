package io.github.magisk317.mipush.common.notification

import android.app.Notification
import android.os.Bundle
import androidx.core.app.NotificationCompat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class SinglePackageNotificationGroupPolicyTest {

    @Test
    fun `native ungrouped notification rewrites to package group`() {
        val context = RuntimeEnvironment.getApplication()
        val notification = NotificationCompat.Builder(context, "ch")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("native")
            .build()

        assertTrue(notification.group.isNullOrEmpty() || notification.group != "com.tencent.wework")
        assertTrue(
            SinglePackageNotificationGroupPolicy.needsRewrite(
                postingPackage = "com.tencent.wework",
                currentGroup = notification.group,
                extras = notification.extras,
            ),
        )
        assertTrue(SinglePackageNotificationGroupPolicy.apply("com.tencent.wework", notification))
        assertEquals("com.tencent.wework", notification.group)
    }

    @Test
    fun `mipush custom group collapses to package group`() {
        val context = RuntimeEnvironment.getApplication()
        val notification = NotificationCompat.Builder(context, "ch")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup("com.tencent.wework_mipush_chat")
            .setContentTitle("mipush")
            .build()
        notification.extras.putString("target_package", "com.tencent.wework")

        assertTrue(SinglePackageNotificationGroupPolicy.apply("com.tencent.wework", notification))
        assertEquals("com.tencent.wework", notification.group)
    }

    @Test
    fun `delegated post uses target package not xmsf`() {
        val extras = Bundle().apply {
            putString("target_package", "com.tencent.wework")
        }
        assertEquals(
            "com.tencent.wework",
            SinglePackageNotificationGroupPolicy.resolveGroupOwnerPackage("com.xiaomi.xmsf", extras),
        )
        assertTrue(
            SinglePackageNotificationGroupPolicy.needsRewrite(
                postingPackage = "com.xiaomi.xmsf",
                currentGroup = "Aggregate_AlertingSection",
                extras = extras,
            ),
        )
    }

    @Test
    fun `island proxy is left untouched`() {
        val extras = Bundle().apply {
            putString("hyperisland.owner", "io.github.magisk317.mipush")
            putString("hyperisland_source_pkg", "com.tencent.wework")
        }
        assertTrue(SinglePackageNotificationGroupPolicy.isIslandProxy(extras))
        assertFalse(
            SinglePackageNotificationGroupPolicy.needsRewrite(
                postingPackage = "com.android.systemui",
                currentGroup = "mipush_island:com.tencent.wework",
                extras = extras,
            ),
        )
    }

    @Test
    fun `nms enqueue args rewrite mutates notification argument`() {
        val context = RuntimeEnvironment.getApplication()
        val notification = NotificationCompat.Builder(context, "ch")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup("other")
            .build()
        val args = arrayOf<Any?>(
            "com.example.app",
            "com.example.app",
            null,
            1,
            notification,
            0,
        )
        assertTrue(SinglePackageNotificationGroupPolicy.applyToNmsEnqueueArgs(args))
        assertEquals("com.example.app", (args[4] as Notification).group)
    }

    @Test
    fun `mock replay source package owns group`() {
        val extras = Bundle().apply {
            putString("mipush_mock_replay_source_package", "com.tencent.wework")
        }
        assertEquals(
            "com.tencent.wework",
            SinglePackageNotificationGroupPolicy.resolveGroupOwnerPackage("com.xiaomi.xmsf", extras),
        )
        assertTrue(
            SinglePackageNotificationGroupPolicy.needsRewrite(
                postingPackage = "com.xiaomi.xmsf",
                currentGroup = null,
                extras = extras,
            ),
        )
    }

    @Test
    fun `aggregate group key always needs rewrite`() {
        assertTrue(SinglePackageNotificationGroupPolicy.isAggregateGroupKey("0|com.tencent.wework|g:Aggregate_AlertingSection"))
        assertTrue(
            SinglePackageNotificationGroupPolicy.needsRewrite(
                postingPackage = "com.tencent.wework",
                currentGroup = "Aggregate_AlertingSection",
                extras = null,
            ),
        )
    }

    @Test
    fun `demotes mipush group summary when collapsing to package group`() {
        val context = RuntimeEnvironment.getApplication()
        val notification = NotificationCompat.Builder(context, "ch")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup("com.eg.android.AlipayGphone")
            .setGroupSummary(true)
            .setContentTitle("mipush summary")
            .build()
        notification.extras.putString("target_package", "com.eg.android.AlipayGphone")
        assertTrue((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0)
        // Group already canonical: apply still demotes the synthetic summary.
        assertTrue(SinglePackageNotificationGroupPolicy.apply("com.eg.android.AlipayGphone", notification))
        assertEquals("com.eg.android.AlipayGphone", notification.group)
        assertEquals(0, notification.flags and Notification.FLAG_GROUP_SUMMARY)
    }


    @Test
    fun `demotes xmsf-hosted summary without target extras`() {
        val context = RuntimeEnvironment.getApplication()
        val notification = NotificationCompat.Builder(context, "ch")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup("com.eg.android.AlipayGphone")
            .setGroupSummary(true)
            .setContentTitle("xmsf summary")
            .build()
        assertTrue(
            SinglePackageNotificationGroupPolicy.demoteDelegatedGroupSummary(
                postingPackage = "com.xiaomi.xmsf",
                notification = notification,
            )
        )
        assertEquals(0, notification.flags and Notification.FLAG_GROUP_SUMMARY)
    }
}
