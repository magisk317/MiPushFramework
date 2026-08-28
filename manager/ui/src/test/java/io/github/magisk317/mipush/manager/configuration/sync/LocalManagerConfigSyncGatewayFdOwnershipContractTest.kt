package io.github.magisk317.mipush.manager.configuration.sync

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalManagerConfigSyncGatewayFdOwnershipContractTest {
    @Test
    fun `manager-owned upload pipe ends close in finally for every result`() {
        val source = File("src/main/java/io/github/magisk317/mipush/manager/configuration/sync/LocalManagerConfigSyncGateway.kt")
            .readText()
        val upload = source.substringAfter("private suspend fun uploadToRuntime")
            .substringBefore("private fun ConfigEditorSnapshot.toManager")
        val finallyBlock = upload.substringAfter("finally {").substringBefore("\n        }")

        assertTrue(upload.contains("is ManagerRuntimeResult.Success -> result.value.success"))
        assertTrue(finallyBlock.contains("readSide.close()"))
        assertTrue(finallyBlock.contains("writeSide.close()"))
        assertTrue(!upload.substringBefore("finally {").contains("else -> {\n                    runCatching { readSide.close() }"))
    }
}
