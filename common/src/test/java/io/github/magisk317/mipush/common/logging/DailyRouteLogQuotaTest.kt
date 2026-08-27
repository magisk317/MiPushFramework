package io.github.magisk317.mipush.common.logging

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class DailyRouteLogQuotaTest {
    @Test
    fun `quota removes only older files from the same route`() {
        withTempDir { dir ->
            val oldModule = File(dir, "runtime.MiPush.2026-08-25.jsonl").apply { writeText("123456") }
            val todayModule = File(dir, "runtime.MiPush.2026-08-26.jsonl").apply { writeText("1234") }
            val manager = File(dir, "runtime.manager.2026-08-25.jsonl").apply { writeText("123456") }

            assertTrue(DailyRouteLogQuota.ensureCapacity(dir, "MiPush", "2026-08-26", 2, 8))
            assertFalse(oldModule.exists())
            assertTrue(todayModule.exists())
            assertTrue(manager.exists())
        }
    }

    @Test
    fun `quota never deletes the current daily file`() {
        withTempDir { dir ->
            val today = File(dir, "runtime.manager.2026-08-26.jsonl").apply { writeText("12345678") }

            assertFalse(DailyRouteLogQuota.ensureCapacity(dir, "manager", "2026-08-26", 1, 8))
            assertTrue(today.exists())
        }
    }

    @Test
    fun `app and named routes use independent file names`() {
        withTempDir { dir ->
            File(dir, "runtime.2026-08-26.jsonl").writeText("12345678")

            assertTrue(DailyRouteLogQuota.ensureCapacity(dir, "MiPush", "2026-08-26", 8, 8))
            assertTrue(File(dir, "runtime.2026-08-26.jsonl").exists())
        }
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("daily-route-quota").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
