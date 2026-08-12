package io.github.magisk317.mipush.hook.securitycore

import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityCoreXSpacePackageInfoHookTest {
    @Test
    fun `hooks the class that actually declares the package info binder method`() {
        assertTrue(
            "com.android.server.pm.IPackageManagerBase" in
                SecurityCoreXSpacePackageInfoHook.packageManagerHookTargets()
        )
    }

    @Test
    fun `patch decision only allows SecurityCore mipush signal package queries`() {
        assertTrue(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.miui.securitycore",
                queryPackage = "io.github.magisk317.mipush",
                flags = GET_SERVICES or GET_PERMISSIONS,
                userId = 999,
                alreadyRequired = false,
                dualAppEnabled = true,
            ).forceRequired
        )
        assertTrue(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.miui.securitycore",
                queryPackage = "io.github.magisk317.mipush",
                flags = GET_SERVICES or GET_PERMISSIONS,
                userId = 0,
                alreadyRequired = false,
                dualAppEnabled = true,
            ).forceRequired,
            "the dumped SecurityCore path reads MiPush manifest signals from owner user 0",
        )

        assertFalse(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.miui.securitycore",
                queryPackage = "com.example.app",
                flags = GET_SERVICES or GET_PERMISSIONS,
                userId = 999,
                alreadyRequired = false,
                dualAppEnabled = true,
            ).forceRequired
        )
        assertFalse(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.android.settings",
                queryPackage = "io.github.magisk317.mipush",
                flags = GET_SERVICES or GET_PERMISSIONS,
                userId = 999,
                alreadyRequired = false,
                dualAppEnabled = true,
            ).forceRequired
        )
        assertFalse(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.miui.securitycore",
                queryPackage = "io.github.magisk317.mipush",
                flags = GET_SERVICES,
                userId = 999,
                alreadyRequired = false,
                dualAppEnabled = true,
            ).forceRequired
        )
        assertFalse(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.miui.securitycore",
                queryPackage = "com.xiaomi.xmsf",
                flags = GET_SERVICES or GET_PERMISSIONS,
                userId = 999,
                alreadyRequired = false,
                dualAppEnabled = true,
            ).forceRequired
        )
        assertFalse(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.miui.securitycore",
                queryPackage = "io.github.magisk317.mipush",
                flags = GET_SERVICES or GET_PERMISSIONS,
                userId = 10,
                alreadyRequired = false,
                dualAppEnabled = true,
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

    @Test
    fun `dual app disabled skips force retention even for mipush module`() {
        assertFalse(
            SecurityCoreXSpacePackageInfoHook.decidePackageInfoPatch(
                callerProcessName = "com.miui.securitycore",
                queryPackage = "io.github.magisk317.mipush",
                flags = GET_SERVICES or GET_PERMISSIONS,
                userId = 999,
                alreadyRequired = false,
                dualAppEnabled = false,
            ).forceRequired
        )
    }

    @Test
    fun `user id parser does not treat the legacy flags argument as a user`() {
        assertEquals(
            null,
            SecurityCoreXSpacePackageInfoHook.userIdArgIndex(
                listOf(String::class.java, Int::class.javaPrimitiveType!!),
            ),
        )
        assertEquals(
            2,
            SecurityCoreXSpacePackageInfoHook.userIdArgIndex(
                listOf(
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                ),
            ),
        )
        assertEquals(
            2,
            SecurityCoreXSpacePackageInfoHook.userIdArgIndex(
                listOf(
                    String::class.java,
                    Long::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                ),
            ),
        )
    }
}
