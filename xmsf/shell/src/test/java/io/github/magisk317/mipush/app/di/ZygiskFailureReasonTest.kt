package io.github.magisk317.mipush.app.di

import io.github.magisk317.mipush.platform.support.BoundedShellResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZygiskFailureReasonTest {
    @Test
    fun `transient exec failure is a blank stderr non-zero exit`() {
        assertTrue(ZygiskFailure.isTransientExecFailure(BoundedShellResult(exitCode = -1)))
        assertFalse(
            ZygiskFailure.isTransientExecFailure(BoundedShellResult(exitCode = 2, stderr = listOf("denied"))),
        )
        assertFalse(ZygiskFailure.isTransientExecFailure(BoundedShellResult.timedOut()))
        assertFalse(ZygiskFailure.isTransientExecFailure(BoundedShellResult.skipped("root not granted")))
    }

    @Test
    fun `config read failures map to stable reasons`() {
        assertEquals(
            "zygisk_root_missing",
            ZygiskFailure.reason(BoundedShellResult.skipped("root not granted")),
        )
        assertEquals(
            "zygisk_config_read_timeout",
            ZygiskFailure.reason(BoundedShellResult.timedOut()),
        )
        assertEquals(
            "zygisk_config_missing",
            ZygiskFailure.reason(
                BoundedShellResult(
                    exitCode = 1,
                    stderr = listOf("cat: /data/adb/mipush_zygisk/app.conf: No such file or directory"),
                ),
            ),
        )
        assertEquals(
            "zygisk_root_exec_failed",
            ZygiskFailure.reason(BoundedShellResult(exitCode = -1)),
        )
        assertEquals(
            "zygisk_config_read_failed",
            ZygiskFailure.reason(BoundedShellResult(exitCode = 1, stderr = listOf("Permission denied"))),
        )
    }

    @Test
    fun `scan failures map to stable reasons`() {
        assertEquals(
            "zygisk_scan_timeout",
            ZygiskFailure.scanReason(BoundedShellResult.timedOut()),
        )
        assertEquals(
            "zygisk_module_not_installed",
            ZygiskFailure.scanReason(
                BoundedShellResult(
                    exitCode = 1,
                    stderr = listOf("sh: /data/adb/modules/mipush_zygisk/bin/mipushctl: No such file or directory"),
                ),
            ),
        )
        assertEquals(
            "zygisk_root_exec_failed",
            ZygiskFailure.scanReason(BoundedShellResult(exitCode = 127)),
        )
        assertEquals(
            "zygisk_scan_failed",
            ZygiskFailure.scanReason(BoundedShellResult(exitCode = 127, stderr = listOf("mipushctl: scan aborted"))),
        )
        assertEquals(
            "zygisk_root_exec_failed",
            ZygiskFailure.scanReason(BoundedShellResult(exitCode = -1)),
        )
    }
}
