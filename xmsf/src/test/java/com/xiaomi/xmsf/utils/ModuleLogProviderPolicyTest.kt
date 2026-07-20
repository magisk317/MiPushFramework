package com.xiaomi.xmsf.utils

import android.content.pm.ApplicationInfo
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
    fun `persistent quota prunes oldest runtime logs only`() {
        val directory = Files.createTempDirectory("mipush-module-log-quota").toFile()
        try {
            val runtimeLog = File(directory, "runtime.2026-07-16.jsonl")
            val unrelated = File(directory, "crash.log").apply { writeText("keep") }
            RandomAccessFile(runtimeLog, "rw").use {
                it.setLength(ModuleLogIngressPolicy.MAX_PERSISTED_LOG_BYTES)
            }

            assertTrue(ModuleLogIngressPolicy.ensurePersistentQuota(directory))
            assertFalse(runtimeLog.exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `persistent quota reserves space for aggregate and route copies`() {
        val directory = Files.createTempDirectory("mipush-module-log-double-write").toFile()
        try {
            val runtimeLog = File(directory, "runtime.2026-07-16.jsonl")
            RandomAccessFile(runtimeLog, "rw").use {
                it.setLength(
                    ModuleLogIngressPolicy.MAX_PERSISTED_LOG_BYTES -
                        2 * ModuleLogIngressPolicy.MAX_EVENT_BYTES,
                )
            }

            assertTrue(ModuleLogIngressPolicy.ensurePersistentQuota(directory))
            assertFalse(runtimeLog.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
