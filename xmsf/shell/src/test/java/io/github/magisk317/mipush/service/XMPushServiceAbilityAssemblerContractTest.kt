package io.github.magisk317.mipush.service

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XMPushServiceAbilityAssemblerContractTest {
    @Test
    fun `active service listeners cannot schedule legacy pull traffic`() {
        val assembler = resolveMainSource("XMPushServiceAbilityAssembler.kt")
        val obsoleteAbility = resolveMainSource("PullAllApplicationDataAbility.kt")
        val obsoleteJob = resolveVendorSource("PullAllApplicationDataFromServerJob.kt")

        assertTrue(assembler.isFile)
        assertFalse(
            assembler.readText().contains("listeners += PullAllApplicationDataAbility("),
        )
        assertFalse(obsoleteAbility.exists())
        assertFalse(obsoleteJob.exists())
    }

    private fun resolveMainSource(fileName: String): File {
        val relativePath =
            "src/main/java/io/github/magisk317/mipush/service/$fileName"
        return listOf(File(relativePath), File("xmsf/$relativePath"))
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    private fun resolveVendorSource(fileName: String): File {
        val relativePath = "vendor/src/main/java/com/xiaomi/push/service/$fileName"
        return listOf(File(relativePath), File("../$relativePath"))
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
