package com.xiaomi.xmsf.utils

import android.content.pm.ApplicationInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class ModuleLogProviderPolicyTest {
    @Test
    fun `module log caller allowlist requires system flags`() {
        assertTrue(
            ModuleLogIngressPolicy.isTrustedSystemPackage(
                "com.android.systemui",
                ApplicationInfo.FLAG_SYSTEM,
            ),
        )
        assertTrue(
            ModuleLogIngressPolicy.isTrustedSystemPackage(
                "com.miui.securitycore",
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
            ),
        )
        assertFalse(ModuleLogIngressPolicy.isTrustedSystemPackage("com.android.systemui", 0))
        assertFalse(
            ModuleLogIngressPolicy.isTrustedSystemPackage(
                "com.example.attacker",
                ApplicationInfo.FLAG_SYSTEM,
            ),
        )
    }

    @Test
    fun `module log route preserves allowlisted caller route and rejects unknown values`() {
        assertEquals("xmsf_hook", ModuleLogIngressPolicy.resolveRoute("xmsf_hook"))
        assertEquals("nms_hook", ModuleLogIngressPolicy.resolveRoute("nms_hook"))
        assertEquals("app", ModuleLogIngressPolicy.resolveRoute("attacker-controlled-route"))
        assertEquals("app", ModuleLogIngressPolicy.resolveRoute(null))
    }

    @Test
    fun `persistent quota prunes oldest file from the requested route only`() {
        val directory = Files.createTempDirectory("mipush-module-log-quota").toFile()
        try {
            val runtimeLog = File(directory, "runtime.MiPush.2026-07-16.jsonl")
            val managerLog = File(directory, "runtime.manager.2026-07-16.jsonl").apply { writeText("keep") }
            val unrelated = File(directory, "crash.log").apply { writeText("keep") }
            RandomAccessFile(runtimeLog, "rw").use {
                it.setLength(ModuleLogIngressPolicy.MAX_PERSISTED_LOG_BYTES)
            }

            assertTrue(
                ModuleLogIngressPolicy.ensurePersistentQuota(
                    directory,
                    route = "MiPush",
                    currentDay = "2026-07-17",
                ),
            )
            assertFalse(runtimeLog.exists())
            assertTrue(managerLog.exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `persistent quota never deletes current route file`() {
        val directory = Files.createTempDirectory("mipush-module-log-current-day").toFile()
        try {
            val runtimeLog = File(directory, "runtime.MiPush.2026-07-16.jsonl")
            RandomAccessFile(runtimeLog, "rw").use {
                it.setLength(ModuleLogIngressPolicy.MAX_PERSISTED_LOG_BYTES)
            }

            assertFalse(
                ModuleLogIngressPolicy.ensurePersistentQuota(
                    directory,
                    route = "MiPush",
                    currentDay = "2026-07-16",
                ),
            )
            assertTrue(runtimeLog.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
