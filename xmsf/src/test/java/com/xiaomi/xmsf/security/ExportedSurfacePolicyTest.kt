package com.xiaomi.xmsf.security

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.os.Process
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ExportedSurfacePolicyTest {
    @Test
    fun `push control accepts only exact read-only queries`() {
        val uri = Uri.parse("content://${ExportedSurfacePolicy.PUSH_CONTROL_AUTHORITY}/control")

        assertTrue(ExportedSurfacePolicy.isPushControlQueryAllowed(uri, null, null, null, null))
        assertTrue(
            ExportedSurfacePolicy.isPushControlQueryAllowed(
                uri,
                arrayOf("control_mode", "control_switch"),
                null,
                null,
                null,
            ),
        )
        assertFalse(
            ExportedSurfacePolicy.isPushControlQueryAllowed(
                Uri.parse("$uri?unexpected=1"),
                null,
                null,
                null,
                null,
            ),
        )
        assertFalse(
            ExportedSurfacePolicy.isPushControlQueryAllowed(
                Uri.parse("content://${ExportedSurfacePolicy.PUSH_CONTROL_AUTHORITY}/other"),
                null,
                null,
                null,
                null,
            ),
        )
        assertFalse(ExportedSurfacePolicy.isPushControlQueryAllowed(uri, arrayOf("secret"), null, null, null))
        assertFalse(ExportedSurfacePolicy.isPushControlQueryAllowed(uri, null, "1=1", null, null))
    }

    @Test
    fun `push common accepts only bounded support probes`() {
        assertTrue(ExportedSurfacePolicy.isPushCommonCallAllowed("is_push_support", null, null))
        assertTrue(
            ExportedSurfacePolicy.isPushCommonCallAllowed(
                "is_push_support",
                null,
                Bundle().apply { putInt("push_support_flag", 4) },
            ),
        )
        assertFalse(ExportedSurfacePolicy.isPushCommonCallAllowed("unknown", null, null))
        assertFalse(ExportedSurfacePolicy.isPushCommonCallAllowed("is_push_support", "arg", null))
        assertFalse(
            ExportedSurfacePolicy.isPushCommonCallAllowed(
                "is_push_support",
                null,
                Bundle().apply { putString("push_support_flag", "4") },
            ),
        )
        assertFalse(
            ExportedSurfacePolicy.isPushCommonCallAllowed(
                "is_push_support",
                null,
                Bundle().apply {
                    putInt("push_support_flag", 4)
                    putString("extra", "unexpected")
                },
            ),
        )
    }

    @Test
    fun `profile package is bound to caller uid`() {
        assertEquals(
            "app.owner",
            ExportedSurfacePolicy.resolveProfilePackageName(
                requestedPackage = "app.owner",
                callingUid = 20_001,
                appUid = 10_001,
                packagesForUid = listOf("app.owner"),
            ),
        )
        assertNull(
            ExportedSurfacePolicy.resolveProfilePackageName(
                requestedPackage = "app.victim",
                callingUid = 20_001,
                appUid = 10_001,
                packagesForUid = listOf("app.owner"),
            ),
        )
        assertNull(
            ExportedSurfacePolicy.resolveProfilePackageName(
                requestedPackage = null,
                callingUid = 20_001,
                appUid = 10_001,
                packagesForUid = listOf("app.one", "app.two"),
            ),
        )
        assertEquals(
            "app.target",
            ExportedSurfacePolicy.resolveProfilePackageName(
                requestedPackage = "app.target",
                callingUid = Process.SYSTEM_UID,
                appUid = 10_001,
                packagesForUid = emptyList(),
            ),
        )
    }

}
