package com.xiaomi.xmsf.push.service

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushFacadeServiceContractTest {
    @Test
    fun `external facade clears started state after dispatch`() {
        val source = readShellSource("com/xiaomi/xmsf/push/service/MiPushFacadeService.kt")
        val foregroundIndex = source.indexOf("ForegroundHelper(this).satisfyForegroundStartContract()")
        val dispatchIndex = source.indexOf("intent?.let(::submitStartIntent)")
        val stopIndex = source.indexOf("stopSelfResult(startId)")

        assertTrue(foregroundIndex >= 0)
        assertTrue(dispatchIndex > foregroundIndex)
        assertTrue(stopIndex > dispatchIndex)
        assertTrue(source.contains("return START_NOT_STICKY"))
        assertFalse(source.contains("startForeground("))

        val foregroundHelperSource = readShellSource("io/github/magisk317/mipush/service/ForegroundHelper.kt")
        assertTrue(foregroundHelperSource.contains("external_ingress_fgs_disallowed"))
        assertTrue(foregroundHelperSource.contains("satisfyForegroundStartContract"))
    }

    private fun readShellSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("../shell/src/main/java/$relativePath"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source not found: $relativePath from ${File(".").absolutePath}")
    }
}
