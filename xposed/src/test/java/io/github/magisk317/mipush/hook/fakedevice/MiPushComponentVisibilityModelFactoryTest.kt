package io.github.magisk317.mipush.hook.fakedevice

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushComponentVisibilityModelFactoryTest {
    @Test
    fun `fake xmsf package info preserves package version and component order`() {
        val packageInfo = MiPushComponentVisibilityModelFactory.fakeXmsfPackageInfo()

        assertEquals("com.xiaomi.xmsf", packageInfo.packageName)
        assertEquals("7.5.29-C", packageInfo.versionName)
        @Suppress("DEPRECATION")
        assertEquals(70005029, packageInfo.versionCode)
        assertEquals("com.xiaomi.xmsf", packageInfo.applicationInfo?.packageName)
        assertTrue(packageInfo.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0)
        assertEquals(
            listOf("top.trumeet.mipushframework.main.MainActivity"),
            packageInfo.activities.orEmpty().map { it.name },
        )
        assertEquals(
            listOf(
                "com.xiaomi.push.service.XMPushService",
                "com.xiaomi.xmsf.push.service.XMPushService",
                "com.xiaomi.push.service.XMJobService",
            ),
            packageInfo.services.orEmpty().map { it.name },
        )
        assertEquals("android.permission.BIND_JOB_SERVICE", packageInfo.services?.last()?.permission)
        assertFalse(packageInfo.services?.last()?.exported ?: true)
    }

    @Test
    fun `fake xmsf providers preserve order and provider fields`() {
        val providers = MiPushComponentVisibilityModelFactory.fakeXmsfProviderInfos()

        assertEquals(
            listOf(
                "com.xiaomi.xmsf.provider.CHANNEL",
                "com.xiaomi.push.provider.PUSH_SUPPORT",
                "com.xiaomi.push.provider.PUSH_COMMON",
                "com.xiaomi.push.provider.profile",
            ),
            providers.map { it.authority },
        )
        assertEquals(
            listOf(
                "com.xiaomi.xmsf.provider.ChannelProvider",
                "com.xiaomi.push.provider.PushSupportProvider",
                "com.xiaomi.push.provider.PushCommonProvider",
                "com.xiaomi.xmsf.provider.PushProfileIdProvider",
            ),
            providers.map { it.name },
        )
        assertEquals("com.xiaomi.xmsf.permission.CHANNEL", providers[0].readPermission)
        assertEquals("com.xiaomi.xmsf.permission.CHANNEL", providers[0].writePermission)
        assertEquals("com.xiaomi.xmsf", providers[0].applicationInfo?.packageName)
        assertTrue(providers.all { it.enabled && it.exported && it.processName == "com.xiaomi.xmsf" })
    }

    @Test
    fun `component helpers preserve field defaults`() {
        val application = MiPushComponentVisibilityModelFactory.fakeApplicationInfo("example.app", system = false)
        val service = MiPushComponentVisibilityModelFactory.serviceInfo(
            "example.app",
            "example.Service",
            exported = false,
            permission = "example.PERMISSION",
        )
        val activity = MiPushComponentVisibilityModelFactory.activityInfo("example.app", "example.Activity", exported = true)

        assertEquals(0, application.flags)
        assertTrue(application.enabled)
        assertEquals("example.app", application.processName)
        assertEquals("example.PERMISSION", service.permission)
        assertFalse(service.exported)
        assertEquals("example.Service", service.name)
        assertTrue(activity.exported)
        assertEquals("example.Activity", activity.name)
    }

    @Test
    fun `merge keeps existing order and entries before new unique components`() {
        val existingServices = arrayOf(
            MiPushComponentVisibilityModelFactory.serviceInfo("app", "Existing", exported = false),
            MiPushComponentVisibilityModelFactory.serviceInfo("app", "Duplicate", exported = false),
        )
        val additions = listOf(
            MiPushComponentVisibilityModelFactory.serviceInfo("app", "Duplicate", exported = true),
            MiPushComponentVisibilityModelFactory.serviceInfo("app", "Added", exported = true),
        )

        val merged = MiPushComponentVisibilityModelFactory.mergeServices(existingServices, additions)

        assertEquals(listOf("Existing", "Duplicate", "Added"), merged.orEmpty().map { it.name })
        assertFalse(merged?.get(1)?.exported ?: true)
        assertSame(existingServices, MiPushComponentVisibilityModelFactory.mergeServices(existingServices, emptyList()))
    }

    @Test
    fun `merge activities skips nameless entries with the original semantics`() {
        val nameless = ActivityInfo()
        val existing = arrayOf(nameless)
        val additions = listOf(
            MiPushComponentVisibilityModelFactory.activityInfo("app", "First", exported = true),
            MiPushComponentVisibilityModelFactory.activityInfo("app", "Second", exported = true),
        )

        val merged = MiPushComponentVisibilityModelFactory.mergeActivities(existing, additions)

        assertEquals(listOf("First", "Second"), merged.orEmpty().map { it.name })
        assertSame(existing, MiPushComponentVisibilityModelFactory.mergeActivities(existing, emptyList()))
    }
}
