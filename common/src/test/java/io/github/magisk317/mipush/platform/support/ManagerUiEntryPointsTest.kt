package io.github.magisk317.mipush.platform.support

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManagerUiEntryPointsTest {
    @AfterEach
    fun tearDown() {
        unmockkStatic(ComponentName::class)
    }

    @Test
    fun `loadable UI classes without local activity declaration use canonical manager`() {
        val context = host("com.xiaomi.xmsf", declared = false)
        // UI is packaged in the runtime too; classloader availability must not select its package.
        every { context.classLoader } returns javaClass.classLoader
        assertEquals(ManagerComponentNames.PACKAGE, ManagerUiEntryPoints.managerUiPackage(context))
        verify(exactly = 0) { context.classLoader }
    }

    @Test
    fun `manifest declared manager host uses its own package`() {
        val context = host("test.manager.host", declared = true)
        assertEquals("test.manager.host", ManagerUiEntryPoints.managerUiPackage(context))
    }

    private fun host(packageName: String, declared: Boolean): Context {
        val manager = mockk<PackageManager>()
        val component = mockk<ComponentName>()
        val context = mockk<Context>()
        every { context.packageName } returns packageName
        every { context.packageManager } returns manager
        mockkStatic(ComponentName::class)
        every { ComponentName.createRelative(packageName, ManagerComponentNames.MAIN_ACTIVITY) } returns component
        if (declared) {
            every { manager.getActivityInfo(component, 0) } returns mockk<ActivityInfo>()
        } else {
            every { manager.getActivityInfo(component, 0) } throws mockk<PackageManager.NameNotFoundException>()
        }
        return context
    }
}
