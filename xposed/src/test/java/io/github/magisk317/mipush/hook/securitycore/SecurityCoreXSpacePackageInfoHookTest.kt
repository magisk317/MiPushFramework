package io.github.magisk317.mipush.hook.securitycore

import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityCoreXSpacePackageInfoHookTest {
    @Test
    fun `patch decision only allows SecurityCore mipush signal package queries`() {
        assertTrue(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.miui.securitycore",
                queryPackage = "com.example.app",
                flags = GET_SERVICES or GET_PERMISSIONS,
                userId = 0,
                alreadyRequired = false,
            ).forceRequired
        )

        assertFalse(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.android.settings",
                queryPackage = "com.example.app",
                flags = GET_SERVICES or GET_PERMISSIONS,
                userId = 0,
                alreadyRequired = false,
            ).forceRequired
        )
        assertFalse(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.miui.securitycore",
                queryPackage = "com.example.app",
                flags = GET_SERVICES,
                userId = 0,
                alreadyRequired = false,
            ).forceRequired
        )
        assertFalse(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.miui.securitycore",
                queryPackage = "com.xiaomi.xmsf",
                flags = GET_SERVICES or GET_PERMISSIONS,
                userId = 0,
                alreadyRequired = false,
            ).forceRequired
        )
    }

    @Test
    fun `ensure mipush signal adds expected permission and handler service`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.app"
            requestedPermissions = arrayOf("android.permission.INTERNET")
            services = arrayOf(ServiceInfo().apply { name = "com.example.OtherService" })
        }

        SecurityCoreXSpacePackageInfoHook.ensureMiPushRequiredSignal("com.example.app", packageInfo)

        assertTrue(
            packageInfo.requestedPermissions.orEmpty().contains("com.example.app.permission.MIPUSH_RECEIVE")
        )
        assertTrue(
            packageInfo.services.orEmpty().any { it.name == "com.xiaomi.mipush.sdk.PushMessageHandler" }
        )
        assertTrue(SecurityCoreXSpacePackageInfoHook.hasMiPushRequiredSignal("com.example.app", packageInfo))
    }

    @Test
    fun `ensure mipush signal does not duplicate existing entries`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.app"
            requestedPermissions = arrayOf("com.example.app.permission.MIPUSH_RECEIVE")
            services = arrayOf(ServiceInfo().apply { name = "com.xiaomi.mipush.sdk.PushMessageHandler" })
        }

        SecurityCoreXSpacePackageInfoHook.ensureMiPushRequiredSignal("com.example.app", packageInfo)

        assertEquals(1, packageInfo.requestedPermissions.orEmpty().count { it == "com.example.app.permission.MIPUSH_RECEIVE" })
        assertEquals(1, packageInfo.services.orEmpty().count { it.name == "com.xiaomi.mipush.sdk.PushMessageHandler" })
    }

    private companion object {
        private const val GET_SERVICES = 0x00000004L
        private const val GET_PERMISSIONS = 0x00001000L
    }
}
