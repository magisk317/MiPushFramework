package com.xiaomi.xmsf.security

import android.os.Process
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
class ExportedSurfacePolicyTest {
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
